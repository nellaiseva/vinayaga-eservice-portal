package com.eservice1.document;

import com.eservice1.common.Role;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.document.controller.DocumentController;
import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.submission.service.RealFileUploadService;
import com.eservice1.submission.service.RequestAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultipleDocumentUploadTest {

    @Mock
    private UploadedDocumentRepository documentRepository;

    @Mock
    private CustomerRequestRepository requestRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private RequestAccessService requestAccessService;

    private RealFileUploadService uploadService;
    private DocumentController documentController;

    private CustomerRequest request1;
    private CustomerRequest request2;
    private Authentication customer1Auth;
    private Authentication customer2Auth;
    private Authentication ownerAuth;

    private final AtomicLong documentIdGen = new AtomicLong(100L);
    private final List<UploadedDocument> databaseStore = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        uploadService = new RealFileUploadService(documentRepository, requestRepository, storageService);
        ReflectionTestUtils.setField(uploadService, "maxFileSize", 30971520L); // 30 MB

        documentController = new DocumentController(documentRepository, uploadService, requestAccessService);

        request1 = new CustomerRequest();
        ReflectionTestUtils.setField(request1, "id", 1L);
        request1.setCustomerName("Citizen One");
        request1.setPhoneNumber("9876543210");
        request1.setStatus(RequestStatus.PENDING);

        request2 = new CustomerRequest();
        ReflectionTestUtils.setField(request2, "id", 2L);
        request2.setCustomerName("Citizen Two");
        request2.setPhoneNumber("9123456780");
        request2.setStatus(RequestStatus.PENDING);

        customer1Auth = new UsernamePasswordAuthenticationToken(
                "9876543210", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
        customer2Auth = new UsernamePasswordAuthenticationToken(
                "9123456780", null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
        ownerAuth = new UsernamePasswordAuthenticationToken(
                "9999999999", null, List.of(new SimpleGrantedAuthority("OWNER")));

        databaseStore.clear();

        lenient().when(requestRepository.findById(1L)).thenReturn(Optional.of(request1));
        lenient().when(requestRepository.findById(2L)).thenReturn(Optional.of(request2));

        lenient().when(storageService.upload(any(), anyLong())).thenAnswer(inv ->
                "customer/" + inv.getArgument(1) + "/" + UUID.randomUUID() + ".pdf");

        lenient().when(documentRepository.save(any(UploadedDocument.class))).thenAnswer(inv -> {
            UploadedDocument doc = inv.getArgument(0);
            if (doc.getId() == null) {
                ReflectionTestUtils.setField(doc, "id", documentIdGen.incrementAndGet());
            }
            databaseStore.add(doc);
            return doc;
        });

        lenient().when(documentRepository.findByRequest_IdAndResultDocument(eq(1L), eq(false)))
                .thenAnswer(inv -> databaseStore.stream()
                        .filter(d -> d.getRequest().getId().equals(1L) && !Boolean.TRUE.equals(d.getResultDocument()))
                        .toList());

        lenient().when(documentRepository.findByRequest_IdAndDocumentTypeAndResultDocument(eq(1L), anyString(), eq(false)))
                .thenAnswer(inv -> {
                    String docType = inv.getArgument(1);
                    return databaseStore.stream()
                            .filter(d -> d.getRequest().getId().equals(1L)
                                    && docType.equalsIgnoreCase(d.getDocumentType())
                                    && !Boolean.TRUE.equals(d.getResultDocument()))
                            .toList();
                });
    }

    @Nested
    @DisplayName("Scenario 1: Upload Single Document")
    class SingleDocumentUploadTests {

        @Test
        @DisplayName("1. Successfully upload single Aadhaar document")
        void testUploadSingleAadhaarDocument() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "my_aadhaar.pdf", "application/pdf", "dummy pdf content".getBytes());

            UploadedDocument doc = documentController.uploadFile(
                    1L, file, "Aadhaar Card", "Aadhaar Card", false, customer1Auth);

            assertNotNull(doc);
            assertNotNull(doc.getId());
            assertEquals("Aadhaar Card", doc.getDocumentName());
            assertEquals("Aadhaar Card", doc.getDocumentType());
            assertEquals("my_aadhaar.pdf", doc.getFileName());
            assertTrue(doc.getFilePath().startsWith("customer/1/"));
            assertEquals(request1, doc.getRequest());
            assertFalse(doc.getResultDocument());
        }
    }

    @Nested
    @DisplayName("Scenario 2: Upload Two Documents of Same Type")
    class TwoDocumentsSameTypeTests {

        @Test
        @DisplayName("2. Upload two Aadhaar documents for the same service request without overwriting")
        void testUploadTwoAadhaarDocuments() throws IOException {
            MockMultipartFile file1 = new MockMultipartFile(
                    "file", "aadhaar_self.pdf", "application/pdf", "self aadhaar".getBytes());
            MockMultipartFile file2 = new MockMultipartFile(
                    "file", "aadhaar_father.pdf", "application/pdf", "father aadhaar".getBytes());

            UploadedDocument doc1 = documentController.uploadFile(
                    1L, file1, "Aadhaar Card 1", "Aadhaar Card", false, customer1Auth);

            UploadedDocument doc2 = documentController.uploadFile(
                    1L, file2, "Aadhaar Card 2", "Aadhaar Card", false, customer1Auth);

            assertNotNull(doc1);
            assertNotNull(doc2);
            assertNotEquals(doc1.getId(), doc2.getId());
            assertNotEquals(doc1.getFilePath(), doc2.getFilePath());

            assertEquals("Aadhaar Card 1", doc1.getDocumentName());
            assertEquals("Aadhaar Card 2", doc2.getDocumentName());
            assertEquals("Aadhaar Card", doc1.getDocumentType());
            assertEquals("Aadhaar Card", doc2.getDocumentType());

            List<UploadedDocument> stored = documentController.getDocumentsByRequest(1L, customer1Auth);
            assertEquals(2, stored.size());
        }
    }

    @Nested
    @DisplayName("Scenario 3: Upload Three Documents of Same Type")
    class ThreeDocumentsSameTypeTests {

        @Test
        @DisplayName("3. Upload three Aadhaar documents dynamically for one service")
        void testUploadThreeDocumentsSameType() throws IOException {
            MockMultipartFile file1 = new MockMultipartFile("file", "doc1.pdf", "application/pdf", "c1".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("file", "doc2.jpg", "image/jpeg", "c2".getBytes());
            MockMultipartFile file3 = new MockMultipartFile("file", "doc3.png", "image/png", "c3".getBytes());

            UploadedDocument d1 = documentController.uploadFile(1L, file1, "Aadhaar 1", "Aadhaar", false, customer1Auth);
            UploadedDocument d2 = documentController.uploadFile(1L, file2, "Aadhaar 2", "Aadhaar", false, customer1Auth);
            UploadedDocument d3 = documentController.uploadFile(1L, file3, "Aadhaar 3", "Aadhaar", false, customer1Auth);

            assertEquals(3, databaseStore.size());
            assertEquals("Aadhaar 1", d1.getDocumentName());
            assertEquals("Aadhaar 2", d2.getDocumentName());
            assertEquals("Aadhaar 3", d3.getDocumentName());

            List<UploadedDocument> aadhaarDocs = documentController.getDocumentsByRequestAndType(1L, "Aadhaar", customer1Auth);
            assertEquals(3, aadhaarDocs.size());
        }
    }

    @Nested
    @DisplayName("Scenario 4: Upload Multiple Document Types With Multiple Files")
    class MultipleDocumentTypesTests {

        @Test
        @DisplayName("4. Upload 2 Aadhaar documents and 1 Photograph independently")
        void testMultipleDocumentTypesWithMultiples() throws IOException {
            MockMultipartFile aadhaar1 = new MockMultipartFile("file", "a1.pdf", "application/pdf", "a1".getBytes());
            MockMultipartFile aadhaar2 = new MockMultipartFile("file", "a2.pdf", "application/pdf", "a2".getBytes());
            MockMultipartFile photo = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "photo".getBytes());

            documentController.uploadFile(1L, aadhaar1, "Aadhaar Card 1", "Aadhaar Card", false, customer1Auth);
            documentController.uploadFile(1L, aadhaar2, "Aadhaar Card 2", "Aadhaar Card", false, customer1Auth);
            documentController.uploadFile(1L, photo, "Photograph", "Photograph", false, customer1Auth);

            List<UploadedDocument> allDocs = documentController.getDocumentsByRequest(1L, customer1Auth);
            assertEquals(3, allDocs.size());

            List<UploadedDocument> aadhaarOnly = documentController.getDocumentsByRequestAndType(1L, "Aadhaar Card", customer1Auth);
            assertEquals(2, aadhaarOnly.size());

            List<UploadedDocument> photoOnly = documentController.getDocumentsByRequestAndType(1L, "Photograph", customer1Auth);
            assertEquals(1, photoOnly.size());
        }
    }

    @Nested
    @DisplayName("Scenario 5: Device Upload + Camera Capture")
    class DeviceUploadAndCameraCaptureTests {

        @Test
        @DisplayName("5. Upload one file from device and capture another using camera")
        void testDeviceUploadAndCameraCapture() throws IOException {
            // File from device (PDF)
            MockMultipartFile deviceFile = new MockMultipartFile(
                    "file", "scan_device.pdf", "application/pdf", "device bytes".getBytes());

            // Image captured from camera (JPEG)
            MockMultipartFile cameraCapture = new MockMultipartFile(
                    "file", "camera_capture.jpg", "image/jpeg", "camera bytes".getBytes());

            UploadedDocument doc1 = documentController.uploadFile(
                    1L, deviceFile, "Aadhaar 1", "Aadhaar", false, customer1Auth);
            UploadedDocument doc2 = documentController.uploadFile(
                    1L, cameraCapture, "Aadhaar 2", "Aadhaar", false, customer1Auth);

            assertNotNull(doc1);
            assertNotNull(doc2);
            assertEquals("scan_device.pdf", doc1.getFileName());
            assertEquals("camera_capture.jpg", doc2.getFileName());
            assertNotEquals(doc1.getFilePath(), doc2.getFilePath());
        }
    }

    @Nested
    @DisplayName("Scenario 8, 9 & 10: Association, Retrievability & Backward Compatibility")
    class AssociationAndRetrievalTests {

        @Test
        @DisplayName("8 & 9. Files associated with correct request and retrievable")
        void testAssociationAndRetrieval() throws IOException {
            MockMultipartFile file1 = new MockMultipartFile("file", "f1.pdf", "application/pdf", "f1".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("file", "f2.pdf", "application/pdf", "f2".getBytes());

            documentController.uploadFile(1L, file1, "Community Certificate 1", "Community Certificate", false, customer1Auth);
            documentController.uploadFile(1L, file2, "Community Certificate 2", "Community Certificate", false, customer1Auth);

            List<UploadedDocument> retrieved = documentController.getDocumentsByRequest(1L, customer1Auth);
            assertEquals(2, retrieved.size());
            for (UploadedDocument doc : retrieved) {
                assertEquals(1L, doc.getRequest().getId());
                assertEquals("Community Certificate", doc.getDocumentType());
            }
        }

        @Test
        @DisplayName("10. Backward compatible with single-document requests without documentType parameter")
        void testBackwardCompatibility() throws IOException {
            MockMultipartFile legacyFile = new MockMultipartFile(
                    "file", "income.pdf", "application/pdf", "legacy".getBytes());

            // Calling without documentType parameter defaults safely
            UploadedDocument legacyDoc = documentController.uploadFile(
                    1L, legacyFile, "Income Certificate", null, false, customer1Auth);

            assertEquals("Income Certificate", legacyDoc.getDocumentName());
            assertEquals("Income Certificate", legacyDoc.getDocumentType());
            assertEquals("income.pdf", legacyDoc.getFileName());
        }
    }

    @Nested
    @DisplayName("Scenario 11: Validation (Size, Type, Empty)")
    class FileValidationTests {

        @Test
        @DisplayName("11a. Reject empty file")
        void testRejectEmptyFile() {
            MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
            InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                    uploadService.uploadFile(1L, "Aadhaar", emptyFile, false));
            assertEquals("Please select a file.", ex.getMessage());
        }

        @Test
        @DisplayName("11b. Reject file exceeding 30 MB")
        void testRejectOversizedFile() {
            byte[] oversizedBytes = new byte[32 * 1024 * 1024]; // 32 MB
            MockMultipartFile bigFile = new MockMultipartFile("file", "big.pdf", "application/pdf", oversizedBytes);
            InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                    uploadService.uploadFile(1L, "Aadhaar", bigFile, false));
            assertEquals("Maximum file size is 30 MB.", ex.getMessage());
        }

        @Test
        @DisplayName("11c. Reject unsupported file type (.exe, .sh)")
        void testRejectUnsupportedFileType() {
            MockMultipartFile badFile = new MockMultipartFile(
                    "file", "script.sh", "application/x-sh", "echo bad".getBytes());
            InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                    uploadService.uploadFile(1L, "Aadhaar", badFile, false));
            assertEquals("Unsupported file type.", ex.getMessage());
        }

        @Test
        @DisplayName("11d. Reject upload on completed request")
        void testRejectUploadOnCompletedRequest() {
            request1.setStatus(RequestStatus.COMPLETED);
            MockMultipartFile file = new MockMultipartFile("file", "valid.pdf", "application/pdf", "ok".getBytes());
            InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                    uploadService.uploadFile(1L, "Aadhaar", file, false));
            assertEquals("Cannot upload documents for a completed request.", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Scenario 12: Authorization / IDOR Protection")
    class AuthorizationSecurityTests {

        @Test
        @DisplayName("12. Customer cannot access another customer's request documents")
        void testIdorProtectionAcrossCustomers() throws IOException {
            doThrow(new AccessDeniedException("You are not authorized to access this request."))
                    .when(requestAccessService).requireRequestAccess(eq(2L), eq(customer1Auth));

            MockMultipartFile file = new MockMultipartFile("file", "hack.pdf", "application/pdf", "hack".getBytes());

            assertThrows(AccessDeniedException.class, () ->
                    documentController.uploadFile(2L, file, "Aadhaar", "Aadhaar", false, customer1Auth));

            assertThrows(AccessDeniedException.class, () ->
                    documentController.getDocumentsByRequest(2L, customer1Auth));
        }
    }
}
