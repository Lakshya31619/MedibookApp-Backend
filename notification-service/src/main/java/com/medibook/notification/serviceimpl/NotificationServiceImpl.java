package com.medibook.notification.serviceimpl;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.entity.NotificationType;
import com.medibook.notification.repository.NotificationRepository;
import com.medibook.notification.service.NotificationService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    @Transactional
    public Notification send(SendNotificationRequest request) {

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            request.setTitle(defaultTitle(request.getType()));
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            request.setMessage(defaultMessage(request));
        }

        List<String> channels = (request.getChannels() != null && !request.getChannels().isEmpty())
            ? request.getChannels()
            : List.of("APP");

        Notification appNotification = null;

        for (String raw : channels) {
            String ch = raw.toUpperCase();

            switch (ch) {
                case "APP" -> {
                    Notification n = buildNotification(request, "APP");
                    appNotification = notificationRepository.save(n);
                }
                case "EMAIL" -> {
                    Notification n = buildNotification(request, "EMAIL");
                    notificationRepository.save(n);
                    if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
                        sendEmail(request.getRecipientEmail(),
                                  request.getTitle(),
                                  buildEmailBody(request));
                    }
                }
                case "SMS" -> {
                    Notification n = buildNotification(request, "SMS");
                    notificationRepository.save(n);
                    sendSmsStub(request.getMessage());
                }
                default -> {
                    Notification n = buildNotification(request, "APP");
                    appNotification = notificationRepository.save(n);
                }
            }
        }

        if (appNotification != null) return appNotification;

        return notificationRepository
            .findByRecipientIdOrderBySentAtDesc(request.getRecipientId())
            .stream().findFirst()
            .orElseThrow(() -> new RuntimeException("Notification could not be saved"));
    }

    @Override
    @Transactional
    public BulkSendResult sendBulk(BulkNotificationRequest request) {
        int sent = 0, failed = 0;
        List<String> channels = (request.getChannels() != null && !request.getChannels().isEmpty())
            ? request.getChannels() : List.of("APP");

        for (int i = 0; i < request.getRecipientIds().size(); i++) {
            int recipientId = request.getRecipientIds().get(i);
            try {
                SendNotificationRequest single = new SendNotificationRequest();
                single.setRecipientId(recipientId);
                single.setType(request.getType());
                single.setTitle(request.getTitle());
                single.setMessage(request.getMessage());
                single.setChannels(channels);
                if (request.getRecipientEmails() != null && i < request.getRecipientEmails().size()) {
                    single.setRecipientEmail(request.getRecipientEmails().get(i));
                }
                send(single);
                sent++;
            } catch (Exception e) {
                System.err.println("Bulk send failed for recipientId=" + recipientId + ": " + e.getMessage());
                failed++;
            }
        }
        return new BulkSendResult(sent, failed);
    }

    @Override
    @Transactional
    public void handleAppointmentEvent(AppointmentEventRequest event) {
        String type = event.getEventType().toUpperCase();
        int apptId = event.getAppointmentId();

        switch (type) {

            case NotificationType.APPOINTMENT_BOOKED -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Confirmed ✅",
                    "Your appointment with " + orUnknown(event.getProviderName()) +
                    " is confirmed for " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + ".",
                    apptId, "APPOINTMENT");

                sendAppNotification(event.getProviderId(), NotificationType.NEW_BOOKING_FOR_PROVIDER,
                    "New Appointment Booked 📅",
                    "Patient " + orUnknown(event.getPatientName()) +
                    " has booked an appointment on " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + ".",
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_CONFIRMED -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Confirmed ✅",
                    "Your appointment on " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + " is confirmed.",
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_CANCELLED -> {
                String reason = event.getCancellationReason() != null
                    ? " Reason: " + event.getCancellationReason() : "";

                sendAppNotification(event.getPatientId(), type,
                    "Appointment Cancelled ❌",
                    "Your appointment on " + orUnknown(event.getAppointmentDate()) +
                    " has been cancelled." + reason,
                    apptId, "APPOINTMENT");

                sendAppNotification(event.getProviderId(), NotificationType.BOOKING_CANCELLED_FOR_PROVIDER,
                    "Appointment Cancelled by Patient",
                    "Patient " + orUnknown(event.getPatientName()) +
                    " cancelled their appointment on " + orUnknown(event.getAppointmentDate()) + "." + reason,
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_RESCHEDULED -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Rescheduled 🔄",
                    "Your appointment has been rescheduled to " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + ".",
                    apptId, "APPOINTMENT");

                sendAppNotification(event.getProviderId(), type,
                    "Appointment Rescheduled",
                    "Patient " + orUnknown(event.getPatientName()) +
                    " rescheduled their appointment to " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + ".",
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_REMINDER -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Reminder ⏰",
                    "Reminder: You have an appointment with " + orUnknown(event.getProviderName()) +
                    " tomorrow on " + orUnknown(event.getAppointmentDate()) +
                    " at " + orUnknown(event.getAppointmentTime()) + ". Please be on time.",
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_COMPLETED -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Completed ✅",
                    "Your appointment on " + orUnknown(event.getAppointmentDate()) +
                    " with " + orUnknown(event.getProviderName()) + " has been marked as completed." +
                    " We'd love to hear your feedback!",
                    apptId, "APPOINTMENT");

                sendAppNotification(event.getProviderId(), type,
                    "Appointment Completed",
                    "Appointment with patient " + orUnknown(event.getPatientName()) +
                    " on " + orUnknown(event.getAppointmentDate()) + " has been marked as completed.",
                    apptId, "APPOINTMENT");
            }

            case NotificationType.APPOINTMENT_NO_SHOW -> {
                sendAppNotification(event.getProviderId(), type,
                    "Patient No-Show 🚫",
                    "Patient " + orUnknown(event.getPatientName()) +
                    " did not show up for their appointment on " + orUnknown(event.getAppointmentDate()) +
                    ". The appointment has been marked as no-show.",
                    apptId, "APPOINTMENT");

                sendAppNotification(event.getPatientId(), type,
                    "Missed Appointment",
                    "You missed your appointment on " + orUnknown(event.getAppointmentDate()) +
                    " with " + orUnknown(event.getProviderName()) +
                    ". Please contact us if you need to reschedule.",
                    apptId, "APPOINTMENT");
            }

            default -> {
                sendAppNotification(event.getPatientId(), type,
                    "Appointment Update",
                    "Your appointment (ID: " + apptId + ") has been updated.",
                    apptId, "APPOINTMENT");
            }
        }
    }

    @Override
    @Transactional
    public void handlePaymentEvent(PaymentEventRequest event) {
        String type = event.getEventType().toUpperCase();

        switch (type) {

            case NotificationType.PAYMENT_RECEIVED -> {
                String amountStr = event.getAmount() != null
                    ? "₹" + String.format("%.2f", event.getAmount()) : "";

                sendAppNotification(event.getPatientId(), NotificationType.PAYMENT_RECEIVED,
                    "Payment Successful 💳",
                    "Your payment " + amountStr + " for your appointment on " +
                    orUnknown(event.getAppointmentDate()) + " was received successfully.",
                    event.getPaymentId(), "PAYMENT");

                sendAppNotification(event.getAdminId(), NotificationType.ADMIN_PAYMENT_RECEIVED,
                    "New Payment Received 💰",
                    "Payment " + amountStr + " received from patient ID " + event.getPatientId() +
                    " for appointment on " + orUnknown(event.getAppointmentDate()) + "." +
                    (event.getProviderName() != null ? " Provider: " + event.getProviderName() : ""),
                    event.getPaymentId(), "PAYMENT");

                if (event.getPatientEmail() != null && !event.getPatientEmail().isBlank()) {
                    sendEmail(event.getPatientEmail(),
                        "MediBook – Payment Receipt",
                        buildPaymentReceiptEmail(event));
                }
            }

            case NotificationType.PAYMENT_REFUNDED -> {
                String amountStr = event.getAmount() != null
                    ? "₹" + String.format("%.2f", event.getAmount()) : "";

                sendAppNotification(event.getPatientId(), NotificationType.PAYMENT_REFUNDED,
                    "Refund Initiated 💰",
                    "A refund of " + amountStr + " for your cancelled appointment has been initiated." +
                    " It will reflect in 3-5 business days.",
                    event.getPaymentId(), "PAYMENT");

                sendAppNotification(event.getAdminId(), NotificationType.ADMIN_PAYMENT_REFUNDED,
                    "Refund Issued 🔄",
                    "Refund " + amountStr + " issued to patient ID " + event.getPatientId() +
                    " for payment ID " + event.getPaymentId() + ".",
                    event.getPaymentId(), "PAYMENT");
            }

            default -> {
                sendAppNotification(event.getPatientId(), type,
                    "Payment Update",
                    "Your payment (ID: " + event.getPaymentId() + ") status has been updated.",
                    event.getPaymentId(), "PAYMENT");
            }
        }
    }

    @Override
    @Transactional
    public void handleProviderEvent(ProviderEventRequest event) {
        String type = event.getEventType().toUpperCase();

        switch (type) {

            case NotificationType.PROVIDER_REGISTERED -> {
                int adminRecipient = (event.getAdminId() != null) ? event.getAdminId() : 1;
                sendAppNotification(adminRecipient, NotificationType.PROVIDER_REGISTERED,
                    "New Provider Registration 🏥",
                    "A new provider " + orUnknown(event.getProviderName()) +
                    " has submitted their profile for verification. Please review and approve.",
                    event.getProviderId(), "PROVIDER");
            }

            case NotificationType.PROVIDER_APPROVED -> {
                sendAppNotification(event.getProviderId(), NotificationType.PROVIDER_APPROVED,
                    "Profile Approved 🎉",
                    "Congratulations " + orUnknown(event.getProviderName()) +
                    "! Your MediBook provider profile has been approved." +
                    " You can now accept patient appointments.",
                    event.getProviderId(), "PROVIDER");

                if (event.getProviderEmail() != null && !event.getProviderEmail().isBlank()) {
                    sendEmail(event.getProviderEmail(),
                        "MediBook – Profile Approved!",
                        "Dear " + orUnknown(event.getProviderName()) + ",\n\n" +
                        "We are pleased to inform you that your MediBook provider profile has been approved.\n" +
                        "You can now log in and start accepting patient appointments.\n\n" +
                        "Welcome to the MediBook family!\n\nTeam MediBook");
                }
            }

            case NotificationType.PROVIDER_REJECTED -> {
                String reason = event.getRejectionReason() != null
                    ? " Reason: " + event.getRejectionReason() : "";

                sendAppNotification(event.getProviderId(), NotificationType.PROVIDER_REJECTED,
                    "Profile Needs Attention ⚠️",
                    "Your MediBook provider profile was not approved at this time." + reason +
                    " Please update your profile and resubmit for review.",
                    event.getProviderId(), "PROVIDER");

                if (event.getProviderEmail() != null && !event.getProviderEmail().isBlank()) {
                    sendEmail(event.getProviderEmail(),
                        "MediBook – Profile Review Update",
                        "Dear " + orUnknown(event.getProviderName()) + ",\n\n" +
                        "Unfortunately, your provider profile was not approved at this time." + reason + "\n\n" +
                        "Please log in, update the required information and resubmit for review.\n\n" +
                        "If you have any questions please contact support@medibook.com.\n\nTeam MediBook");
                }
            }

            default -> {
                sendAppNotification(event.getProviderId(), type,
                    "Profile Update",
                    "Your provider profile status has been updated.",
                    event.getProviderId(), "PROVIDER");
            }
        }
    }

    @Override
    public List<Notification> getByRecipient(int recipientId) {
        return notificationRepository.findByRecipientIdOrderBySentAtDesc(recipientId);
    }

    @Override
    public List<Notification> getUnreadByRecipient(int recipientId) {
        return notificationRepository.findByRecipientIdAndIsReadOrderBySentAtDesc(recipientId, false);
    }

    @Override
    public int getUnreadCount(int recipientId) {
        return notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }

    @Override
    @Transactional
    public void markAsRead(int notificationId) {
        Notification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        n.setRead(true);
        notificationRepository.save(n);
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
    @Transactional
    public int cleanup(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        return notificationRepository.deleteOldReadNotifications(cutoff);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        if (!mailEnabled || mailSender == null) {
            System.out.println("[EMAIL DISABLED] Would send to: " + to + " | Subject: " + subject);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(mailFromName + " <" + mailFrom + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(mime);
            System.out.println("[EMAIL SENT] To: " + to);
        } catch (Exception e) {
            System.err.println("[EMAIL FAILED] To: " + to + " | Error: " + e.getMessage());
        }
    }

    private void sendAppNotification(int recipientId, String type,
                                     String title, String message,
                                     int relatedId, String relatedType) {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setType(type);
        req.setTitle(title);
        req.setMessage(message);
        req.setChannels(List.of("APP"));
        req.setRelatedId(relatedId);
        req.setRelatedType(relatedType);
        send(req);
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

    private String defaultTitle(String type) {
        return switch (type.toUpperCase()) {
            case NotificationType.APPOINTMENT_BOOKED        -> "Appointment Confirmed ✅";
            case NotificationType.APPOINTMENT_CANCELLED     -> "Appointment Cancelled ❌";
            case NotificationType.APPOINTMENT_RESCHEDULED   -> "Appointment Rescheduled 🔄";
            case NotificationType.APPOINTMENT_REMINDER      -> "Appointment Reminder ⏰";
            case NotificationType.APPOINTMENT_COMPLETED     -> "Appointment Completed ✅";
            case NotificationType.APPOINTMENT_NO_SHOW       -> "Missed Appointment 🚫";
            case NotificationType.NEW_BOOKING_FOR_PROVIDER  -> "New Appointment Booked 📅";
            case NotificationType.BOOKING_CANCELLED_FOR_PROVIDER -> "Appointment Cancelled";
            case NotificationType.PAYMENT_RECEIVED          -> "Payment Successful 💳";
            case NotificationType.PAYMENT_REFUNDED          -> "Refund Initiated 💰";
            case NotificationType.ADMIN_PAYMENT_RECEIVED    -> "New Payment Received 💰";
            case NotificationType.ADMIN_PAYMENT_REFUNDED    -> "Refund Issued 🔄";
            case NotificationType.PROVIDER_REGISTERED         -> "New Provider Registration 🏥";
            case NotificationType.PROVIDER_APPROVED         -> "Profile Approved 🎉";
            case NotificationType.PROVIDER_REJECTED         -> "Profile Needs Attention ⚠️";
            case NotificationType.REVIEW_RECEIVED           -> "New Review ⭐";
            default                                         -> "Notification";
        };
    }

    private String defaultMessage(SendNotificationRequest req) {
        return "You have a new notification of type: " + req.getType();
    }

    private String buildEmailBody(SendNotificationRequest req) {
        return "Dear User,\n\n" + req.getMessage() + "\n\nThank you,\nTeam MediBook";
    }

    private String buildPaymentReceiptEmail(PaymentEventRequest event) {
        String amountStr = event.getAmount() != null
            ? "₹" + String.format("%.2f", event.getAmount()) : "N/A";
        return "Dear Patient,\n\n" +
               "Thank you for your payment.\n\n" +
               "Payment ID   : " + event.getPaymentId() + "\n" +
               "Amount       : " + amountStr + "\n" +
               "Appointment  : " + orUnknown(event.getAppointmentDate()) + "\n" +
               "Provider     : " + orUnknown(event.getProviderName()) + "\n\n" +
               "Please keep this email as your receipt.\n\n" +
               "Team MediBook";
    }

    private void sendSmsStub(String message) {
        System.out.println("[SMS STUB] " + message);
    }

    private String orUnknown(String value) {
        return (value != null && !value.isBlank()) ? value : "N/A";
    }
}