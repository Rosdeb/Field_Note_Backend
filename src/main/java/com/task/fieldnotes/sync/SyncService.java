package com.task.fieldnotes.sync;

import com.task.fieldnotes.common.*;
import com.task.fieldnotes.data.*;
import com.task.fieldnotes.sync.SyncModels.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import java.time.Instant;

@Service
public class SyncService {
    private final JdbcClient db;
    private final EntityRepository entities;
    private final FieldValidator validator;
    private final ObjectMapper json;
    private record Receipt(String requestHash,String response) {}
    public SyncService(JdbcClient db, EntityRepository entities, FieldValidator validator, ObjectMapper json) {
        this.db=db; this.entities=entities; this.validator=validator; this.json=json;
    }
    @Transactional
    public PushResult push(UUID owner,List<Mutation> mutations) {
        // A per-account transaction lock makes version checks, cascade writes, receipts and revisions atomic.
        db.sql("SELECT revision FROM users WHERE id=:owner FOR UPDATE").param("owner",owner).query(Long.class).single();
        List<Ack> results=new ArrayList<>();
        for(Mutation mutation:mutations) results.add(apply(owner,mutation));
        return new PushResult(results);
    }
    private Ack apply(UUID owner,Mutation m) {
        String hash=Digests.sha256(json.writeValueAsString(canonical(json.convertValue(m,new TypeReference<Map<String,Object>>(){}))));
        var receipt=db.sql("SELECT request_hash,response FROM mutation_receipts WHERE owner_id=:owner AND mutation_id=:id")
            .param("owner",owner).param("id",m.mutationId()).query(Receipt.class).optional();
        if(receipt.isPresent()) {
            if(!receipt.get().requestHash().equals(hash)) throw ApiException.conflict("mutationId was already used for a different request");
            return json.readValue(receipt.get().response(),Ack.class);
        }
        var current=entities.find(owner,m.entityType(),m.entityId());
        if(current.isPresent() && Boolean.TRUE.equals(current.get().get("deleted"))) throw ApiException.conflict("Resource was deleted; create a new UUID to recreate it");
        long version=current.map(row -> ((Number)row.get("version")).longValue()).orElse(0L);
        if(version!=m.expectedVersion()) throw ApiException.conflict("Version conflict for "+m.entityType()+" "+m.entityId()+"; current version is "+version);
        Instant now=Instant.now(); long revision;
        if(m.operation()==Operation.UPSERT) {
            Map<String,Object> fields=validator.validate(m.entityType(),m.data());
            validateRelations(owner,m.entityType(),fields);
            entities.save(owner,m.entityType(),m.entityId(),fields,version+1,now,current.isEmpty());
            revision=record(owner,m.entityType(),m.entityId());
        } else {
            if(m.data()!=null && !m.data().isEmpty()) throw ApiException.bad("DELETE cannot contain data");
            if(current.isEmpty()) throw ApiException.missing();
            revision=cascadeDelete(owner,m.entityType(),m.entityId(),now);
        }
        var snapshot=entities.find(owner,m.entityType(),m.entityId()).orElseThrow();
        Ack ack=new Ack(m.mutationId(),m.entityType(),m.entityId(),((Number)snapshot.get("version")).longValue(),revision,snapshot);
        db.sql("INSERT INTO mutation_receipts(owner_id,mutation_id,request_hash,response) VALUES(:owner,:id,:hash,:response)")
            .param("owner",owner).param("id",m.mutationId()).param("hash",hash).param("response",json.writeValueAsString(ack)).update();
        return ack;
    }
    private void validateRelations(UUID owner,EntityType type,Map<String,Object> fields) {
        if(type==EntityType.SITE) active(owner,EntityType.CUSTOMER,UUID.fromString((String)fields.get("customerId")));
        if(type==EntityType.NOTE) {
            var site=active(owner,EntityType.SITE,UUID.fromString((String)fields.get("siteId")));
            active(owner,EntityType.CUSTOMER,UUID.fromString((String)site.get("customerId")));
            if(fields.get("photoId")!=null && !db.sql("SELECT EXISTS(SELECT 1 FROM photos WHERE owner_id=:owner AND id=:id)")
                .param("owner",owner).param("id",UUID.fromString((String)fields.get("photoId"))).query(Boolean.class).single()) throw ApiException.missing();
        }
    }
    private Map<String,Object> active(UUID owner,EntityType type,UUID id) {
        var row=entities.find(owner,type,id).orElseThrow(ApiException::missing);
        if(Boolean.TRUE.equals(row.get("deleted"))) throw ApiException.missing();
        return row;
    }
    private long cascadeDelete(UUID owner,EntityType type,UUID id,Instant now) {
        if(type==EntityType.CUSTOMER)
            for(UUID child:entities.children(owner,EntityType.SITE,"customer_id",id)) cascadeDelete(owner,EntityType.SITE,child,now);
        if(type==EntityType.SITE)
            for(UUID child:entities.children(owner,EntityType.NOTE,"site_id",id)) cascadeDelete(owner,EntityType.NOTE,child,now);
        entities.delete(owner,type,id,now);
        return record(owner,type,id);
    }
    private long record(UUID owner,EntityType type,UUID id) {
        long revision=db.sql("UPDATE users SET revision=revision+1 WHERE id=:owner RETURNING revision").param("owner",owner).query(Long.class).single();
        db.sql("INSERT INTO changes(owner_id,revision,entity_type,entity_id,snapshot) VALUES(:owner,:revision,:type,:id,:snapshot)")
            .param("owner",owner).param("revision",revision).param("type",type.name()).param("id",id)
            .param("snapshot",json.writeValueAsString(entities.find(owner,type,id).orElseThrow())).update();
        return revision;
    }
    @Transactional(readOnly=true)
    public Pull pull(UUID owner,long after,int limit) {
        if(after<0 || limit<1 || limit>500) throw ApiException.bad("after must be >=0 and limit between 1 and 500");
        long upper=db.sql("SELECT revision FROM users WHERE id=:owner").param("owner",owner).query(Long.class).single();
        if(after>upper) throw ApiException.bad("Cursor exceeds the account revision; reset local sync state");
        var changes=db.sql("SELECT revision,entity_type,entity_id,snapshot FROM changes WHERE owner_id=:owner AND revision>:after AND revision<=:upper ORDER BY revision LIMIT :limit")
            .param("owner",owner).param("after",after).param("upper",upper).param("limit",limit+1)
            .query((rs,n) -> new Change(rs.getLong("revision"),EntityType.valueOf(rs.getString("entity_type")),rs.getObject("entity_id",UUID.class),
                json.readValue(rs.getString("snapshot"),new TypeReference<Map<String,Object>>(){}))).list();
        boolean more=changes.size()>limit;
        var page=more?changes.subList(0,limit):changes;
        long next=page.isEmpty()?after:page.getLast().revision();
        return new Pull(page,next,more);
    }
    private Object canonical(Object value) {
        if(value instanceof Map<?,?> map) {
            Map<String,Object> sorted=new TreeMap<>(); map.forEach((k,v)->sorted.put(k.toString(),canonical(v))); return sorted;
        }
        if(value instanceof List<?> list) return list.stream().map(this::canonical).toList();
        return value;
    }
}
