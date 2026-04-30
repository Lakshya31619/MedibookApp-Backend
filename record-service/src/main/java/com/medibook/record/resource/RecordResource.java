package com.medibook.record.resource;

import com.medibook.record.dto.RecordDto.*;
import com.medibook.record.entity.MedicalRecord;
import com.medibook.record.service.RecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/records")
public class RecordResource {

    @Autowired
    private RecordService recordService;

    private static final int EDIT_WINDOW_HOURS = 48;

    @PostMapping
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateRecordRequest request) {
        try {
            MedicalRecord record = recordService.createRecord(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            return ResponseEntity.ok(toResponse(recordService.getRecordById(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getByAppointment(@PathVariable int appointmentId) {
        try {
            return ResponseEntity.ok(toResponse(recordService.getRecordByAppointment(appointmentId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<RecordResponse>> getByPatient(@PathVariable int patientId) {
        return ResponseEntity.ok(
            recordService.getRecordsByPatient(patientId)
                .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/patient/{patientId}/count")
    public ResponseEntity<Map<String, Integer>> getCount(@PathVariable int patientId) {
        return ResponseEntity.ok(Map.of("count", recordService.getRecordCount(patientId)));
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<RecordResponse>> getByProvider(@PathVariable int providerId) {
        return ResponseEntity.ok(
            recordService.getRecordsByProvider(providerId)
                .stream().map(this::toResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id,
                                     @RequestBody UpdateRecordRequest request) {
        try {
            return ResponseEntity.ok(toResponse(recordService.updateRecord(id, request)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/adminattach")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> attachDocument(@RequestBody Map<String, Object> body) {
        try {
            int recordId = Integer.parseInt(body.get("recordId").toString());
            String url   = body.get("attachmentUrl").toString();
            return ResponseEntity.ok(toResponse(recordService.attachDocument(recordId, url)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecordResponse>> getAll() {
        return ResponseEntity.ok(
            recordService.getAllRecords()
                .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/followup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecordResponse>> getAllWithFollowUp() {
        return ResponseEntity.ok(
            recordService.getAllWithFollowUp()
                .stream().map(this::toResponse).toList()
        );
    }

    @PostMapping("/followup/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> processFollowUp() {
        int sent = recordService.processFollowUpReminders();
        return ResponseEntity.ok(Map.of("sent", sent));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            recordService.deleteRecord(id);
            return ResponseEntity.ok(Map.of("message", "Record deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private RecordResponse toResponse(MedicalRecord r) {
        RecordResponse res = new RecordResponse();
        res.setRecordId(r.getRecordId());
        res.setAppointmentId(r.getAppointmentId());
        res.setPatientId(r.getPatientId());
        res.setProviderId(r.getProviderId());
        res.setDiagnosis(r.getDiagnosis());
        res.setPrescription(r.getPrescription());
        res.setNotes(r.getNotes());
        res.setAttachmentUrl(r.getAttachmentUrl());
        res.setFollowUpDate(r.getFollowUpDate() != null ? r.getFollowUpDate().toString() : null);
        res.setFollowUpReminderSent(r.isFollowUpReminderSent());
        res.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        res.setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);

        boolean editable = r.getCreatedAt() != null &&
                LocalDateTime.now().isBefore(r.getCreatedAt().plusHours(EDIT_WINDOW_HOURS));
        res.setEditable(editable);

        return res;
    }
}