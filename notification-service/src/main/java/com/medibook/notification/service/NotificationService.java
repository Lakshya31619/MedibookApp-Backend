package com.medibook.notification.service;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    Notification send(SendNotificationRequest request);

    BulkSendResult sendBulk(BulkNotificationRequest request);

    List<Notification> getByRecipient(int recipientId);

    List<Notification> getUnreadByRecipient(int recipientId);

    int getUnreadCount(int recipientId);

    void markAsRead(int notificationId);

    void markAllRead(int recipientId);

    void deleteNotification(int notificationId);

    List<Notification> getAll();

    void sendEmail(String to, String subject, String body);

    void sendSms(String phoneNumber, String message);
}