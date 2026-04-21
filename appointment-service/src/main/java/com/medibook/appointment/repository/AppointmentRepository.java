package com.medibook.appointment.repository;

import com.medibook.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

    List<Appointment> findByPatientId(int patientId);

    List<Appointment> findByProviderId(int providerId);

    Optional<Appointment> findBySlotId(int slotId);

    List<Appointment> findByStatus(String status);

    List<Appointment> findByProviderIdAndAppointmentDate(
            int providerId, LocalDate date);

    @Query("SELECT a FROM Appointment a WHERE " +
           "a.patientId = :patientId AND " +
           "a.status = 'SCHEDULED' AND " +
           "a.appointmentDate >= CURRENT_DATE " +
           "ORDER BY a.appointmentDate ASC, a.startTime ASC")
    List<Appointment> findUpcomingByPatientId(@Param("patientId") int patientId);

    @Query("SELECT a FROM Appointment a WHERE " +
           "a.providerId = :providerId AND " +
           "a.status = 'SCHEDULED' AND " +
           "a.appointmentDate >= CURRENT_DATE " +
           "ORDER BY a.appointmentDate ASC, a.startTime ASC")
    List<Appointment> findUpcomingByProviderId(@Param("providerId") int providerId);

    int countByProviderId(int providerId);

    int countByProviderIdAndStatus(int providerId, String status);

    @Query("SELECT a FROM Appointment a WHERE " +
           "a.providerId = :providerId AND " +
           "a.appointmentDate = CURRENT_DATE " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findTodayByProviderId(@Param("providerId") int providerId);

    List<Appointment> findByPatientIdAndStatus(int patientId, String status);

    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE " +
           "a.patientId = :patientId AND " +
           "a.providerId = :providerId AND " +
           "a.appointmentDate = :date AND " +
           "a.status = 'SCHEDULED'")
    boolean existsActiveBooking(
            @Param("patientId") int patientId,
            @Param("providerId") int providerId,
            @Param("date") LocalDate date);
}