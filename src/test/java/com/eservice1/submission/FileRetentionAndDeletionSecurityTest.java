package com.eservice1.submission;

import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.submission.service.DocumentRetentionService;
import com.eservice1.submission.service.RequestAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileRetentionAndDeletionSecurityTest {

    @Mock
    private UploadedDocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private RequestAccessService requestAccessService;

    private DocumentRetentionService retentionService;

    private final List<File> createdFilesToDelete = new ArrayList<>();

    private static final String CUSTOMER_A_PHONE = "9876543210";
    private static final String CUSTOMER_B_PHONE = "9123456780";

    private Authentication authCustomerA;
    private Authentication authCustomerB;

    @BeforeEach
    void setUp() {
        retentionService = new DocumentRetentionService(documentRepository, storageService, requestAccessService);
        ReflectionTestUtils.setField(retentionService, "retentionDays", 30);

        authCustomerA = new UsernamePasswordAuthenticationToken(
                CUSTOMER_A_PHONE, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
        authCustomerB = new UsernamePasswordAuthenticationToken(
                CUSTOMER_B_PHONE, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
    }

    @AfterEach
    void tearDown() {
        for (File f : createdFilesToDelete) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }
        createdFilesToDelete.clear();
    }

    private File createTestLocalFile(String fileName, String content) throws IOException {
        File uploadsDir = new File(System.getProperty("user.dir"), "uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
        File testFile = new File(uploadsDir, fileName);
        Files.write(testFile.toPath(), content.getBytes());
        createdFilesToDelete.add(testFile);
        return testFile;
    }

    @Test
    @DisplayName("Own customer file can be manually deleted with physical file and DB record removal")
    void testOwnCustomerFileCanBeManuallyDeleted() throws IOException {
        File physicalFile = createTestLocalFile("CUST_own_doc.pdf", "sample content");
        assertTrue(physicalFile.exists());

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 100L);
        request.setPhoneNumber(CUSTOMER_A_PHONE);

        UploadedDocument doc = new UploadedDocument();
        ReflectionTestUtils.setField(doc, "id", 1L);
        doc.setFileName("CUST_own_doc.pdf");
        doc.setFilePath(physicalFile.getAbsolutePath());
        doc.setRequest(request);
        doc.setResultDocument(false);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        doNothing().when(requestAccessService).requireCustomerDocumentDeleteAccess(doc, authCustomerA);

        retentionService.deleteCustomerDocument(1L, authCustomerA);

        // Physical file must be deleted
        assertFalse(physicalFile.exists(), "Physical file should have been deleted from disk");
        // DB record must be deleted
        verify(documentRepository).delete(doc);
    }

    @Test
    @DisplayName("Customer cannot delete another customer's file (ownership validation)")
    void testCustomerCannotDeleteAnotherCustomerFile() throws IOException {
        File physicalFile = createTestLocalFile("CUST_other_doc.pdf", "other customer content");
        assertTrue(physicalFile.exists());

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 101L);
        request.setPhoneNumber(CUSTOMER_A_PHONE);

        UploadedDocument doc = new UploadedDocument();
        ReflectionTestUtils.setField(doc, "id", 2L);
        doc.setFileName("CUST_other_doc.pdf");
        doc.setFilePath(physicalFile.getAbsolutePath());
        doc.setRequest(request);
        doc.setResultDocument(false);

        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));
        doThrow(new AccessDeniedException("You are not authorized to delete another customer's document."))
                .when(requestAccessService).requireCustomerDocumentDeleteAccess(doc, authCustomerB);

        assertThrows(AccessDeniedException.class, () ->
                retentionService.deleteCustomerDocument(2L, authCustomerB));

        // Physical file must NOT be deleted
        assertTrue(physicalFile.exists(), "Physical file must remain untouched on authorization failure");
        // DB record must NOT be deleted
        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Customer cannot delete result images")
    void testCustomerCannotDeleteResultImage() throws IOException {
        File physicalFile = createTestLocalFile("RESULT_final.pdf", "result certificate");

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 102L);
        request.setPhoneNumber(CUSTOMER_A_PHONE);

        UploadedDocument doc = new UploadedDocument();
        ReflectionTestUtils.setField(doc, "id", 3L);
        doc.setFileName("RESULT_final.pdf");
        doc.setFilePath(physicalFile.getAbsolutePath());
        doc.setRequest(request);
        doc.setResultDocument(true);

        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc));
        doThrow(new InvalidOperationException("Result documents cannot be deleted by customers."))
                .when(requestAccessService).requireCustomerDocumentDeleteAccess(doc, authCustomerA);

        assertThrows(InvalidOperationException.class, () ->
                retentionService.deleteCustomerDocument(3L, authCustomerA));

        assertTrue(physicalFile.exists());
        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Undeleted customer file remains before 30 days")
    void testUndeletedCustomerFileRemainsBefore30Days() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("CUST_recent.pdf", "recent content");
        assertTrue(physicalFile.exists());

        // findExpiredCustomerDocuments returns empty because file upload age < 30 days
        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of());
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of());

        DocumentRetentionService.CleanupStats stats = retentionService.cleanupExpiredDocuments(now);

        assertEquals(0, stats.getCustomerFilesDeleted());
        assertEquals(0, stats.getResultFilesDeleted());
        assertTrue(physicalFile.exists(), "File uploaded within 30 days must be retained");
        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Undeleted customer file is automatically deleted after 30 days")
    void testUndeletedCustomerFileIsAutomaticallyDeletedAfter30Days() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("CUST_expired.pdf", "expired content");
        assertTrue(physicalFile.exists());

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 200L);

        UploadedDocument expiredDoc = new UploadedDocument();
        ReflectionTestUtils.setField(expiredDoc, "id", 20L);
        expiredDoc.setFileName("CUST_expired.pdf");
        expiredDoc.setFilePath(physicalFile.getAbsolutePath());
        expiredDoc.setResultDocument(false);
        expiredDoc.setUploadedAt(now.minusDays(31));
        expiredDoc.setRequest(request);

        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of(expiredDoc));
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of());

        DocumentRetentionService.CleanupStats stats = retentionService.cleanupExpiredDocuments(now);

        assertEquals(1, stats.getCustomerFilesDeleted());
        assertFalse(physicalFile.exists(), "File uploaded over 30 days ago must be physically deleted");
        verify(documentRepository).delete(expiredDoc);
    }

    @Test
    @DisplayName("Result image remains before 30 days after completion")
    void testResultImageRemainsBefore30DaysAfterCompletion() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("RESULT_recent.pdf", "recent result");
        assertTrue(physicalFile.exists());

        // findExpiredResultDocuments returns empty because completion age is only 10 days < 30 days
        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of());
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of());

        DocumentRetentionService.CleanupStats stats = retentionService.cleanupExpiredDocuments(now);

        assertEquals(0, stats.getResultFilesDeleted());
        assertTrue(physicalFile.exists(), "Result image completed within 30 days must be retained");
        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Result image is automatically deleted after 30 days of completion")
    void testResultImageIsAutomaticallyDeletedAfter30Days() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("RESULT_expired.pdf", "expired result");
        assertTrue(physicalFile.exists());

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 300L);
        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(now.minusDays(35));

        UploadedDocument expiredResult = new UploadedDocument();
        ReflectionTestUtils.setField(expiredResult, "id", 30L);
        expiredResult.setFileName("RESULT_expired.pdf");
        expiredResult.setFilePath(physicalFile.getAbsolutePath());
        expiredResult.setResultDocument(true);
        expiredResult.setUploadedAt(now.minusDays(40));
        expiredResult.setRequest(request);

        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of());
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of(expiredResult));

        DocumentRetentionService.CleanupStats stats = retentionService.cleanupExpiredDocuments(now);

        assertEquals(1, stats.getResultFilesDeleted());
        assertFalse(physicalFile.exists(), "Result image completed over 30 days ago must be physically deleted");
        verify(documentRepository).delete(expiredResult);
    }

    @Test
    @DisplayName("Active request result image is retained and never deleted by cleanup")
    void testActiveRequestResultImageIsRetained() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("RESULT_active.pdf", "active task result");
        assertTrue(physicalFile.exists());

        // Active requests are not COMPLETED, so findExpiredResultDocuments returns empty
        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of());
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of());

        DocumentRetentionService.CleanupStats stats = retentionService.cleanupExpiredDocuments(now);

        assertEquals(0, stats.getResultFilesDeleted());
        assertTrue(physicalFile.exists(), "Active request result image must be preserved");
        verify(documentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Cleanup is safe to run repeatedly (idempotent)")
    void testCleanupIsSafeToRunRepeatedly() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        File physicalFile = createTestLocalFile("CUST_repeat.pdf", "repeat test content");

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 400L);

        UploadedDocument doc = new UploadedDocument();
        ReflectionTestUtils.setField(doc, "id", 40L);
        doc.setFileName("CUST_repeat.pdf");
        doc.setFilePath(physicalFile.getAbsolutePath());
        doc.setResultDocument(false);
        doc.setUploadedAt(now.minusDays(32));
        doc.setRequest(request);

        // Run 1: finds 1 expired file
        when(documentRepository.findExpiredCustomerDocuments(cutoff))
                .thenReturn(List.of(doc))
                .thenReturn(List.of()); // Run 2: empty
        when(documentRepository.findExpiredResultDocuments(cutoff))
                .thenReturn(List.of());

        DocumentRetentionService.CleanupStats run1 = retentionService.cleanupExpiredDocuments(now);
        assertEquals(1, run1.getCustomerFilesDeleted());
        assertFalse(physicalFile.exists());
        verify(documentRepository, times(1)).delete(doc);

        // Run 2: no files left, must complete smoothly without exceptions
        DocumentRetentionService.CleanupStats run2 = retentionService.cleanupExpiredDocuments(now);
        assertEquals(0, run2.getCustomerFilesDeleted());
        assertEquals(0, run2.getResultFilesDeleted());
    }

    @Test
    @DisplayName("Missing physical file does not crash cleanup process")
    void testMissingPhysicalFileDoesNotCrashCleanup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);

        CustomerRequest request = new CustomerRequest();
        ReflectionTestUtils.setField(request, "id", 500L);

        UploadedDocument missingFileDoc = new UploadedDocument();
        ReflectionTestUtils.setField(missingFileDoc, "id", 50L);
        missingFileDoc.setFileName("already_deleted_file.pdf");
        missingFileDoc.setFilePath("uploads/non_existent_uuid.pdf");
        missingFileDoc.setResultDocument(false);
        missingFileDoc.setUploadedAt(now.minusDays(35));
        missingFileDoc.setRequest(request);

        when(documentRepository.findExpiredCustomerDocuments(cutoff)).thenReturn(List.of(missingFileDoc));
        when(documentRepository.findExpiredResultDocuments(cutoff)).thenReturn(List.of());

        // Does not throw any exception even though the file is missing from disk
        DocumentRetentionService.CleanupStats stats = assertDoesNotThrow(() ->
                retentionService.cleanupExpiredDocuments(now));

        assertEquals(1, stats.getCustomerFilesDeleted());
        verify(documentRepository).delete(missingFileDoc);
    }
}