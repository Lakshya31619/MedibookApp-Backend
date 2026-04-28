package com.medibook.record.serviceImpl;

import com.medibook.record.dto.RecordDto.*;
import com.medibook.record.entity.MedicalRecord;
import com.medibook.record.repository.RecordRepository;
import com.medibook.record.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecordServiceImpl implements RecordService {

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    private static final int EDIT_WINDOW_HOURS = 48;

    @Override
    @Transactional
    public MedicalRecord createRecord(CreateRecordRequest request) {

        if (recordRepository.existsByAppointmentId(request.getAppointmentId())) {
            throw new RuntimeException(
                "A medical record already exists for appointmentId: "
                + request.getAppointmentId());
        }

        MedicalRecord record = new MedicalRecord();
        record.setAppointmentId(request.getAppointmentId());
        record.setPatientId(request.getPatientId());
        record.setProviderId(request.getProviderId());
        record.setDiagnosis(request.getDiagnosis());
        record.setPrescription(request.getPrescription());
        record.setNotes(request.getNotes());
        record.setFollowUpReminderSent(false);

        if (request.getFollowUpDate() != null && !request.getFollowUpDate().isBlank()) {
            try {
                record.setFollowUpDate(LocalDate.parse(request.getFollowUpDate()));
            } catch (Exception e) {
                throw new RuntimeException(
                    "Invalid followUpDate format. Use YYYY-MM-DD. Got: "
                    + request.getFollowUpDate());
            }
        }

        MedicalRecord saved = recordRepository.save(record);

        sendRecordCreatedNotification(saved);

        return saved;
    }

    @Override
    public MedicalRecord getRecordById(int recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException(
                    "Medical record not found with id: " + recordId));
    }

    @Override
    public MedicalRecord getRecordByAppointment(int appointmentId) {
        return recordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "No medical record found for appointmentId: " + appointmentId));
    }

    @Override
    public List<MedicalRecord> getRecordsByPatient(int patientId) {
        return recordRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    @Override
    public List<MedicalRecord> getRecordsByProvider(int providerId) {
        return recordRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    @Override
    public List<MedicalRecord> getAllRecords() {
        return recordRepository.findAll();
    }

    @Override
    public List<MedicalRecord> getAllWithFollowUp() {
        return recordRepository.findAllWithFollowUp();
    }

    @Override
    public int getRecordCount(int patientId) {
        return recordRepository.countByPatientId(patientId);
    }

    @Override
    @Transactional
    public MedicalRecord updateRecord(int recordId, UpdateRecordRequest request) {
        MedicalRecord record = getRecordById(recordId);

        LocalDateTime editDeadline = record.getCreatedAt()
                .plusHours(EDIT_WINDOW_HOURS);
        if (LocalDateTime.now().isAfter(editDeadline)) {
            throw new RuntimeException(
                "Medical record can no longer be edited. " +
                "The " + EDIT_WINDOW_HOURS + "-hour edit window has passed.");
        }

        if (request.getDiagnosis() != null && !request.getDiagnosis().isBlank())
            record.setDiagnosis(request.getDiagnosis());

        if (request.getPrescription() != null)
            record.setPrescription(request.getPrescription());

        if (request.getNotes() != null)
            record.setNotes(request.getNotes());

        if (request.getAttachmentUrl() != null)
            record.setAttachmentUrl(request.getAttachmentUrl());

        if (request.getFollowUpDate() != null) {
            if (request.getFollowUpDate().isBlank()) {
                record.setFollowUpDate(null);
                record.setFollowUpReminderSent(false);
            } else {
                try {
                    LocalDate newDate = LocalDate.parse(request.getFollowUpDate());
                    if (!newDate.equals(record.getFollowUpDate())) {
                        record.setFollowUpReminderSent(false);
                    }
                    record.setFollowUpDate(newDate);
                } catch (Exception e) {
                    throw new RuntimeException(
                        "Invalid followUpDate format. Use YYYY-MM-DD.");
                }
            }
        }

        return recordRepository.save(record);
    }

    @Override
    @Transactional
    public MedicalRecord attachDocument(int recordId, String attachmentUrl) {
        MedicalRecord record = getRecordById(recordId);
        record.setAttachmentUrl(attachmentUrl);
        return recordRepository.save(record);
    }

    @Override
    @Transactional
    public void deleteRecord(int recordId) {
        if (!recordRepository.existsById(recordId)) {
            throw new RuntimeException("Record not found with id: " + recordId);
        }
        recordRepository.deleteByRecordId(recordId);
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void scheduledFollowUpReminders() {
        System.out.println("FollowUpJob: running at " + LocalDateTime.now());
        int sent = processFollowUpReminders();
        System.out.println("FollowUpJob: sent " + sent + " follow-up reminders");
    }

    @Override
    @Transactional
    public int processFollowUpReminders() {
        List<MedicalRecord> pending = recordRepository
                .findPendingFollowUpReminders(LocalDate.now());

        int sent = 0;
        for (MedicalRecord record : pending) {
            try {
                sendFollowUpNotification(record);
                record.setFollowUpReminderSent(true);
                recordRepository.save(record);
                sent++;
            } catch (Exception e) {
                System.err.println("Failed to send follow-up for recordId="
                    + record.getRecordId() + ": " + e.getMessage());
            }
        }
        return sent;
    }

    private void sendRecordCreatedNotification(MedicalRecord record) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId",  record.getPatientId());
            payload.put("type",         "APPOINTMENT_COMPLETED");
            payload.put("title",        "Medical Record Available");
            payload.put("message",      "Your medical record from your recent consultation " +
                                        "is now available. Diagnosis: " + record.getDiagnosis());
            payload.put("channels",     List.of("APP", "EMAIL"));
            payload.put("relatedId",    record.getAppointmentId());
            payload.put("relatedType",  "APPOINTMENT");

            restTemplate.postForObject(
                notificationServiceUrl + "/notifications/events/appointment",
                payload,
                Map.class
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not send record-created notification: "
                + e.getMessage());
        }
    }

    private void sendFollowUpNotification(MedicalRecord record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId",  record.getPatientId());
        payload.put("type",         "APPOINTMENT_REMINDER");
        payload.put("title",        "Follow-Up Reminder");
        payload.put("message",      "You have a follow-up appointment scheduled for today. " +
                                    "Please contact your healthcare provider.");
        payload.put("channels",     List.of("APP", "EMAIL"));
        payload.put("relatedId",    record.getAppointmentId());
        payload.put("relatedType",  "APPOINTMENT");
        payload.put("appointmentDate", record.getFollowUpDate().toString());

        restTemplate.postForObject(
            notificationServiceUrl + "/notifications/events/appointment",
            payload,
            Map.class
        );
    }
}