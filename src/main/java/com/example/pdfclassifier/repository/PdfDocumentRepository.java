package com.example.pdfclassifier.repository;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfDocumentRepository extends JpaRepository<PdfDocument, Long> {
    List<PdfDocument> findByUser(User user);
    List<PdfDocument> findByProcessingStatus(PdfDocument.ProcessingStatus status);
    Page<PdfDocument> findByProcessingStatus(PdfDocument.ProcessingStatus status, Pageable pageable);
    List<PdfDocument> findByUserAndProcessingStatus(User user, PdfDocument.ProcessingStatus status);
}
