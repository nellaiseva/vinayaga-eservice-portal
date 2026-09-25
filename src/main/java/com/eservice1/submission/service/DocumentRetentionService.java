package com.eservice1.submission.service;

import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetentionService.class);

    private final UploadedDocumentRepository documentRepository;
    private final StorageService storageService;
    private final RequestAccessService requestAccessService;

    @Value("${document.retention.days:30}")
    private int retentionDays;

    public DocumentRetentionService(
            UploadedDocumentRepository documentRepository,
            StorageService storageService,
            RequestAccessService requestAccessService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.requestAccessService = requestAccessService;
    }

    /**
     * Manually delete a customer-uploaded document with ownership verification.
     */
    @Transactional
    public void deleteCustomerDocument(Long documentId, Authentication authentication) {
        UploadedDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found."));

        requestAccessService.requireCustomerDocumentDeleteAccess(document, authentication);

        deletePhysicalFileSafely(document);
        documentRepository.delete(document);

        log.info("Document ID {} deleted successfully by user {}", documentId,
                authentication != null ? authentication.getName() : "system");
    }

    /**
     * Scheduled cleanup job running at configured cron (default 2 AM daily).
     */
    @Scheduled(cron = "${document.cleanup.cron:0 0 2 * * *}")
    public void scheduledCleanup() {
        log.info("Starting scheduled document retention cleanup...");
        CleanupStats stats = cleanupExpiredDocuments(LocalDateTime.now());
        log.info("Completed document retention cleanup: deleted {} customer files, {} result files",
                stats.getCustomerFilesDeleted(), stats.getResultFilesDeleted());
    }

    /**
     * Automated cleanup logic with explicit reference time for deterministic testing.
     */
    @Transactional
    public CleanupStats cleanupExpiredDocuments(LocalDateTime referenceTime) {
        LocalDateTime cutoff = referenceTime.minusDays(retentionDays);

        int customerDeleted = 0;
        int resultDeleted = 0;

        // 1. Delete customer-uploaded files where upload age >= 30 days
        List<UploadedDocument> expiredCustomerDocs = documentRepository.findExpiredCustomerDocuments(cutoff);
        for (UploadedDocument doc : expiredCustomerDocs) {
            try {
                deletePhysicalFileSafely(doc);
                documentRepository.delete(doc);
                customerDeleted++;
                log.info("Cleaned up expired customer document ID: {}, uploaded at: {}", doc.getId(), doc.getUploadedAt());
            } catch (Exception e) {
                log.error("Failed to delete expired customer document ID {}: {}", doc.getId(), e.getMessage());
            }
        }

        // 2. Delete result images where request completion age >= 30 days
        List<UploadedDocument> expiredResultDocs = documentRepository.findExpiredResultDocuments(cutoff);
        for (UploadedDocument doc : expiredResultDocs) {
            try {
                deletePhysicalFileSafely(doc);
                documentRepository.delete(doc);
                resultDeleted++;
                log.info("Cleaned up expired result document ID: {}, request completed at: {}",
                        doc.getId(), doc.getRequest() != null ? doc.getRequest().getCompletedAt() : null);
            } catch (Exception e) {
                log.error("Failed to delete expired result document ID {}: {}", doc.getId(), e.getMessage());
            }
        }

        return new CleanupStats(customerDeleted, resultDeleted);
    }

    public CleanupStats cleanupExpiredDocuments() {
        return cleanupExpiredDocuments(LocalDateTime.now());
    }

    /**
     * Safely deletes physical file from local uploads directory and/or remote storage.
     * Guaranteed not to throw exception if the physical file is already missing.
     */
    public void deletePhysicalFileSafely(UploadedDocument document) {
        if (document == null) {
            return;
        }

        String rawPath = document.getFilePath();
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }

        // 1. Check local filesystem with path traversal guards
        Path localPath = resolveLocalFilePath(rawPath);
        if (localPath != null && Files.isRegularFile(localPath)) {
            try {
                Files.deleteIfExists(localPath);
                log.info("Deleted local file at {}", localPath);
            } catch (IOException e) {
                log.warn("Could not delete local file at {}: {}", localPath, e.getMessage());
            }
        }

        // 2. Also attempt remote storage deletion (e.g. Supabase)
        try {
            storageService.delete(rawPath);
        } catch (Exception e) {
            // Missing physical file or local-only storage: log at debug/info, do not crash
            log.debug("Remote storage delete ignored or failed for {}: {}", rawPath, e.getMessage());
        }
    }

    public Path resolveLocalFilePath(String rawPath) {
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

    public static class CleanupStats {
        private final int customerFilesDeleted;
        private final int resultFilesDeleted;

        public CleanupStats(int customerFilesDeleted, int resultFilesDeleted) {
            this.customerFilesDeleted = customerFilesDeleted;
            this.resultFilesDeleted = resultFilesDeleted;
        }

        public int getCustomerFilesDeleted() {
            return customerFilesDeleted;
        }

        public int getResultFilesDeleted() {
            return resultFilesDeleted;
        }

        public int getTotalDeleted() {
            return customerFilesDeleted + resultFilesDeleted;
        }
    }
}
