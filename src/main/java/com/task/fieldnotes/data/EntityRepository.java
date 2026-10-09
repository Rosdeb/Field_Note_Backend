package com.task.fieldnotes.data;

import com.task.fieldnotes.common.ApiException;
import com.task.fieldnotes.sync.SyncModels.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.sql.*;
import java.time.*;

@Repository
public class EntityRepository {
    private final JdbcClient db;
    public EntityRepository(JdbcClient db) { this.db=db; }
    public Optional<Map<String,Object>> find(UUID owner, EntityType type, UUID id) {
        return db.sql("SELECT * FROM "+type.table+" WHERE owner_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).query((rs,n)->map(rs)).optional();
    }
    public Map<String,Object> read(UUID owner, EntityType type, UUID id) {
        return db.sql(select(type)+" WHERE e.owner_id=:owner AND e.id=:id AND NOT e.deleted")
            .param("owner",owner).param("id",id).query((rs,n)->map(rs)).optional().orElseThrow(ApiException::missing);
    }
    public List<Map<String,Object>> list(UUID owner, EntityType type, UUID parent, String q, Status status, int page, int size) {
        if(page<0 || size<1 || size>100) throw ApiException.bad("page must be >=0 and size between 1 and 100");
        String sql=select(type)+" WHERE e.owner_id=:owner AND NOT e.deleted";
        Map<String,Object> args=new HashMap<>(); args.put("owner",owner);
        if(parent!=null && type!=EntityType.CUSTOMER) { sql+=" AND e."+(type==EntityType.SITE?"customer_id":"site_id")+"=:parent"; args.put("parent",parent); }
        if(type==EntityType.NOTE && status!=null) { sql+=" AND e.status=:status"; args.put("status",status.name()); }
        if(type==EntityType.NOTE && q!=null) {
            if(q.length()>200 || q.indexOf(0)>=0) throw ApiException.bad("Search text must be at most 200 characters and contain no null character");
            if(!q.isBlank()) {
                // Separate candidate queries allow each text index to participate; an OR
                // across the outer joined tables would often scan all owned notes.
                sql+="""
                     AND e.id IN (
                         SELECT n.id FROM field_notes n
                         WHERE n.owner_id=:owner AND NOT n.deleted
                           AND (n.title ILIKE :q ESCAPE '!' OR n.description ILIKE :q ESCAPE '!')
                         UNION
                         SELECT n.id FROM sites matched JOIN field_notes n
                           ON n.site_id=matched.id AND n.owner_id=matched.owner_id
                         WHERE matched.owner_id=:owner AND NOT matched.deleted AND NOT n.deleted
                           AND matched.site_name ILIKE :q ESCAPE '!'
                         UNION
                         SELECT n.id FROM customers matched JOIN sites parent
                           ON parent.customer_id=matched.id AND parent.owner_id=matched.owner_id
                         JOIN field_notes n ON n.site_id=parent.id AND n.owner_id=parent.owner_id
                         WHERE matched.owner_id=:owner AND NOT matched.deleted AND NOT parent.deleted AND NOT n.deleted
                           AND matched.name ILIKE :q ESCAPE '!'
                     )
                    """;
                args.put("q","%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            }
        }
        args.put("limit",size); args.put("offset",(long)page*size);
        return db.sql(sql+" ORDER BY e.updated_at DESC,e.id LIMIT :limit OFFSET :offset").params(args).query((rs,n)->map(rs)).list();
    }
    private String select(EntityType type) {
        return switch(type) {
            case CUSTOMER -> "SELECT e.* FROM customers e";
            case SITE -> "SELECT e.*,c.name AS customer_name FROM sites e JOIN customers c ON c.id=e.customer_id AND c.owner_id=e.owner_id";
            case NOTE -> "SELECT e.*,s.site_name,c.name AS customer_name,c.id AS customer_id FROM field_notes e JOIN sites s ON s.id=e.site_id AND s.owner_id=e.owner_id JOIN customers c ON c.id=s.customer_id AND c.owner_id=s.owner_id";
        };
    }
    public void save(UUID owner, EntityType type, UUID id, Map<String,Object> fields, long version, Instant now, boolean create) {
        Map<String,Object> args=new HashMap<>(); args.put("owner",owner); args.put("id",id); args.put("version",version);
        args.put("now",OffsetDateTime.ofInstant(now,ZoneOffset.UTC));
        Map<String,String> columns=switch(type) {
            case CUSTOMER -> Map.of("name","name","contactInformation","contact_information");
            case SITE -> Map.of("siteName","site_name","address","address","customerId","customer_id");
            case NOTE -> Map.of("siteId","site_id","title","title","description","description","latitude","latitude","longitude","longitude","occurredAt","occurred_at","status","status","photoId","photo_id");
        };
        List<String> keys=new ArrayList<>(columns.keySet()); Collections.sort(keys);
        for(String key:keys) {
            Object value=fields.get(key);
            if(value!=null && key.endsWith("Id")) value=UUID.fromString(value.toString());
            if(value!=null && key.equals("occurredAt")) value=OffsetDateTime.parse(value.toString());
            args.put(key,value);
        }
        String sql;
        if(create) {
            sql="INSERT INTO "+type.table+"(id,owner_id,version,created_at,updated_at,"+String.join(",",keys.stream().map(columns::get).toList())+") VALUES(:id,:owner,:version,:now,:now,"+String.join(",",keys.stream().map(k->":"+k).toList())+")";
        } else {
            sql="UPDATE "+type.table+" SET version=:version,updated_at=:now,"+String.join(",",keys.stream().map(k->columns.get(k)+"=:"+k).toList())+" WHERE owner_id=:owner AND id=:id";
        }
        db.sql(sql).params(args).update();
    }
    public List<UUID> children(UUID owner, EntityType type, String parentColumn, UUID parent) {
        return db.sql("SELECT id FROM "+type.table+" WHERE owner_id=:owner AND "+parentColumn+"=:parent AND NOT deleted ORDER BY id")
            .param("owner",owner).param("parent",parent).query(UUID.class).list();
    }
    public void delete(UUID owner, EntityType type, UUID id, Instant now) {
        db.sql("UPDATE "+type.table+" SET deleted=true,version=version+1,updated_at=:now WHERE owner_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).param("now",OffsetDateTime.ofInstant(now,ZoneOffset.UTC)).update();
    }
    private Map<String,Object> map(ResultSet rs) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        var meta=rs.getMetaData();
        for(int i=1;i<=meta.getColumnCount();i++) {
            String name=meta.getColumnLabel(i);
            if(name.equals("owner_id")) continue;
            StringBuilder camel=new StringBuilder(); boolean upper=false;
            for(char c:name.toCharArray()) { if(c=='_') upper=true; else { camel.append(upper?Character.toUpperCase(c):c); upper=false; } }
            Object value=rs.getObject(i);
            if(value instanceof UUID) value=value.toString();
            if(value instanceof Timestamp t) value=t.toInstant().toString();
            if(value instanceof OffsetDateTime t) value=t.toInstant().toString();
            result.put(camel.toString(),value);
        }
        return result;
    }
}
