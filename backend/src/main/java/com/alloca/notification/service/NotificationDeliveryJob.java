package com.alloca.notification.service;

import org.springframework.stereotype.Component;

/**
 * Future scheduled delivery entry point.
 *
 * <p>TODO (ALC-44, ALC-47): introduce scheduling only after outbox persistence
 * exists. Claim records safely, make retries idempotent, and never hold booking locks
 * while an external email provider is called.
 */
@Component
public class NotificationDeliveryJob {
}
