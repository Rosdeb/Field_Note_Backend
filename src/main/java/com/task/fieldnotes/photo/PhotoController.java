package com.task.fieldnotes.photo;

import com.task.fieldnotes.auth.AuthService.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@Tag(name = "Photos", description = "Inspection photo uploads and downloads (up to 5 MiB binary JPEG/PNG)")
public class PhotoController {
    private final PhotoService photos;
    public PhotoController(PhotoService photos) { this.photos = photos; }

    @Operation(summary = "Upload photo", description = "Uploads raw binary image (image/jpeg or image/png up to 5 MiB) for the given photo UUID.")
    @PutMapping("/{id}")
    public PhotoService.Photo upload(@AuthenticationPrincipal User user, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        return photos.upload(user.id(), id, request.getContentType(), request.getInputStream().readNBytes(PhotoService.MAX + 1));
    }

    @Operation(summary = "Download photo", description = "Downloads raw binary bytes of the photo matching the given ID.")
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        var photo = photos.download(user.id(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
            .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(photo.bytes());
    }
}
