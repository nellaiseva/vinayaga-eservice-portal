package com.eservice1.submission.controller;

import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.eservice1.submission.service.RequestAccessService;
import org.springframework.security.core.Authentication;

import com.eservice1.submission.service.DocumentRetentionService;
import java.util.Map;

@RestController
@RequestMapping("/documents")
public class UploadedDocumentController {

    private final UploadedDocumentRepository documentRepository;
    private final StorageService storageService;
    private final RequestAccessService requestAccessService;
    private final DocumentRetentionService retentionService;

    public UploadedDocumentController(
            UploadedDocumentRepository documentRepository,
            StorageService storageService,
            RequestAccessService requestAccessService,
            DocumentRetentionService retentionService) {

        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.requestAccessService = requestAccessService;
        this.retentionService = retentionService;
    }

    public UploadedDocumentController(
            UploadedDocumentRepository documentRepository,
            StorageService storageService,
            RequestAccessService requestAccessService) {
        this(documentRepository, storageService, requestAccessService, null);
    }

    @GetMapping("/download/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long documentId,
            Authentication authentication) throws IOException {

        UploadedDocument document = documentRepository
                .findById(documentId)
                .orElseThrow();

        requestAccessService.requireRequestAccess(
                document.getRequest().getId(),
                authentication
        );

        byte[] fileBytes;
        Path localFilePath = resolveLocalFilePath(document.getFilePath());
        if (localFilePath != null && Files.isRegularFile(localFilePath)) {
            fileBytes = Files.readAllBytes(localFilePath);
        } else {
            fileBytes = storageService.download(document.getFilePath());
        }

        String safeFileName = sanitizeFileName(document.getFileName());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFileName + "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        if (retentionService != null) {
            retentionService.deleteCustomerDocument(documentId, authentication);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Document deleted successfully."
        ));
    }

    private Path resolveLocalFilePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        try {
            Path baseUploadDir = Path.of(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
            Path path = Path.of(rawPath);
            if (!path.isAbsolute()) {
                path = baseUploadDir.resolve(path).normalize();
            } else {
                path = path.normalize();
            }

            // Path traversal guard: must be strictly inside baseUploadDir
            if (path.startsWith(baseUploadDir) && Files.isRegularFile(path)) {
                return path;
            }

            // Check if filename exists directly in baseUploadDir
            Path fallback = baseUploadDir.resolve(path.getFileName().toString()).normalize();
            if (fallback.startsWith(baseUploadDir) && Files.isRegularFile(fallback)) {
                return fallback;
            }
        } catch (Exception ignored) {
            // Invalid path syntax or IO error
        }

        return null;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
