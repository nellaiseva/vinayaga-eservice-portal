package com.eservice1.employee;

import com.eservice1.employee.entity.Task;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.employee.service.TaskService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileUploadSecurityTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private CustomerRequestRepository requestRepository;
    @Mock
    private UploadedDocumentRepository uploadedDocumentRepository;

    private TaskService taskService;
    private File testUploadsDir;
    private final java.util.List<File> createdFilesToDelete = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, employeeRepository, requestRepository, uploadedDocumentRepository);
        testUploadsDir = new File(System.getProperty("user.dir"), "uploads");
    }

    @AfterEach
    void tearDown() {
        // Clean up only files explicitly created during tests
        for (File f : createdFilesToDelete) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }
        createdFilesToDelete.clear();
    }

    private Task createMockTask() {
        Task task = new Task();
        CustomerRequest request = new CustomerRequest();
        task.setRequest(request);
        return task;
    }

    @Test
    void testValidUpload_Success_SafeStorageNameGenerated() throws IOException {
        Long taskId = 1L;
        Task task = createMockTask();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "legit_document.pdf",
                "application/pdf",
                "Sample PDF content".getBytes()
        );

        taskService.uploadResult(taskId, file);

        ArgumentCaptor<UploadedDocument> docCaptor = ArgumentCaptor.forClass(UploadedDocument.class);
        verify(uploadedDocumentRepository).save(docCaptor.capture());

        UploadedDocument savedDoc = docCaptor.getValue();
        assertNotNull(savedDoc);

        // Verify storage file name uses safe generated UUID name, not user-controlled input
        assertTrue(savedDoc.getFileName().startsWith("RESULT_"));
        assertTrue(savedDoc.getFileName().endsWith(".pdf"));
        assertNotEquals("RESULT_legit_document.pdf", savedDoc.getFileName());

        // Verify file is stored strictly within uploads directory
        File targetFile = new File(savedDoc.getFilePath());
        createdFilesToDelete.add(targetFile);
        assertEquals(testUploadsDir.getCanonicalPath(), targetFile.getParentFile().getCanonicalPath());
        assertTrue(targetFile.exists());
    }

    @Test
    void testMaliciousFilename_PathTraversalDotDotSlash_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../test.txt",
                "text/plain",
                "Malicious content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testMaliciousFilename_PathTraversalBackslash_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\..\\test.txt",
                "text/plain",
                "Malicious content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testMaliciousFilename_EtcPasswd_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "/etc/passwd",
                "text/plain",
                "Malicious content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testMaliciousFilename_WindowsSystem32_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\Windows\\System32\\test.txt",
                "text/plain",
                "Malicious content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testDangerousExtension_Executable_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                "Binary content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testDangerousExtension_JspScript_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "shell.jsp",
                "application/octet-stream",
                "<% runtime.exec(); %>".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }

    @Test
    void testEmptyFile_Rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> taskService.uploadResult(1L, file));
    }
}
