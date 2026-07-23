package com.eservice1.submission.controller;

import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/documents")
public class UploadedDocumentController {

    private final UploadedDocumentRepository documentRepository;
    private final StorageService storageService;

    public UploadedDocumentController(
            UploadedDocumentRepository documentRepository,
            StorageService storageService) {

        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    @GetMapping("/download/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long documentId) throws IOException {

        UploadedDocument document = documentRepository
                .findById(documentId)
                .orElseThrow();

        byte[] fileBytes =
                storageService.download(document.getFilePath());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                document.getFileName() +
                                "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }
}