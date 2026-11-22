package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.IncidentDtos.AttachmentView;
import com.iitsm.backend.domain.incident.Attachment;
import com.iitsm.backend.service.AttachmentService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Photos on incidents: list, upload (multipart, field {@code file}), and download. */
@RestController
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/api/incidents/{incidentId}/attachments")
    public List<AttachmentView> list(@PathVariable Long incidentId) {
        return attachments.list(incidentId);
    }

    @PostMapping(path = "/api/incidents/{incidentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(@PathVariable Long incidentId, @RequestParam("file") MultipartFile file) throws IOException {
        return attachments.add(incidentId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    @GetMapping("/api/attachments/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        Attachment attachment = attachments.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(attachment.getFileName()).build().toString())
                // The type was verified from the bytes on upload; never let a browser reinterpret it.
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(attachment.getContent());
    }
}
