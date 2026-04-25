package com.medibook.notification.serviceimpl;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.repository.NotificationRepository;
import com.medibook.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@medibook.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:MediBook}")
    private String mailFromName;

    @Override
    @Transactional
    public Notification send(SendNotificationRequest request) {

        List<String> channels = (request.getChannels() != null
            && !request.getChannels().isEmpty())
            ? request.getChannels()
            : List.of("APP");

        Notification saved = null;

        for (String channel : channels) {
            String ch = channel.toUpperCase();

            if (ch.equals("APP")) {
                Notification notification = buildNotification(request, "APP");
                saved = notificationRepository.save(notification);
            }

            else if (ch.equals("EMAIL")) {
                Notification notification = buildNotification(request, "EMAIL");
                notificationRepository.save(notification);

                if (request.getRecipientEmail() != null
                        && !request.getRecipientEmail().isBlank()) {
                    sendEmail(
                        request.getRecipientEmail(),
                        request.getTitle(),
                        request.getMessage()
                    );
                }
            }

            else if (ch.equals("SMS")) {
                Notification notification = buildNotification(request, "SMS");
                notificationRepository.save(notification);
                sendSms("unknown", request.getMessage());
            }
        }

        return saved != null ? saved : notificationRepository
            .findByRecipientIdOrderBySentAtDesc(request.getRecipientId())
            .stream().findFirst()
            .orElseThrow(() -> new RuntimeException("Notification could not be saved"));
    }

    @Override
    @Transactional
    public BulkSendResult sendBulk(BulkNotificationRequest request) {
        int sent   = 0;
        int failed = 0;

        List<String> channels = (request.getChannels() != null
            && !request.getChannels().isEmpty())
            ? request.getChannels()
            : List.of("APP");

        for (int i = 0; i < request.getRecipientIds().size(); i++) {
            int recipientId = request.getRecipientIds().get(i);
            try {
                SendNotificationRequest single = new SendNotificationRequest();
                single.setRecipientId(recipientId);
                single.setType(request.getType());
                single.setTitle(request.getTitle());
                single.setMessage(request.getMessage());
                single.setChannels(channels);

                if (request.getRecipientEmails() != null
                        && i < request.getRecipientEmails().size()) {
                    single.setRecipientEmail(request.getRecipientEmails().get(i));
                }

                send(single);
                sent++;
            } catch (Exception e) {
                System.err.println("Failed to send notification to recipientId="
                    + recipientId + ": " + e.getMessage());
                failed++;
            }
        }

        return new BulkSendResult(sent, failed);
    }

    @Override
    public List<Notification> getByRecipient(int recipientId) {
        return notificationRepository
                .findByRecipientIdOrderBySentAtDesc(recipientId);
    }

    @Override
    public List<Notification> getUnreadByRecipient(int recipientId) {
        return notificationRepository
                .findByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    public int getUnreadCount(int recipientId) {
        return notificationRepository
                .countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }

    @Override
    @Transactional
    public void markAsRead(int notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException(
                    "Notification not found: " + notificationId));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(int recipientId) {
        notificationRepository.markAllReadByRecipientId(recipientId);
    }

    @Override
    @Transactional
    public void deleteNotification(int notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new RuntimeException("Notification not found: " + notificationId);
        }
        notificationRepository.deleteByNotificationId(notificationId);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        if (mailSender == null) {
            System.out.println("Email not configured — skipping email to: " + to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFromName + " <" + mailFrom + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("Email sent to: " + to);
        } catch (Exception e) {
            System.err.println("Email failed to " + to + ": " + e.getMessage());
        }
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        System.out.println("[SMS STUB] To: " + phoneNumber + " | Message: " + message);
    }

    private Notification buildNotification(SendNotificationRequest req, String channel) {
        Notification n = new Notification();
        n.setRecipientId(req.getRecipientId());
        n.setType(req.getType().toUpperCase());
        n.setTitle(req.getTitle());
        n.setMessage(req.getMessage());
        n.setChannel(channel);
        n.setRelatedId(req.getRelatedId() != null ? req.getRelatedId() : 0);
        n.setRelatedType(req.getRelatedType());
        n.setRead(false);
        return n;
    }
}