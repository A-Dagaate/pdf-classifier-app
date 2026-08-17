package com.example.pdfclassifier.repository;

import com.example.pdfclassifier.entity.PdfDocumentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfDocumentContentRepository extends JpaRepository<PdfDocumentContent, Long> {

    /**
     * Document ids for one user, newest first, WITHOUT loading any blob columns.
     * Used to work out which content rows to prune; selecting the entity here
     * would pull every stored PDF into memory to decide what to delete.
     */
    @Query("select c.documentId from PdfDocumentContent c "
            + "where c.userId = :userId order by c.storedAt desc, c.documentId desc")
    List<Long> findDocumentIdsByUserNewestFirst(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
