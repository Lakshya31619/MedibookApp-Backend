package com.medibook.appointment.resource;

import com.medibook.appointment.dto.AppointmentDto.*;
import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/appointments")
@CrossOrigin(origins = "*")
public class AppointmentResource {

    @Autowired
    private AppointmentService appointmentService;

    @PostMapping("/book")
    public ResponseEntity<?> book(@Valid @RequestBody BookAppointmentRequest request) {
        try {
            Appointment appointment = appointmentService.bookAppointment(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(appointment));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            return ResponseEntity.ok(toResponse(appointmentService.getById(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AppointmentSummary>> getByPatient(
            @PathVariable int patientId) {
        return ResponseEntity.ok(
            appointmentService.getByPatient(patientId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/patient/{patientId}/upcoming")
    public ResponseEntity<List<AppointmentSummary>> getUpcomingByPatient(
            @PathVariable int patientId) {
        return ResponseEntity.ok(
            appointmentService.getUpcomingByPatient(patientId)
                .stream().map(this::toSummary).toList()
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable int id,
                                     @RequestBody(required = false) CancelRequest request) {
        try {
            String reason = (request != null) ? request.getReason() : "No reason provided";
            appointmentService.cancelAppointment(id, reason);
            return ResponseEntity.ok(Map.of(
                "message", "Appointment cancelled successfully",
                "appointmentId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable int id,
                                         @Valid @RequestBody RescheduleRequest request) {
        try {
            Appointment updated = appointmentService.rescheduleAppointment(id, request);
            return ResponseEntity.ok(toResponse(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<AppointmentSummary>> getByProvider(
            @PathVariable int providerId) {
        return ResponseEntity.ok(
            appointmentService.getByProvider(providerId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/provider/{providerId}/today")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<AppointmentSummary>> getToday(
            @PathVariable int providerId) {
        return ResponseEntity.ok(
            appointmentService.getTodayByProvider(providerId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/provider/{providerId}/upcoming")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<AppointmentSummary>> getUpcomingByProvider(
            @PathVariable int providerId) {
        return ResponseEntity.ok(
            appointmentService.getUpcomingByProvider(providerId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/provider/{providerId}/date")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<AppointmentSummary>> getByDate(
            @PathVariable int providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(
            appointmentService.getByProviderAndDate(providerId, date)
                .stream().map(this::toSummary).toList()
        );
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> complete(@PathVariable int id) {
        try {
            appointmentService.completeAppointment(id);
            return ResponseEntity.ok(Map.of(
                "message", "Appointment marked as completed",
                "appointmentId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> noShow(@PathVariable int id) {
        try {
            appointmentService.markNoShow(id);
            return ResponseEntity.ok(Map.of("message", "Appointment marked as no-show"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerId}/count")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<AppointmentCount> getCount(@PathVariable int providerId) {
        return ResponseEntity.ok(
            appointmentService.getAppointmentCount(providerId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponse>> getAll() {
        return ResponseEntity.ok(
            appointmentService.getAllAppointments()
                .stream().map(this::toResponse).toList()
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable int id,
                                           @RequestParam String value) {
        try {
            appointmentService.updateStatus(id, value);
            return ResponseEntity.ok(Map.of(
                "message", "Status updated to " + value,
                "appointmentId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private AppointmentResponse toResponse(Appointment a) {
        AppointmentResponse r = new AppointmentResponse();
        r.setAppointmentId(a.getAppointmentId());
        r.setPatientId(a.getPatientId());
        r.setProviderId(a.getProviderId());
        r.setSlotId(a.getSlotId());
        r.setServiceType(a.getServiceType());
        r.setAppointmentDate(a.getAppointmentDate().toString());
        r.setStartTime(a.getStartTime().toString());
        r.setEndTime(a.getEndTime().toString());
        r.setStatus(a.getStatus());
        r.setModeOfConsultation(a.getModeOfConsultation());
        r.setNotes(a.getNotes());
        r.setCancellationReason(a.getCancellationReason());
        r.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
        r.setUpdatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : "");
        return r;
    }

    private AppointmentSummary toSummary(Appointment a) {
        AppointmentSummary s = new AppointmentSummary();
        s.setAppointmentId(a.getAppointmentId());
        s.setPatientId(a.getPatientId());
        s.setProviderId(a.getProviderId());
        s.setServiceType(a.getServiceType());
        s.setAppointmentDate(a.getAppointmentDate().toString());
        s.setStartTime(a.getStartTime().toString());
        s.setEndTime(a.getEndTime().toString());
        s.setStatus(a.getStatus());
        s.setModeOfConsultation(a.getModeOfConsultation());
        return s;
    }
}