package com.medibook.record;

import com.medibook.record.dto.RecordDto.*;
import com.medibook.record.entity.MedicalRecord;
import com.medibook.record.repository.RecordRepository;
import com.medibook.record.serviceImpl.RecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordServiceImplTest {

    @Mock
    private RecordRepository recordRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RecordServiceImpl recordService;

    private MedicalRecord testRecord;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recordService,
            "notificationServiceUrl", "http://localhost:8088");

        testRecord = new MedicalRecord();
        testRecord.setRecordId(1);
        testRecord.setAppointmentId(10);
        testRecord.setPatientId(2);
        testRecord.setProviderId(3);
        testRecord.setDiagnosis("Flu");
        testRecord.setPrescription("Paracetamol 500mg");
        testRecord.setNotes("Rest and hydration");
        testRecord.setFollowUpReminderSent(false);
        testRecord.setCreatedAt(LocalDateTime.now());
    }

    // ── createRecord ──────────────────────────────────────────────────────────

    @Test
    void createRecord_ShouldCreate_WhenNoDuplicate() {
        CreateRecordRequest req = new CreateRecordRequest();
        req.setAppointmentId(10);
        req.setPatientId(2);
        req.setProviderId(3);
        req.setDiagnosis("Flu");
        req.setPrescription("Paracetamol");

        when(recordRepository.existsByAppointmentId(10)).thenReturn(false);
        when(recordRepository.save(any())).thenReturn(testRecord);

        MedicalRecord result = recordService.createRecord(req);

        assertNotNull(result);
        assertEquals("Flu", result.getDiagnosis());
        verify(recordRepository).save(any());
    }

    @Test
    void createRecord_ShouldThrow_WhenDuplicateAppointment() {
        CreateRecordRequest req = new CreateRecordRequest();
        req.setAppointmentId(10);
        req.setPatientId(2);
        req.setProviderId(3);
        req.setDiagnosis("Flu");

        when(recordRepository.existsByAppointmentId(10)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> recordService.createRecord(req));
    }

    @Test
    void createRecord_ShouldThrow_WhenFollowUpDateFormatInvalid() {
        CreateRecordRequest req = new CreateRecordRequest();
        req.setAppointmentId(11);
        req.setPatientId(2);
        req.setProviderId(3);
        req.setDiagnosis("Cold");
        req.setFollowUpDate("not-a-date");

        when(recordRepository.existsByAppointmentId(11)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> recordService.createRecord(req));
    }

    @Test
    void createRecord_ShouldSetFollowUpDate_WhenProvided() {
        CreateRecordRequest req = new CreateRecordRequest();
        req.setAppointmentId(12);
        req.setPatientId(2);
        req.setProviderId(3);
        req.setDiagnosis("Hypertension");
        req.setFollowUpDate(LocalDate.now().plusDays(14).toString());

        MedicalRecord saved = new MedicalRecord();
        saved.setFollowUpDate(LocalDate.now().plusDays(14));
        saved.setCreatedAt(LocalDateTime.now());

        when(recordRepository.existsByAppointmentId(12)).thenReturn(false);
        when(recordRepository.save(any())).thenReturn(saved);

        MedicalRecord result = recordService.createRecord(req);

        assertNotNull(result.getFollowUpDate());
    }

    // ── getRecordById ─────────────────────────────────────────────────────────

    @Test
    void getRecordById_ShouldReturn_WhenFound() {
        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));

        MedicalRecord result = recordService.getRecordById(1);

        assertEquals(1, result.getRecordId());
    }

    @Test
    void getRecordById_ShouldThrow_WhenNotFound() {
        when(recordRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> recordService.getRecordById(99));
    }

    // ── getRecordByAppointment ────────────────────────────────────────────────

    @Test
    void getRecordByAppointment_ShouldReturn_WhenFound() {
        when(recordRepository.findByAppointmentId(10)).thenReturn(Optional.of(testRecord));

        MedicalRecord result = recordService.getRecordByAppointment(10);

        assertEquals(10, result.getAppointmentId());
    }

    @Test
    void getRecordByAppointment_ShouldThrow_WhenNotFound() {
        when(recordRepository.findByAppointmentId(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> recordService.getRecordByAppointment(99));
    }

    // ── getRecordsByPatient ───────────────────────────────────────────────────

    @Test
    void getRecordsByPatient_ShouldReturnList() {
        when(recordRepository.findByPatientIdOrderByCreatedAtDesc(2)).thenReturn(List.of(testRecord));

        List<MedicalRecord> results = recordService.getRecordsByPatient(2);

        assertEquals(1, results.size());
    }

    // ── updateRecord ──────────────────────────────────────────────────────────

    @Test
    void updateRecord_ShouldUpdateFields_WithinEditWindow() {
        UpdateRecordRequest req = new UpdateRecordRequest();
        req.setDiagnosis("Updated Flu");
        req.setPrescription("Ibuprofen 400mg");
        req.setNotes("Stay hydrated");

        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MedicalRecord updated = recordService.updateRecord(1, req);

        assertEquals("Updated Flu", updated.getDiagnosis());
        assertEquals("Ibuprofen 400mg", updated.getPrescription());
    }

    @Test
    void updateRecord_ShouldThrow_WhenEditWindowExpired() {
        testRecord.setCreatedAt(LocalDateTime.now().minusHours(49));

        UpdateRecordRequest req = new UpdateRecordRequest();
        req.setDiagnosis("Too Late");

        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));

        assertThrows(RuntimeException.class, () -> recordService.updateRecord(1, req));
    }

    @Test
    void updateRecord_ShouldClearFollowUpDate_WhenEmptyStringProvided() {
        testRecord.setFollowUpDate(LocalDate.now().plusDays(7));

        UpdateRecordRequest req = new UpdateRecordRequest();
        req.setFollowUpDate(""); // empty string = clear

        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MedicalRecord updated = recordService.updateRecord(1, req);

        assertNull(updated.getFollowUpDate());
        assertFalse(updated.isFollowUpReminderSent());
    }

    @Test
    void updateRecord_ShouldThrow_WhenFollowUpDateFormatInvalid() {
        UpdateRecordRequest req = new UpdateRecordRequest();
        req.setFollowUpDate("bad-date");

        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));

        assertThrows(RuntimeException.class, () -> recordService.updateRecord(1, req));
    }

    // ── deleteRecord ──────────────────────────────────────────────────────────

    @Test
    void deleteRecord_ShouldDelete_WhenExists() {
        when(recordRepository.existsById(1)).thenReturn(true);
        doNothing().when(recordRepository).deleteByRecordId(1);

        assertDoesNotThrow(() -> recordService.deleteRecord(1));
        verify(recordRepository).deleteByRecordId(1);
    }

    @Test
    void deleteRecord_ShouldThrow_WhenNotFound() {
        when(recordRepository.existsById(99)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> recordService.deleteRecord(99));
    }

    // ── getRecordCount ────────────────────────────────────────────────────────

    @Test
    void getRecordCount_ShouldReturnCount() {
        when(recordRepository.countByPatientId(2)).thenReturn(5);

        assertEquals(5, recordService.getRecordCount(2));
    }

    // ── attachDocument ────────────────────────────────────────────────────────

    @Test
    void attachDocument_ShouldSetUrl() {
        when(recordRepository.findById(1)).thenReturn(Optional.of(testRecord));
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MedicalRecord result = recordService.attachDocument(1, "https://cdn.example.com/doc.pdf");

        assertEquals("https://cdn.example.com/doc.pdf", result.getAttachmentUrl());
    }

    // ── processFollowUpReminders ──────────────────────────────────────────────

    @Test
    void processFollowUpReminders_ShouldMarkReminderSent() {
        testRecord.setFollowUpDate(LocalDate.now());
        testRecord.setFollowUpReminderSent(false);

        when(recordRepository.findPendingFollowUpReminders(LocalDate.now()))
            .thenReturn(List.of(testRecord));
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int sent = recordService.processFollowUpReminders();

        assertEquals(1, sent);
        assertTrue(testRecord.isFollowUpReminderSent());
    }

    @Test
    void processFollowUpReminders_ShouldReturn_Zero_WhenNoPending() {
        when(recordRepository.findPendingFollowUpReminders(LocalDate.now()))
            .thenReturn(List.of());

        assertEquals(0, recordService.processFollowUpReminders());
    }
}
