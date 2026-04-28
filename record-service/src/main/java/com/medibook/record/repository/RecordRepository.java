package com.medibook.record.repository;

import com.medibook.record.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecordRepository extends JpaRepository<MedicalRecord, Integer> {

    Optional<MedicalRecord> findByAppointmentId(int appointmentId);

    List<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(int patientId);

    List<MedicalRecord> findByProviderIdOrderByCreatedAtDesc(int providerId);

    List<MedicalRecord> findByFollowUpDate(LocalDate followUpDate);

    @Query("SELECT r FROM MedicalRecord r WHERE " +
           "r.followUpDate = :today AND " +
           "r.followUpReminderSent = false")
    List<MedicalRecord> findPendingFollowUpReminders(@Param("today") LocalDate today);

    int countByPatientId(int patientId);

    boolean existsByAppointmentId(int appointmentId);

    void deleteByRecordId(int recordId);

    List<MedicalRecord> findByProviderIdAndPatientIdOrderByCreatedAtDesc(
            int providerId, int patientId);

    @Query("SELECT r FROM MedicalRecord r WHERE r.followUpDate IS NOT NULL " +
           "ORDER BY r.followUpDate ASC")
    List<MedicalRecord> findAllWithFollowUp();
}