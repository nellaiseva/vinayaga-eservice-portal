package com.eservice1.submission.repository;

import com.eservice1.submission.entity.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT d FROM UploadedDocument d WHERE (d.resultDocument = false OR d.resultDocument IS NULL) AND d.uploadedAt IS NOT NULL AND d.uploadedAt <= :cutoff")
    List<UploadedDocument> findExpiredCustomerDocuments(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT d FROM UploadedDocument d WHERE d.resultDocument = true AND d.request.status = com.eservice1.submission.entity.RequestStatus.COMPLETED AND d.request.completedAt IS NOT NULL AND d.request.completedAt <= :cutoff")
    List<UploadedDocument> findExpiredResultDocuments(@Param("cutoff") LocalDateTime cutoff);
}