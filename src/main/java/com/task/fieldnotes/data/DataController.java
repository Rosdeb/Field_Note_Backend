package com.task.fieldnotes.data;

import com.task.fieldnotes.auth.AuthService.User;
import com.task.fieldnotes.common.ApiException;
import com.task.fieldnotes.sync.*;
import com.task.fieldnotes.sync.SyncModels.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Data Resources", description = "Query, read, upsert, and soft-delete operations for Customers, Sites, and Field Notes")
public class DataController {
    private final EntityRepository entities;
    private final SyncService sync;
    public DataController(EntityRepository entities, SyncService sync) { this.entities = entities; this.sync = sync; }

    @io.swagger.v3.oas.annotations.Operation(summary = "List customers", description = "Returns a paginated list of active customers owned by the user.")
    @GetMapping("/customers")
    public Page customers(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return new Page(entities.list(user.id(), EntityType.CUSTOMER, null, null, null, page, size), page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List sites", description = "Returns a paginated list of sites owned by the user, optionally filtered by customer ID.")
    @GetMapping("/sites")
    public Page sites(@AuthenticationPrincipal User user, @RequestParam(required = false) UUID customerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return new Page(entities.list(user.id(), EntityType.SITE, customerId, null, null, page, size), page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List field notes", description = "Returns a paginated list of field notes with optional filters for site ID, status, and text search query.")
    @GetMapping("/notes")
    public Page notes(@AuthenticationPrincipal User user, @RequestParam(required = false) UUID siteId, @RequestParam(required = false) String q, @RequestParam(required = false) Status status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return new Page(entities.list(user.id(), EntityType.NOTE, siteId, q, status, page, size), page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get entity by ID", description = "Retrieves the single customer, site, or field note matching the resource type and UUID.")
    @GetMapping("/{resource:customers|sites|notes}/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal User user, @PathVariable String resource, @PathVariable UUID id) {
        return entities.read(user.id(), type(resource), id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Upsert entity", description = "Creates or updates a customer, site, or note with optimistic version check and idempotent mutation envelope.")
    @PutMapping("/{resource:customers|sites|notes}/{id}")
    public Ack put(@AuthenticationPrincipal User user, @PathVariable String resource, @PathVariable UUID id, @Valid @RequestBody Write request) {
        return sync.push(user.id(), List.of(new Mutation(request.mutationId(), type(resource), id, Operation.UPSERT, request.expectedVersion(), request.data()))).results().getFirst();
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Delete entity", description = "Soft-deletes a customer, site, or note (and cascades to child records) with optimistic version check.")
    @DeleteMapping("/{resource:customers|sites|notes}/{id}")
    public Ack delete(@AuthenticationPrincipal User user, @PathVariable String resource, @PathVariable UUID id, @Valid @RequestBody Delete request) {
        return sync.push(user.id(), List.of(new Mutation(request.mutationId(), type(resource), id, Operation.DELETE, request.expectedVersion(), null))).results().getFirst();
    }
    private EntityType type(String resource) {
        return switch(resource) { case "customers" -> EntityType.CUSTOMER; case "sites" -> EntityType.SITE; case "notes" -> EntityType.NOTE; default -> throw ApiException.missing(); };
    }
}
