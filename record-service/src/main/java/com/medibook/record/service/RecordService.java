package com.medibook.record.service;

import com.medibook.record.dto.RecordDto.*;
import com.medibook.record.entity.MedicalRecord;

import java.util.List;

public interface RecordService {

    MedicalRecord createRecord(CreateRecordRequest request);

    MedicalRecord getRecordById(int recordId);

    MedicalRecord getRecordByAppointment(int appointmentId);

    List<MedicalRecord> getRecordsByPatient(int patientId);

    List<MedicalRecord> getRecordsByProvider(int providerId);

    MedicalRecord updateRecord(int recordId, UpdateRecordRequest request);

    MedicalRecord attachDocument(int recordId, String attachmentUrl);

    void deleteRecord(int recordId);

    List<MedicalRecord> getAllRecords();

    List<MedicalRecord> getAllWithFollowUp();

    int getRecordCount(int patientId);

    int processFollowUpReminders();
}