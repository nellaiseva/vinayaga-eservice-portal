package com.eservice1.employee.service;

import com.eservice1.employee.entity.Receipt;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.repository.ReceiptRepository;
import com.eservice1.employee.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.submission.service.RequestAccessService;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final TaskRepository taskRepository;
    private final RequestAccessService requestAccessService;

    @Value("${file.max-size}")
    private long maxFileSize;

    public ReceiptService(
            ReceiptRepository receiptRepository,
            TaskRepository taskRepository,
            RequestAccessService requestAccessService) {

        this.receiptRepository = receiptRepository;
        this.taskRepository = taskRepository;
        this.requestAccessService = requestAccessService;
    }

    public Receipt uploadReceipt(
            Long taskId,
            MultipartFile file,
            Authentication authentication)
            throws IOException {

        requestAccessService.requireReceiptUploadAccess(taskId, authentication);

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow();

        validateFile(file);

        Path uploadDir = Path.of("receipts")
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(uploadDir);

        String extension = extensionFor(file.getContentType());
        Path filePath = uploadDir.resolve(UUID.randomUUID() + extension)
                .normalize();

        if (!filePath.startsWith(uploadDir)) {
            throw new InvalidOperationException("Invalid receipt file path.");
        }

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Receipt receipt = new Receipt();

        receipt.setTask(task);
        receipt.setFileName(file.getOriginalFilename());
        receipt.setFilePath(filePath.toString());

        return receiptRepository.save(receipt);
    }

    public File getValidatedReceiptFile(Receipt receipt) {

        Path receiptDirectory = Path.of("receipts")
                .toAbsolutePath()
                .normalize();
        Path filePath = Path.of(receipt.getFilePath())
                .toAbsolutePath()
                .normalize();

        if (!filePath.startsWith(receiptDirectory)) {
            throw new InvalidOperationException("Invalid receipt file path.");
        }

        return filePath.toFile();
    }

    private void validateFile(MultipartFile file) {

        if (file.isEmpty()) {
            throw new InvalidOperationException("Please select a receipt file.");
        }

        if (file.getSize() > maxFileSize) {
            throw new InvalidOperationException("Maximum file size is 30 MB.");
        }

        if (!Map.of(
                "application/pdf", ".pdf",
                "image/jpeg", ".jpg",
                "image/png", ".png"
        ).containsKey(file.getContentType())) {
            throw new InvalidOperationException("Unsupported receipt file type.");
        }
    }

    private String extensionFor(String contentType) {

        return Map.of(
                "application/pdf", ".pdf",
                "image/jpeg", ".jpg",
                "image/png", ".png"
        ).get(contentType);
    }
}
