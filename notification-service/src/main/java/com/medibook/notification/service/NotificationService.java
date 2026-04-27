package com.medibook.notification.service;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    Notification send(SendNotificationRequest request);

    BulkSendResult sendBulk(BulkNotificationRequest request);

    void handleAppointmentEvent(AppointmentEventRequest event);

    void handlePaymentEvent(PaymentEventRequest event);

    void handleProviderEvent(ProviderEventRequest event);

    List<Notification> getByRecipient(int recipientId);

    List<Notification> getUnreadByRecipient(int recipientId);

    int getUnreadCount(int recipientId);

    List<Notification> getAll();

    void markAsRead(int notificationId);

    void markAllRead(int recipientId);

    void deleteNotification(int notificationId);

    int cleanup(int daysOld);

    void sendEmail(String to, String subject, String body);
}