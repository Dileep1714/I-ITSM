package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.MonitoringDtos.NotificationView;
import com.iitsm.backend.api.dto.MonitoringDtos.UnreadCount;
import com.iitsm.backend.api.dto.PageResponse;
import com.iitsm.backend.service.InboxService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's notification inbox (the bell icon in the UI). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final InboxService inbox;

    public NotificationController(InboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return inbox.list(unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return inbox.unreadCount();
    }

    @PostMapping("/{id}/read")
    public NotificationView markRead(@PathVariable Long id) {
        return inbox.markRead(id);
    }

    @PostMapping("/read-all")
    public UnreadCount markAllRead() {
        return inbox.markAllRead();
    }
}
