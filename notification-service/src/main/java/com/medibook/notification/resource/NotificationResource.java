package com.medibook.notification.resource;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "*")
public class NotificationResource {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/recipient/{recipientId}")
    public ResponseEntity<List<NotificationResponse>> getByRecipient(
            @PathVariable int recipientId) {
        return ResponseEntity.ok(
            notificationService.getByRecipient(recipientId)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/unread/{recipientId}")
    public ResponseEntity<List<NotificationResponse>> getUnread(
            @PathVariable int recipientId) {
        return ResponseEntity.ok(
            notificationService.getUnreadByRecipient(recipientId)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/unread/{recipientId}/count")
    public ResponseEntity<UnreadCount> getUnreadCount(@PathVariable int recipientId) {
        UnreadCount uc = new UnreadCount();
        uc.setRecipientId(recipientId);
        uc.setUnreadCount(notificationService.getUnreadCount(recipientId));
        return ResponseEntity.ok(uc);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable int id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok(Map.of("message", "Notification marked as read", "notificationId", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/read-all/{recipientId}")
    public ResponseEntity<?> markAllRead(@PathVariable int recipientId) {
        notificationService.markAllRead(recipientId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read", "recipientId", recipientId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.ok(Map.of("message", "Notification deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@Valid @RequestBody SendNotificationRequest request) {
        try {
            Notification n = notificationService.send(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(n));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/events/appointment")
    public ResponseEntity<?> appointmentEvent(
            @Valid @RequestBody AppointmentEventRequest event) {
        try {
            notificationService.handleAppointmentEvent(event);
            return ResponseEntity.ok(Map.of("message", "Appointment event notifications sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/events/payment")
    public ResponseEntity<?> paymentEvent(
            @Valid @RequestBody PaymentEventRequest event) {
        try {
            notificationService.handlePaymentEvent(event);
            return ResponseEntity.ok(Map.of("message", "Payment event notifications sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/events/provider")
    public ResponseEntity<?> providerEvent(
            @Valid @RequestBody ProviderEventRequest event) {
        try {
            notificationService.handleProviderEvent(event);
            return ResponseEntity.ok(Map.of("message", "Provider event notification sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationResponse>> getAll() {
        return ResponseEntity.ok(
            notificationService.getAll().stream().map(this::toResponse).toList());
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkSendResult> sendBulk(
            @Valid @RequestBody BulkNotificationRequest request) {
        return ResponseEntity.ok(notificationService.sendBulk(request));
    }

    @DeleteMapping("/admin/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cleanup() {
        int deleted = notificationService.cleanup(30);
        return ResponseEntity.ok(Map.of(
            "message", "Cleanup complete",
            "deleted", deleted,
            "olderThanDays", 30));
    }

    @DeleteMapping("/admin/cleanup/{days}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cleanupDays(@PathVariable int days) {
        int deleted = notificationService.cleanup(days);
        return ResponseEntity.ok(Map.of(
            "message", "Cleanup complete",
            "deleted", deleted,
            "olderThanDays", days));
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setNotificationId(n.getNotificationId());
        r.setRecipientId(n.getRecipientId());
        r.setType(n.getType());
        r.setTitle(n.getTitle());
        r.setMessage(n.getMessage());
        r.setChannel(n.getChannel());
        r.setRelatedId(n.getRelatedId());
        r.setRelatedType(n.getRelatedType());
        r.setRead(n.isRead());
        r.setSentAt(n.getSentAt() != null ? n.getSentAt().toString() : "");
        return r;
    }
}