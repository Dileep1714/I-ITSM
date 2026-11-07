package com.iitsm.backend.repository;

import com.iitsm.backend.domain.notification.Notification;
import com.iitsm.backend.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientOrderByCreatedAtDescIdDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    List<Notification> findByRecipientAndIncidentId(User recipient, Long incidentId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.recipient = :recipient AND n.readAt IS NULL")
    int markAllRead(@Param("recipient") User recipient, @Param("at") Instant at);
}
