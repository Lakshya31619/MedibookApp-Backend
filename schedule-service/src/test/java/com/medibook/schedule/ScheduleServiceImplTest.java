package com.medibook.schedule;

import com.medibook.schedule.dto.ScheduleDto.*;
import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.repository.SlotRepository;
import com.medibook.schedule.serviceimpl.ScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private SlotRepository slotRepository;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private AvailabilitySlot testSlot;

    @BeforeEach
    void setUp() {
        testSlot = new AvailabilitySlot();
        testSlot.setSlotId(1);
        testSlot.setProviderId(1);
        testSlot.setDate(LocalDate.now().plusDays(1));
        testSlot.setStartTime(LocalTime.of(9, 0));
        testSlot.setEndTime(LocalTime.of(9, 30));
        testSlot.setDurationMinutes(30);
        testSlot.setBooked(false);
        testSlot.setBlocked(false);
    }

    @Test
    void addSlot_ShouldCreate_WhenNoDuplicate() {
        AddSlotRequest req = new AddSlotRequest();
        req.setProviderId(1);
        req.setDate(LocalDate.now().plusDays(1));
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(9, 30));

        when(slotRepository.existsByProviderIdAndDateAndStartTime(
            anyInt(), any(), any())).thenReturn(false);
        when(slotRepository.save(any())).thenReturn(testSlot);

        AvailabilitySlot result = scheduleService.addSlot(req);

        assertNotNull(result);
        verify(slotRepository).save(any());
    }

    @Test
    void addSlot_ShouldThrow_WhenDuplicate() {
        AddSlotRequest req = new AddSlotRequest();
        req.setProviderId(1);
        req.setDate(LocalDate.now().plusDays(1));
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(9, 30));

        when(slotRepository.existsByProviderIdAndDateAndStartTime(
            anyInt(), any(), any())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> scheduleService.addSlot(req));
    }

    @Test
    void addSlot_ShouldThrow_WhenEndTimeBeforeStartTime() {
        AddSlotRequest req = new AddSlotRequest();
        req.setProviderId(1);
        req.setDate(LocalDate.now().plusDays(1));
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(9, 0));  // end before start

        assertThrows(RuntimeException.class, () -> scheduleService.addSlot(req));
    }

    @Test
    void bookSlot_ShouldSetBookedTrue() {
        when(slotRepository.findBySlotId(1)).thenReturn(Optional.of(testSlot));
        when(slotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduleService.bookSlot(1);

        assertTrue(testSlot.isBooked());
    }

    @Test
    void bookSlot_ShouldThrow_WhenAlreadyBooked() {
        testSlot.setBooked(true);
        when(slotRepository.findBySlotId(1)).thenReturn(Optional.of(testSlot));

        assertThrows(RuntimeException.class, () -> scheduleService.bookSlot(1));
    }

    @Test
    void releaseSlot_ShouldSetBookedFalse() {
        testSlot.setBooked(true);
        when(slotRepository.findBySlotId(1)).thenReturn(Optional.of(testSlot));
        when(slotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduleService.releaseSlot(1);

        assertFalse(testSlot.isBooked());
    }

    @Test
    void blockSlot_ShouldThrow_WhenAlreadyBooked() {
        testSlot.setBooked(true);
        when(slotRepository.findBySlotId(1)).thenReturn(Optional.of(testSlot));

        assertThrows(RuntimeException.class, () -> scheduleService.blockSlot(1));
    }

    @Test
    void deleteSlot_ShouldThrow_WhenBooked() {
        testSlot.setBooked(true);
        when(slotRepository.findBySlotId(1)).thenReturn(Optional.of(testSlot));

        assertThrows(RuntimeException.class, () -> scheduleService.deleteSlot(1));
    }

    @Test
    void generateRecurringSlots_ShouldThrow_WhenEndBeforeStart() {
        RecurringSlotRequest req = new RecurringSlotRequest();
        req.setProviderId(1);
        req.setStartDate(LocalDate.now().plusDays(7));
        req.setEndDate(LocalDate.now());  // end before start
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(30);
        req.setRecurrenceType("DAILY");

        assertThrows(RuntimeException.class,
            () -> scheduleService.generateRecurringSlots(req));
    }
}
