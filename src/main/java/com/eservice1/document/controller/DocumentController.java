package com.eservice1.document.controller;

import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.submission.service.RealFileUploadService;
import com.eservice1.submission.service.RequestAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final RealFileUploadService uploadService;
    private final UploadedDocumentRepository documentRepository;
    private final RequestAccessService requestAccessService;

    public DocumentController(
            UploadedDocumentRepository documentRepository,
            RealFileUploadService uploadService,
            RequestAccessService requestAccessService
    ) {
        this.documentRepository = documentRepository;
        this.uploadService = uploadService;
        this.requestAccessService = requestAccessService;
    }

    @PostMapping("/upload")
    public UploadedDocument uploadFile(
            @RequestParam Long requestId,
            @RequestParam MultipartFile file,
            @RequestParam(value = "documentName", required = false)
            String documentName,
            @RequestParam(value = "documentType", required = false)
            String documentType,
            @RequestParam(defaultValue = "false")
            boolean isResult,
            Authentication authentication
    ) throws IOException {

        requestAccessService.requireRequestAccess(requestId, authentication);

        if (isResult) {
            requestAccessService.requireResultDocumentUploadAccess(
                    requestId,
                    authentication
            );
        }

        String resolvedName = (documentName != null && !documentName.isBlank())
                ? documentName.trim()
                : file.getOriginalFilename();

        String resolvedType = (documentType != null && !documentType.isBlank())
                ? documentType.trim()
                : resolvedName;

        return uploadService.uploadFile(
                requestId,
                resolvedName,
                resolvedType,
                file,
                isResult
        );
    }

    @GetMapping("/request/{requestId}")
    public List<UploadedDocument> getDocumentsByRequest(
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        requestAccessService.requireRequestAccess(requestId, authentication);

        return documentRepository
                .findByRequest_IdAndResultDocument(
                        requestId,
                        false
                );
    }

    @GetMapping("/request/{requestId}/type/{documentType}")
    public List<UploadedDocument> getDocumentsByRequestAndType(
            @PathVariable Long requestId,
            @PathVariable String documentType,
            Authentication authentication
    ) {
        requestAccessService.requireRequestAccess(requestId, authentication);

        return documentRepository
                .findByRequest_IdAndDocumentTypeAndResultDocument(
                        requestId,
                        documentType,
                        false
                );
    }

    @GetMapping("/request/{requestId}/results")
    public List<UploadedDocument> getResultDocuments(
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        requestAccessService.requireRequestAccess(requestId, authentication);

        return documentRepository
                .findByRequest_IdAndResultDocument(
                        requestId,
                        true
                );
    }
}
