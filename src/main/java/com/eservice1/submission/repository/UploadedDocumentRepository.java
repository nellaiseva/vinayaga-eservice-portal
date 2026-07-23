package com.eservice1.submission.repository;

import com.eservice1.submission.entity.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface UploadedDocumentRepository
        extends JpaRepository<UploadedDocument, Long> {

    List<UploadedDocument> findByRequest_Id(
            Long requestId
    );
    List<UploadedDocument>
    findByRequest_IdAndResultDocument(

            Long requestId,

            Boolean resultDocument

    );

    List<UploadedDocument> findByUploadedAtBefore(
            LocalDateTime dateTime
    );
}