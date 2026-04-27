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
import java.util.HashMap;
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

    @Value("${provider.service.url}")
    private String providerServiceUrl;

    @Override
    @Transactional
    public Appointment bookAppointment(BookAppointmentRequest request) {

        Map<String, Object> slotDetails = fetchSlotDetails(request.getSlotId());
        LocalDate appointmentDate = LocalDate.parse((String) slotDetails.get("date"));
        String startTime = (String) slotDetails.get("startTime");

        if (appointmentRepository.existsActiveBooking(
                request.getPatientId(), request.getProviderId(), appointmentDate)) {
            throw new RuntimeException(
                "You already have a scheduled appointment with this provider on " + appointmentDate);
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
        appointment.setStartTime(java.time.LocalTime.parse(startTime));
        appointment.setEndTime(java.time.LocalTime.parse((String) slotDetails.get("endTime")));

        Appointment saved = appointmentRepository.save(appointment);

        int providerUserId = resolveProviderUserId(request.getProviderId());

        sendAppointmentEvent("APPOINTMENT_BOOKED", saved.getAppointmentId(),
            request.getPatientId(), providerUserId,
            appointmentDate.toString(), startTime, null, null, null);

        return saved;
    }

    @Override
    @Transactional
    public void cancelAppointment(int appointmentId, String reason) {
        Appointment appointment = getById(appointmentId);

        if (!appointment.getStatus().equals("SCHEDULED")) {
            throw new RuntimeException(
                "Only SCHEDULED appointments can be cancelled. Current status: " + appointment.getStatus());
        }

        appointment.setStatus("CANCELLED");
        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        releaseSlot(appointment.getSlotId());
        triggerRefund(appointmentId);

        int providerUserId = resolveProviderUserId(appointment.getProviderId());

        sendAppointmentEvent("APPOINTMENT_CANCELLED", appointmentId,
            appointment.getPatientId(), providerUserId,
            appointment.getAppointmentDate().toString(),
            appointment.getStartTime().toString(),
            null, null, reason);
    }

    @Override
    @Transactional
    public Appointment rescheduleAppointment(int appointmentId, RescheduleRequest request) {
        Appointment appointment = getById(appointmentId);

        if (!appointment.getStatus().equals("SCHEDULED")) {
            throw new RuntimeException("Only SCHEDULED appointments can be rescheduled.");
        }

        int oldSlotId = appointment.getSlotId();
        lockSlot(request.getNewSlotId());
        releaseSlot(oldSlotId);

        Map<String, Object> newSlotDetails = fetchSlotDetails(request.getNewSlotId());
        String newDate = (String) newSlotDetails.get("date");
        String newTime = (String) newSlotDetails.get("startTime");

        appointment.setSlotId(request.getNewSlotId());
        appointment.setAppointmentDate(LocalDate.parse(newDate));
        appointment.setStartTime(java.time.LocalTime.parse(newTime));
        appointment.setEndTime(java.time.LocalTime.parse((String) newSlotDetails.get("endTime")));

        Appointment updated = appointmentRepository.save(appointment);

        int providerUserId = resolveProviderUserId(appointment.getProviderId());

        sendAppointmentEvent("APPOINTMENT_RESCHEDULED", appointmentId,
            appointment.getPatientId(), providerUserId,
            newDate, newTime, null, null, null);

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

        int providerUserId = resolveProviderUserId(appointment.getProviderId());

        sendAppointmentEvent("APPOINTMENT_COMPLETED", appointmentId,
            appointment.getPatientId(), providerUserId,
            appointment.getAppointmentDate().toString(),
            appointment.getStartTime().toString(),
            null, null, null);
    }

    @Override
    @Transactional
    public void markNoShow(int appointmentId) {
        Appointment appointment = getById(appointmentId);
        appointment.setStatus("NO_SHOW");
        appointmentRepository.save(appointment);

        int providerUserId = resolveProviderUserId(appointment.getProviderId());

        sendAppointmentEvent("APPOINTMENT_NO_SHOW", appointmentId,
            appointment.getPatientId(), providerUserId,
            appointment.getAppointmentDate().toString(),
            appointment.getStartTime().toString(),
            null, null, null);
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
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + appointmentId));
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
        count.setCompleted(appointmentRepository.countByProviderIdAndStatus(providerId, "COMPLETED"));
        count.setScheduled(appointmentRepository.countByProviderIdAndStatus(providerId, "SCHEDULED"));
        count.setCancelled(appointmentRepository.countByProviderIdAndStatus(providerId, "CANCELLED"));
        return count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSlotDetails(int slotId) {
        try {
            return restTemplate.getForObject(scheduleServiceUrl + "/slots/" + slotId, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Could not fetch slot details: " + e.getMessage());
        }
    }

    private void lockSlot(int slotId) {
        try {
            restTemplate.put(scheduleServiceUrl + "/slots/" + slotId + "/book", null);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Slot is no longer available: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Could not communicate with schedule-service: " + e.getMessage());
        }
    }

    private void releaseSlot(int slotId) {
        try {
            restTemplate.put(scheduleServiceUrl + "/slots/" + slotId + "/release", null);
        } catch (Exception e) {
            System.err.println("Warning: Could not release slot " + slotId + ": " + e.getMessage());
        }
    }

    private void triggerRefund(int appointmentId) {
        try {
            restTemplate.postForObject(
                paymentServiceUrl + "/payments/refund/" + appointmentId, null, Map.class);
        } catch (Exception e) {
            System.err.println("Warning: Could not trigger refund: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private int resolveProviderUserId(int providerId) {
        try {
            Map<String, Object> provider = restTemplate.getForObject(
                providerServiceUrl + "/providers/" + providerId, Map.class);
            if (provider != null && provider.get("userId") != null) {
                return ((Number) provider.get("userId")).intValue();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not resolve provider userId for providerId="
                + providerId + " — falling back to providerId. Error: " + e.getMessage());
        }
        return providerId;
    }

    private void sendAppointmentEvent(String eventType, int appointmentId,
                                       int patientId, int providerUserId,
                                       String appointmentDate, String appointmentTime,
                                       String providerName, String patientName,
                                       String cancellationReason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType",       eventType);
            payload.put("appointmentId",   appointmentId);
            payload.put("patientId",       patientId);
            payload.put("providerId",      providerUserId);
            payload.put("appointmentDate", appointmentDate);
            payload.put("appointmentTime", appointmentTime);
            if (providerName       != null) payload.put("providerName",       providerName);
            if (patientName        != null) payload.put("patientName",        patientName);
            if (cancellationReason != null) payload.put("cancellationReason", cancellationReason);

            restTemplate.postForObject(
                notificationServiceUrl + "/notifications/events/appointment",
                payload, Map.class);
        } catch (Exception e) {
            System.err.println("Warning: Could not send appointment notification: " + e.getMessage());
        }
    }
}