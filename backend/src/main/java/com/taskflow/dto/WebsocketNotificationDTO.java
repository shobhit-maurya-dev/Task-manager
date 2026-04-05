package com.taskflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * A lightweight message payload sent to clients over WebSocket.
 */
@Data
@Builder
public class WebsocketNotificationDTO {

    /**
     * A simple type identifier (e.g. "TASK_UPDATED", "COMMENT_ADDED").
     */
    private String type;

    /**
     * Event timestamp (server time).
     */
    private Instant timestamp;

    /**
     * Optional payload containing any metadata the client needs.
     */
    private Map<String, Object> payload;

    /**
     * User email who initiated the action (for filtering).
     */
    private String initiatedBy;
}
