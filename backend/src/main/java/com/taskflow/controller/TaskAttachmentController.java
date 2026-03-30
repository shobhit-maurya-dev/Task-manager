package com.taskflow.controller;

import com.taskflow.dto.TaskAttachmentDTO;
import com.taskflow.model.TaskAttachment;
import com.taskflow.service.TaskAttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TaskAttachmentController {

    @Autowired
    private TaskAttachmentService attachmentService;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','MEMBER')")
    @PostMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<TaskAttachmentDTO> uploadAttachment(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file) {
        TaskAttachmentDTO dto = attachmentService.uploadAttachment(taskId, file);
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<List<TaskAttachmentDTO>> listAttachments(@PathVariable Long taskId) {
        List<TaskAttachmentDTO> list = attachmentService.listAttachments(taskId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadAttachment(@PathVariable Long id) {
        TaskAttachment attachment = attachmentService.getAttachment(id);

        ByteArrayResource resource = new ByteArrayResource(attachment.getFileData());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .contentLength(attachment.getFileSizeBytes())
                .body(resource);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','MEMBER')")
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return ResponseEntity.noContent().build();
    }
}
