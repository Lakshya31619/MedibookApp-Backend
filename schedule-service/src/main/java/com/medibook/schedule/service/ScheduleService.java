package com.medibook.schedule.service;

import com.medibook.schedule.dto.ScheduleDto.*;
import com.medibook.schedule.entity.AvailabilitySlot;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleService {

    AvailabilitySlot addSlot(AddSlotRequest request);

    BulkResult addBulkSlots(BulkSlotRequest request);

    BulkResult generateRecurringSlots(RecurringSlotRequest request);

    List<AvailabilitySlot> getSlotsByProvider(int providerId);

    List<AvailabilitySlot> getAvailableSlots(int providerId, LocalDate date);

    List<AvailabilitySlot> getSlotsByDateRange(
            int providerId, LocalDate startDate, LocalDate endDate);

    AvailabilitySlot getSlotById(int slotId);

    void bookSlot(int slotId);

    void releaseSlot(int slotId);

    void blockSlot(int slotId);

    void unblockSlot(int slotId);

    void deleteSlot(int slotId);

    int purgeExpiredSlots();

    int countAvailableSlots(int providerId);

    List<AvailabilitySlot> getFutureAvailableSlots(int providerId);
}