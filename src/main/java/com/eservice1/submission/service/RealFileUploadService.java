package com.eservice1.submission.service;

import com.eservice1.storage.StorageService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
@Service
public class RealFileUploadService {

    private final UploadedDocumentRepository documentRepository;
    private final CustomerRequestRepository requestRepository;
    private final StorageService storageService;


    @Value("${file.max-size}")
    private long maxFileSize;


    public RealFileUploadService(
            UploadedDocumentRepository documentRepository,
            CustomerRequestRepository requestRepository,
            StorageService storageService) {

        this.documentRepository = documentRepository;
        this.requestRepository = requestRepository;
        this.storageService = storageService;
    }

    public UploadedDocument uploadFile(
            Long requestId,
            String documentName,
            MultipartFile file,
            boolean isResult)throws IOException {

        CustomerRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Request not found."
                                )
                        );
        if (request.getStatus() == RequestStatus.COMPLETED) {

            throw new InvalidOperationException(
                    "Cannot upload documents for a completed request."
            );

        }
        if (file.isEmpty()) {

            throw new InvalidOperationException(
                    "Please select a file."
            );

        }
        if (file.getSize() > maxFileSize) {
            throw new InvalidOperationException(
                    "Maximum file size is 30 MB."
            );

        }
        Set<String> allowedTypes = Set.of(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "image/jpg",
                "image/heic",
                "image/heif"
        );
        if (!allowedTypes.contains(file.getContentType())) {

            throw new InvalidOperationException(
                    "Unsupported file type."
            );

        }
        if (documentName == null ||

                documentName.isBlank()) {

            throw new InvalidOperationException(
                    "Document name cannot be empty."
            );

        }

        //System.out.println("Saving to: " + filePath);

        String objectPath = storageService.upload(file, requestId);
        UploadedDocument document =
                new UploadedDocument();

        document.setDocumentName(documentName);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(objectPath);
        document.setRequest(request);
        document.setResultDocument(isResult);
        return documentRepository.save(document);
    }
    public UploadedDocument getDocument(Long documentId) {

        return documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Document not found."
                        )
                );
    }

    public byte[] downloadDocument(Long documentId) {

        UploadedDocument document =
                documentRepository.findById(documentId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Document not found."
                                )
                        );

        return storageService.download(
                document.getFilePath()
        );
    }
}