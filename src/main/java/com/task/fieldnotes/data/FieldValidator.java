package com.task.fieldnotes.data;

import com.task.fieldnotes.common.ApiException;
import com.task.fieldnotes.sync.SyncModels.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.OffsetDateTime;

@Component
public class FieldValidator {
    public Map<String,Object> validate(EntityType type, Map<String,Object> input) {
        if(input==null) throw ApiException.bad("UPSERT requires data");
        Set<String> allowed=switch(type) {
            case CUSTOMER -> Set.of("name","contactInformation");
            case SITE -> Set.of("siteName","address","customerId");
            case NOTE -> Set.of("siteId","title","description","latitude","longitude","occurredAt","status","photoId");
        };
        if(!allowed.containsAll(input.keySet())) throw ApiException.bad("Unknown data fields");
        var out=new LinkedHashMap<String,Object>();
        switch(type) {
            case CUSTOMER -> { out.put("name",text(input,"name",200,true)); out.put("contactInformation",text(input,"contactInformation",2000,false)); }
            case SITE -> { out.put("siteName",text(input,"siteName",200,true)); out.put("address",text(input,"address",2000,true)); out.put("customerId",uuid(input,"customerId",true)); }
            case NOTE -> {
                out.put("siteId",uuid(input,"siteId",true)); out.put("title",text(input,"title",200,true)); out.put("description",text(input,"description",10000,false));
                out.put("photoId",uuid(input,"photoId",false));
                String date=text(input,"occurredAt",100,true);
                try { out.put("occurredAt",OffsetDateTime.parse(date).toInstant().toString()); } catch(Exception ex) { throw ApiException.bad("occurredAt must be an ISO-8601 timestamp with offset"); }
                try { out.put("status",Status.valueOf(text(input,"status",20,true)).name()); } catch(IllegalArgumentException ex) { throw ApiException.bad("Invalid note status"); }
                Object lat=input.get("latitude"), lon=input.get("longitude");
                if((lat==null)!=(lon==null)) throw ApiException.bad("latitude and longitude must be supplied together");
                out.put("latitude",coordinate(lat,90)); out.put("longitude",coordinate(lon,180));
            }
        }
        return out;
    }
    private String text(Map<String,Object> input,String key,int max,boolean required) {
        Object value=input.get(key);
        if(value==null && !required) return "";
        if(!(value instanceof String s) || s.length()>max || s.indexOf(0)>=0 || (required && s.isBlank())) throw ApiException.bad("Invalid "+key);
        return s;
    }
    private String uuid(Map<String,Object> input,String key,boolean required) {
        if(input.get(key)==null && !required) return null;
        try { String value=(String)input.get(key); UUID id=UUID.fromString(value); if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id.toString(); }
        catch(Exception ex) { throw ApiException.bad("Invalid "+key); }
    }
    private Double coordinate(Object value,int bound) {
        if(value==null) return null;
        if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || Math.abs(number.doubleValue())>bound) throw ApiException.bad("Invalid coordinates");
        return number.doubleValue();
    }
}
