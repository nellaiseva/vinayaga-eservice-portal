package com.eservice1.employee.controller;

import com.eservice1.employee.entity.Receipt;
import com.eservice1.employee.service.ReceiptService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.eservice1.employee.repository.ReceiptRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.File;
import com.eservice1.submission.service.RequestAccessService;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    private final ReceiptRepository receiptRepository;
    private final RequestAccessService requestAccessService;

    public ReceiptController(
            ReceiptService receiptService,
            ReceiptRepository receiptRepository,
            RequestAccessService requestAccessService) {

        this.receiptService = receiptService;
        this.receiptRepository = receiptRepository;
        this.requestAccessService = requestAccessService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadReceipt(
            @PathVariable Long id,
            Authentication authentication) {

        Receipt receipt =
                receiptRepository.findById(id)
                        .orElseThrow();

        requestAccessService.requireRequestAccess(
                receipt.getTask().getRequest().getId(),
                authentication
        );

        File file = receiptService.getValidatedReceiptFile(receipt);

        Resource resource =
                new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                receipt.getFileName() +
                                "\""
                )
                .body(resource);
    }

    @PostMapping("/{taskId}/upload")
    public Receipt uploadReceipt(
            @PathVariable Long taskId,
            @RequestParam MultipartFile file,
            Authentication authentication)
            throws IOException {

        return receiptService.uploadReceipt(
                taskId,
                file,
                authentication
        );
    }
}
