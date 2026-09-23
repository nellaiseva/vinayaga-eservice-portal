package com.eservice1.admin.controller;

import com.eservice1.document.entity.RequiredDocument;
import com.eservice1.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/services")
@PreAuthorize("hasAuthority('OWNER')")
public class AdminDocumentController {

    private final DocumentService documentService;

    public AdminDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/{serviceId}/documents")
    public RequiredDocument addDocument(

            @PathVariable Long serviceId,

            @Valid
            @RequestBody RequiredDocument document

    ) {

        return documentService.addDocument(serviceId, document);
    }

    @GetMapping("/{serviceId}/documents")
    public List<RequiredDocument> getDocuments(
            @PathVariable Long serviceId) {

        return documentService.getDocuments(serviceId);
    }

}