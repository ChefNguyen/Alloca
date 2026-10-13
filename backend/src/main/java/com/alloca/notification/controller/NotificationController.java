package com.alloca.notification.controller;

import com.alloca.notification.service.NotificationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for in-app notifications and reminder settings.
 *
 * <p>TODO (ALC-42, ALC-44 to ALC-47): add list/read/settings endpoints only
 * when the durable notification workflow is implemented.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
