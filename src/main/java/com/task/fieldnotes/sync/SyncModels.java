package com.task.fieldnotes.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class SyncModels {
    private SyncModels() {}
    public enum EntityType {
        CUSTOMER("customers"), SITE("sites"), NOTE("field_notes");
        public final String table;
        EntityType(String table) { this.table=table; }
    }
    public enum Operation { UPSERT, DELETE }
    public enum Status { DRAFT, OPEN, IN_PROGRESS, COMPLETED }
    public record Mutation(@NotNull UUID mutationId, @NotNull EntityType entityType, @NotNull UUID entityId,
        @NotNull Operation operation, @NotNull @Min(0) Long expectedVersion, Map<String,Object> data) {}
    public record Push(@NotEmpty @Size(max=100) List<@NotNull @Valid Mutation> mutations) {}
    public record Write(@NotNull UUID mutationId, @NotNull @Min(0) Long expectedVersion, @NotNull Map<String,Object> data) {}
    public record Delete(@NotNull UUID mutationId, @NotNull @Min(1) Long expectedVersion) {}
    public record Ack(UUID mutationId, EntityType entityType, UUID entityId, long version, long revision, Map<String,Object> data) {}
    public record PushResult(List<Ack> results) {}
    public record Change(long revision, EntityType entityType, UUID entityId, Map<String,Object> data) {}
    public record Pull(List<Change> changes, long nextCursor, boolean hasMore) {}
    public record Page(List<Map<String,Object>> items, int page, int size) {}
}
