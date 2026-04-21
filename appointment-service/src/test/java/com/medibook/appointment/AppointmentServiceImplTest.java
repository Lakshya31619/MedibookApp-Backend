package com.medibook.appointment;

import com.medibook.appointment.dto.AppointmentDto.*;
import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.repository.AppointmentRepository;
import com.medibook.appointment.serviceimpl.AppointmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private Appointment testAppointment;

    @BeforeEach
    void setUp() {
        // Inject URL values since @Value doesn't work in unit tests
        ReflectionTestUtils.setField(appointmentService,
            "scheduleServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(appointmentService,
            "paymentServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(appointmentService,
            "notificationServiceUrl", "http://localhost:8088");

        testAppointment = new Appointment();
        testAppointment.setAppointmentId(1);
        testAppointment.setPatientId(2);
        testAppointment.setProviderId(1);
        testAppointment.setSlotId(5);
        testAppointment.setServiceType("General Consultation");
        testAppointment.setAppointmentDate(LocalDate.now().plusDays(2));
        testAppointment.setStartTime(LocalTime.of(9, 0));
        testAppointment.setEndTime(LocalTime.of(9, 30));
        testAppointment.setStatus("SCHEDULED");
        testAppointment.setModeOfConsultation("IN_PERSON");
    }

    @Test
    void getById_ShouldReturn_WhenFound() {
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));

        Appointment result = appointmentService.getById(1);

        assertNotNull(result);
        assertEquals(1, result.getAppointmentId());
    }

    @Test
    void getById_ShouldThrow_WhenNotFound() {
        when(appointmentRepository.findById(99))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> appointmentService.getById(99));
    }

    @Test
    void cancelAppointment_ShouldSetStatusCancelled() {
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        // RestTemplate calls are graceful — mock them to do nothing
        doNothing().when(restTemplate).put(anyString(), any());

        appointmentService.cancelAppointment(1, "Schedule conflict");

        assertEquals("CANCELLED", testAppointment.getStatus());
        assertEquals("Schedule conflict", testAppointment.getCancellationReason());
    }

    @Test
    void cancelAppointment_ShouldThrow_WhenAlreadyCancelled() {
        testAppointment.setStatus("CANCELLED");
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));

        assertThrows(RuntimeException.class,
            () -> appointmentService.cancelAppointment(1, "reason"));
    }

    @Test
    void completeAppointment_ShouldSetStatusCompleted() {
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        appointmentService.completeAppointment(1);

        assertEquals("COMPLETED", testAppointment.getStatus());
    }

    @Test
    void completeAppointment_ShouldThrow_WhenNotScheduled() {
        testAppointment.setStatus("CANCELLED");
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));

        assertThrows(RuntimeException.class,
            () -> appointmentService.completeAppointment(1));
    }

    @Test
    void markNoShow_ShouldSetStatusNoShow() {
        when(appointmentRepository.findById(1))
            .thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        appointmentService.markNoShow(1);

        assertEquals("NO_SHOW", testAppointment.getStatus());
    }

    @Test
    void getByPatient_ShouldReturnList() {
        when(appointmentRepository.findByPatientId(2))
            .thenReturn(List.of(testAppointment));

        List<Appointment> results = appointmentService.getByPatient(2);

        assertEquals(1, results.size());
    }

    @Test
    void getAppointmentCount_ShouldReturnCorrectStats() {
        when(appointmentRepository.countByProviderId(1)).thenReturn(10);
        when(appointmentRepository.countByProviderIdAndStatus(1, "COMPLETED")).thenReturn(6);
        when(appointmentRepository.countByProviderIdAndStatus(1, "SCHEDULED")).thenReturn(3);
        when(appointmentRepository.countByProviderIdAndStatus(1, "CANCELLED")).thenReturn(1);

        AppointmentCount count = appointmentService.getAppointmentCount(1);

        assertEquals(10, count.getTotal());
        assertEquals(6, count.getCompleted());
        assertEquals(3, count.getScheduled());
        assertEquals(1, count.getCancelled());
    }
}
