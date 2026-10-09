package com.task.fieldnotes.sync;

import com.task.fieldnotes.auth.AuthService.User;
import com.task.fieldnotes.sync.SyncModels.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Synchronization", description = "Offline two-way synchronization: push local mutation batches and pull incremental server change revisions")
public class SyncController {
    private final SyncService sync;
    public SyncController(SyncService sync) { this.sync = sync; }

    @Operation(summary = "Push local offline mutations", description = "Applies an atomic batch of UPSERT and DELETE mutations with expected versions.")
    @PostMapping("/push")
    public PushResult push(@AuthenticationPrincipal User user, @Valid @RequestBody Push request) {
        return sync.push(user.id(), request.mutations());
    }

    @Operation(summary = "Pull incremental change revisions", description = "Fetches change revisions strictly after the specified cursor for local SQLite cache updates and initial sync hydration.")
    @GetMapping("/changes")
    public Pull pull(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "100") int limit) {
        return sync.pull(user.id(), after, limit);
    }
}
