package com.iitsm.backend.repository;

import com.iitsm.backend.domain.incident.Attachment;
import com.iitsm.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** Attachment metadata without the image bytes. */
    record AttachmentRow(Long id, String fileName, String contentType, long sizeBytes, Instant createdAt,
                         User uploadedBy) {
    }

    /** Metadata only (a constructor projection), so listing never reads the content column. */
    @Query("""
            select new com.iitsm.backend.repository.AttachmentRepository$AttachmentRow(
                a.id, a.fileName, a.contentType, a.sizeBytes, a.createdAt, a.uploadedBy)
            from Attachment a
            where a.incident.id = :incidentId
            order by a.createdAt
            """)
    List<AttachmentRow> listForIncident(Long incidentId);

    long countByIncidentId(Long incidentId);
}
