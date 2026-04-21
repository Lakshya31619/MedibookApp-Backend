package com.medibook.appointment.service;

import com.medibook.appointment.dto.AppointmentDto.*;
import com.medibook.appointment.entity.Appointment;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {

    Appointment bookAppointment(BookAppointmentRequest request);

    Appointment getById(int appointmentId);

    List<Appointment> getByPatient(int patientId);

    List<Appointment> getByProvider(int providerId);

    List<Appointment> getByProviderAndDate(int providerId, LocalDate date);

    List<Appointment> getTodayByProvider(int providerId);

    List<Appointment> getUpcomingByPatient(int patientId);

    List<Appointment> getUpcomingByProvider(int providerId);

    void cancelAppointment(int appointmentId, String reason);

    Appointment rescheduleAppointment(int appointmentId, RescheduleRequest request);

    void completeAppointment(int appointmentId);

    void markNoShow(int appointmentId);

    void updateStatus(int appointmentId, String status);

    AppointmentCount getAppointmentCount(int providerId);

    List<Appointment> getAllAppointments();
}