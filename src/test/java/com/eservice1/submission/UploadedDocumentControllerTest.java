package com.eservice1.submission;

import com.eservice1.storage.StorageService;
import com.eservice1.submission.controller.UploadedDocumentController;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.submission.service.RequestAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UploadedDocumentControllerTest {

    @Mock
    private UploadedDocumentRepository documentRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private RequestAccessService requestAccessService;
    @Mock
    private com.eservice1.submission.service.DocumentRetentionService retentionService;
    @Mock
    private Authentication authentication;

    private UploadedDocumentController controller;
    private final List<File> createdFilesToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new UploadedDocumentController(documentRepository, storageService, requestAccessService, retentionService);
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

    private void setEntityId(Object target, Long id) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDownloadDocument_LocalFile_Success() throws IOException {
        File uploadsDir = new File(System.getProperty("user.dir"), "uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
        File testLocalFile = new File(uploadsDir, "RESULT_test_download_file.pdf");
        Files.write(testLocalFile.toPath(), "Local file content for download test".getBytes());
        createdFilesToDelete.add(testLocalFile);

        CustomerRequest request = new CustomerRequest();
        setEntityId(request, 42L);

        UploadedDocument doc = new UploadedDocument();
        setEntityId(doc, 10L);
        doc.setRequest(request);
        doc.setFileName("RESULT_test_download_file.pdf");
        doc.setFilePath(testLocalFile.getAbsolutePath());
        doc.setResultDocument(true);

        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

        ResponseEntity<byte[]> response = controller.downloadDocument(10L, authentication);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals("Local file content for download test".getBytes(), response.getBody());
        verify(requestAccessService).requireRequestAccess(42L, authentication);
        // StorageService (Supabase) should NOT be called since file exists locally
        verify(storageService, never()).download(anyString());
    }

    @Test
    void testDownloadDocument_RemoteSupabaseFile_Success() throws IOException {
        CustomerRequest request = new CustomerRequest();
        setEntityId(request, 43L);

        UploadedDocument doc = new UploadedDocument();
        setEntityId(doc, 11L);
        doc.setRequest(request);
        doc.setFileName("citizen_id.pdf");
        doc.setFilePath("customer/43/uuid-photo.pdf");
        doc.setResultDocument(false);

        when(documentRepository.findById(11L)).thenReturn(Optional.of(doc));
        when(storageService.download("customer/43/uuid-photo.pdf")).thenReturn("Supabase remote content".getBytes());

        ResponseEntity<byte[]> response = controller.downloadDocument(11L, authentication);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals("Supabase remote content".getBytes(), response.getBody());
        verify(requestAccessService).requireRequestAccess(43L, authentication);
        verify(storageService).download("customer/43/uuid-photo.pdf");
    }

    @Test
    void testDownloadDocument_PathTraversalAttempt_DoesNotReadArbitraryLocalFile() throws IOException {
        CustomerRequest request = new CustomerRequest();
        setEntityId(request, 44L);

        UploadedDocument doc = new UploadedDocument();
        setEntityId(doc, 12L);
        doc.setRequest(request);
        doc.setFileName("malicious.pdf");
        doc.setFilePath("../pom.xml");
        doc.setResultDocument(true);

        when(documentRepository.findById(12L)).thenReturn(Optional.of(doc));
        when(storageService.download("../pom.xml")).thenReturn("remote fallback or error".getBytes());

        ResponseEntity<byte[]> response = controller.downloadDocument(12L, authentication);

        // It must NOT read the actual local pom.xml file directly
        assertNotNull(response);
        verify(storageService).download("../pom.xml");
    }

    @Test
    void testDeleteDocument_Success() {
        doNothing().when(retentionService).deleteCustomerDocument(15L, authentication);

        ResponseEntity<java.util.Map<String, Object>> response = controller.deleteDocument(15L, authentication);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));
        verify(retentionService).deleteCustomerDocument(15L, authentication);
    }

    @Test
    void testDeleteDocument_AccessDenied() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"))
                .when(retentionService).deleteCustomerDocument(16L, authentication);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                controller.deleteDocument(16L, authentication));
        verify(retentionService).deleteCustomerDocument(16L, authentication);
    }
}
