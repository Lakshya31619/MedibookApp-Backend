package com.medibook.appointment.serviceimpl;

import com.medibook.appointment.dto.AppointmentDto.*;
import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.repository.AppointmentRepository;
import com.medibook.appointment.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${schedule.service.url}")
    private String scheduleServiceUrl;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    @Override
    @Transactional
    public Appointment bookAppointment(BookAppointmentRequest request) {

        Map slotDetails = fetchSlotDetails(request.getSlotId());
        LocalDate appointmentDate = LocalDate.parse((String) slotDetails.get("date"));

        if (appointmentRepository.existsActiveBooking(
                request.getPatientId(),
                request.getProviderId(),
                appointmentDate)) {
            throw new RuntimeException(
                "You already have a scheduled appointment with this provider on "
                + appointmentDate);
        }

        lockSlot(request.getSlotId());

        Appointment appointment = new Appointment();
        appointment.setPatientId(request.getPatientId());
        appointment.setProviderId(request.getProviderId());
        appointment.setSlotId(request.getSlotId());
        appointment.setServiceType(request.getServiceType());
        appointment.setModeOfConsultation(request.getModeOfConsultation());
        appointment.setNotes(request.getNotes());
        appointment.setStatus("SCHEDULED");
        appointment.setAppointmentDate(appointmentDate);
        appointment.setStartTime(
            java.time.LocalTime.parse((String) slotDetails.get("startTime")));
        appointment.setEndTime(
            java.time.LocalTime.parse((String) slotDetails.get("endTime")));

        Appointment saved = appointmentRepository.save(appointment);

        sendNotification(
            request.getPatientId(),
            "BOOKING",
            "Appointment confirmed for " + appointmentDate + " at "
            + appointment.getStartTime(),
            saved.getAppointmentId()
        );

        return saved;
    }

    @Override
    @Transactional
    public void cancelAppointment(int appointmentId, String reason) {
        Appointment appointment = getById(appointmentId);

        if (!appointment.getStatus().equals("SCHEDULED")) {
            throw new RuntimeException(
                "Only SCHEDULED appointments can be cancelled. " +
                "Current status: " + appointment.getStatus());
        }

        appointment.setStatus("CANCELLED");
        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        releaseSlot(appointment.getSlotId());

        triggerRefund(appointmentId);

        sendNotification(
            appointment.getPatientId(),
            "CANCELLATION",
            "Your appointment on " + appointment.getAppointmentDate() + " has been cancelled.",
            appointmentId
        );
    }

    @Override
    @Transactional
    public Appointment rescheduleAppointment(int appointmentId,
                                              RescheduleRequest request) {
        Appointment appointment = getById(appointmentId);

        if (!appointment.getStatus().equals("SCHEDULED")) {
            throw new RuntimeException("Only SCHEDULED appointments can be rescheduled.");
        }

        int oldSlotId = appointment.getSlotId();

        lockSlot(request.getNewSlotId());

        releaseSlot(oldSlotId);

        Map newSlotDetails = fetchSlotDetails(request.getNewSlotId());

        appointment.setSlotId(request.getNewSlotId());
        appointment.setAppointmentDate(
            LocalDate.parse((String) newSlotDetails.get("date")));
        appointment.setStartTime(
            java.time.LocalTime.parse((String) newSlotDetails.get("startTime")));
        appointment.setEndTime(
            java.time.LocalTime.parse((String) newSlotDetails.get("endTime")));

        Appointment updated = appointmentRepository.save(appointment);

        sendNotification(
            appointment.getPatientId(),
            "RESCHEDULE",
            "Your appointment has been rescheduled to "
            + appointment.getAppointmentDate(),
            appointmentId
        );

        return updated;
    }

    @Override
    @Transactional
    public void completeAppointment(int appointmentId) {
        Appointment appointment = getById(appointmentId);
        if (!appointment.getStatus().equals("SCHEDULED")) {
            throw new RuntimeException("Only SCHEDULED appointments can be completed.");
        }
        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public void markNoShow(int appointmentId) {
        Appointment appointment = getById(appointmentId);
        appointment.setStatus("NO_SHOW");
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public void updateStatus(int appointmentId, String status) {
        Appointment appointment = getById(appointmentId);
        appointment.setStatus(status.toUpperCase());
        appointmentRepository.save(appointment);
    }

    @Override
    public Appointment getById(int appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "Appointment not found with id: " + appointmentId));
    }

    @Override
    public List<Appointment> getByPatient(int patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    @Override
    public List<Appointment> getByProvider(int providerId) {
        return appointmentRepository.findByProviderId(providerId);
    }

    @Override
    public List<Appointment> getByProviderAndDate(int providerId, LocalDate date) {
        return appointmentRepository.findByProviderIdAndAppointmentDate(providerId, date);
    }

    @Override
    public List<Appointment> getTodayByProvider(int providerId) {
        return appointmentRepository.findTodayByProviderId(providerId);
    }

    @Override
    public List<Appointment> getUpcomingByPatient(int patientId) {
        return appointmentRepository.findUpcomingByPatientId(patientId);
    }

    @Override
    public List<Appointment> getUpcomingByProvider(int providerId) {
        return appointmentRepository.findUpcomingByProviderId(providerId);
    }

    @Override
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Override
    public AppointmentCount getAppointmentCount(int providerId) {
        AppointmentCount count = new AppointmentCount();
        count.setProviderId(providerId);
        count.setTotal(appointmentRepository.countByProviderId(providerId));
        count.setCompleted(appointmentRepository.countByProviderIdAndStatus(
            providerId, "COMPLETED"));
        count.setScheduled(appointmentRepository.countByProviderIdAndStatus(
            providerId, "SCHEDULED"));
        count.setCancelled(appointmentRepository.countByProviderIdAndStatus(
            providerId, "CANCELLED"));
        return count;
    }

    @SuppressWarnings("unchecked")
    private Map fetchSlotDetails(int slotId) {
        try {
            return restTemplate.getForObject(
                scheduleServiceUrl + "/slots/" + slotId,
                Map.class
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "Could not fetch slot details from schedule-service: " + e.getMessage());
        }
    }

    private void lockSlot(int slotId) {
        try {
            restTemplate.put(
                scheduleServiceUrl + "/slots/" + slotId + "/book",
                null
            );
        } catch (HttpClientErrorException e) {
            throw new RuntimeException(
                "Slot is no longer available: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(
                "Could not communicate with schedule-service: " + e.getMessage());
        }
    }

    private void releaseSlot(int slotId) {
        try {
            restTemplate.put(
                scheduleServiceUrl + "/slots/" + slotId + "/release",
                null
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not release slot " + slotId
                + " in schedule-service: " + e.getMessage());
        }
    }

    private void triggerRefund(int appointmentId) {
        try {
            restTemplate.postForObject(
                paymentServiceUrl + "/payments/refund/" + appointmentId,
                null,
                Map.class
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not trigger refund for appointment "
                + appointmentId + ": " + e.getMessage());
        }
    }

    private void sendNotification(int recipientId, String type,
                                   String message, int relatedId) {
        try {
            Map<String, Object> payload = Map.of(
                "recipientId", recipientId,
                "type",        type,
                "message",     message,
                "relatedId",   relatedId,
                "relatedType", "APPOINTMENT"
            );
            restTemplate.postForObject(
                notificationServiceUrl + "/notifications/send",
                payload,
                Map.class
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not send notification: " + e.getMessage());
        }
    }
}