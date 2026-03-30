# WebSocket Integration (Backend)

This backend exposes a STOMP over WebSocket endpoint for real-time notifications.

## WebSocket Endpoint

- **URL:** `ws://<server>/ws` (or `wss://<server>/ws` in TLS environments)
- Uses SockJS fallback for browsers that do not support native WebSockets.

## Authentication

The WebSocket connection is authenticated using the same JWT token used for REST API calls.
Send the JWT token in the `Authorization` header when establishing the WebSocket connection:

```
Authorization: Bearer <jwt-token>
```

Alternatively, clients can send the token as a query parameter: `?token=<jwt-token>`.

## Subscription Topics

### User-specific notifications (recommended)
Clients should subscribe to their user queue:

- `/user/queue/notifications`

The server will send notifications to the currently authenticated user.

### Team-level notifications (optional)
Clients can subscribe to team updates for any team they are a member of:

- `/topic/teams/{teamId}`

### Global notifications (optional)
Clients can subscribe to:

- `/topic/notifications`

## Message Format

Notifications are sent as JSON objects in the following structure:

```json
{
  "type": "TASK_UPDATED",
  "timestamp": "2026-03-13T12:34:56.789Z",
  "payload": {
    "taskId": 123,
    "taskTitle": "Fix bug",
    "message": "Alice updated task \"Fix bug\"",
    "teamId": 10
  }
}
```

- `type` is a string representing the event type (e.g., `TASK_CREATED`, `COMMENT_ADDED`).
- `payload` contains relevant metadata.

## Suggested Client Flow (Angular)

1. Connect to the WebSocket endpoint using SockJS + STOMP.
2. Authenticate by sending the JWT token as a header or query parameter.
3. Subscribe to `/user/queue/notifications`.
4. Listen for messages and update the UI accordingly (e.g., refresh task lists, show toast).

---

> **Note:** This file is intended as a quick-reference for frontend implementation; it is not required for backend execution and should not be committed to the repository.
