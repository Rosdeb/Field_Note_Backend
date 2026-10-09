package com.task.fieldnotes.photo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class PhotoRepository {
    public record Stored(String contentType,byte[] bytes,String digest) {}
    private final JdbcClient db;
    public PhotoRepository(JdbcClient db) { this.db=db; }
    public void lockOwner(UUID owner) {
        db.sql("SELECT revision FROM users WHERE id=:owner FOR UPDATE").param("owner",owner).query(Long.class).single();
    }
    public Optional<Stored> find(UUID owner,UUID id) {
        return db.sql("SELECT content_type,bytes,digest FROM photos WHERE owner_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).query(Stored.class).optional();
    }
    public void insert(UUID owner,UUID id,String contentType,byte[] bytes,String digest) {
        db.sql("INSERT INTO photos(id,owner_id,content_type,bytes,digest) VALUES(:id,:owner,:type,:bytes,:digest)")
            .param("id",id).param("owner",owner).param("type",contentType).param("bytes",bytes).param("digest",digest).update();
    }
}
