package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.MonitoringDtos.NotificationView;
import com.iitsm.backend.api.dto.MonitoringDtos.UnreadCount;
import com.iitsm.backend.api.dto.PageResponse;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.notification.Notification;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.NotificationRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** The current user's in-app notifications. Every query is scoped to the caller: nobody can read others' inboxes. */
@Service
@Transactional
public class InboxService {

    private final NotificationRepository notifications;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public InboxService(NotificationRepository notifications, CurrentUserService currentUser, Clock clock) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(boolean unreadOnly, Pageable pageable) {
        User user = currentUser.require();
        Page<Notification> page = unreadOnly
                ? notifications.findByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(user, pageable)
                : notifications.findByRecipientOrderByCreatedAtDescIdDesc(user, pageable);
        return PageResponse.of(page, NotificationView::of);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        return new UnreadCount(notifications.countByRecipientAndReadAtIsNull(currentUser.require()));
    }

    public NotificationView markRead(Long notificationId) {
        User user = currentUser.require();
        // Looking up by id AND recipient: someone else's notification id simply "does not exist" for you.
        Notification notification = notifications.findByIdAndRecipient(notificationId, user)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId));
        notification.markRead(clock.instant());
        return NotificationView.of(notification);
    }

    public UnreadCount markAllRead() {
        User user = currentUser.require();
        notifications.markAllRead(user, clock.instant());
        return new UnreadCount(0);
    }
}
