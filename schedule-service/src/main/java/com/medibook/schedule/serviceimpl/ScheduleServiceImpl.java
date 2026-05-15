package com.medibook.schedule.serviceimpl;

import com.medibook.schedule.dto.ScheduleDto.*;
import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.repository.SlotRepository;
import com.medibook.schedule.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Autowired
    private SlotRepository slotRepository;

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slotsByProvider", key = "#request.providerId"),
        @CacheEvict(value = "availableSlots",  allEntries = true)
    })
    public AvailabilitySlot addSlot(AddSlotRequest request) {

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new RuntimeException("endTime must be after startTime");
        }

        if (slotRepository.existsByProviderIdAndDateAndStartTime(
                request.getProviderId(),
                request.getDate(),
                request.getStartTime())) {
            throw new RuntimeException(
                "A slot already exists at " + request.getStartTime() +
                " on " + request.getDate());
        }

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setProviderId(request.getProviderId());
        slot.setDate(request.getDate());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setDurationMinutes(
            (int) java.time.Duration.between(
                request.getStartTime(), request.getEndTime()).toMinutes());
        slot.setBooked(false);
        slot.setBlocked(false);
        slot.setRecurrence("NONE");

        return slotRepository.save(slot);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slotsByProvider", key = "#request.providerId"),
        @CacheEvict(value = "availableSlots",  allEntries = true)
    })
    public BulkResult addBulkSlots(BulkSlotRequest request) {
        int created = 0;
        int skipped = 0;

        for (BulkSlotRequest.SlotEntry entry : request.getSlots()) {
            if (slotRepository.existsByProviderIdAndDateAndStartTime(
                    request.getProviderId(), entry.getDate(), entry.getStartTime())) {
                skipped++;
                continue;
            }

            AvailabilitySlot slot = new AvailabilitySlot();
            slot.setProviderId(request.getProviderId());
            slot.setDate(entry.getDate());
            slot.setStartTime(entry.getStartTime());
            slot.setEndTime(entry.getEndTime());
            slot.setDurationMinutes(
                (int) java.time.Duration.between(
                    entry.getStartTime(), entry.getEndTime()).toMinutes());
            slot.setBooked(false);
            slot.setBlocked(false);
            slot.setRecurrence("NONE");

            slotRepository.save(slot);
            created++;
        }

        return new BulkResult(created, skipped);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slotsByProvider", key = "#request.providerId"),
        @CacheEvict(value = "availableSlots",  allEntries = true)
    })
    public BulkResult generateRecurringSlots(RecurringSlotRequest request) {

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("endDate must be after startDate");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new RuntimeException("endTime must be after startTime");
        }

        List<DayOfWeek> targetDays = new ArrayList<>();
        if ("WEEKLY".equalsIgnoreCase(request.getRecurrenceType())
                && request.getDaysOfWeek() != null) {
            for (String day : request.getDaysOfWeek()) {
                targetDays.add(DayOfWeek.valueOf(day.toUpperCase()));
            }
        }

        // WEEKDAYS preset: Monday–Friday
        if ("WEEKDAYS".equalsIgnoreCase(request.getRecurrenceType())) {
            targetDays.add(DayOfWeek.MONDAY);
            targetDays.add(DayOfWeek.TUESDAY);
            targetDays.add(DayOfWeek.WEDNESDAY);
            targetDays.add(DayOfWeek.THURSDAY);
            targetDays.add(DayOfWeek.FRIDAY);
        }

        int created = 0;
        int skipped = 0;
        String recurrenceLabel = request.getRecurrenceType().toUpperCase();

        LocalDate current = request.getStartDate();
        while (!current.isAfter(request.getEndDate())) {

            boolean shouldGenerate = false;
            if ("DAILY".equalsIgnoreCase(request.getRecurrenceType())) {
                shouldGenerate = true;
            } else if ("WEEKLY".equalsIgnoreCase(request.getRecurrenceType())) {
                shouldGenerate = targetDays.contains(current.getDayOfWeek());
            } else if ("WEEKDAYS".equalsIgnoreCase(request.getRecurrenceType())) {
                shouldGenerate = targetDays.contains(current.getDayOfWeek());
            }

            if (shouldGenerate) {
                LocalTime slotStart = request.getStartTime();

                while (slotStart.plusMinutes(request.getSlotDurationMinutes())
                               .compareTo(request.getEndTime()) <= 0) {

                    LocalTime slotEnd = slotStart.plusMinutes(
                        request.getSlotDurationMinutes());

                    if (slotRepository.existsByProviderIdAndDateAndStartTime(
                            request.getProviderId(), current, slotStart)) {
                        skipped++;
                    } else {
                        AvailabilitySlot slot = new AvailabilitySlot();
                        slot.setProviderId(request.getProviderId());
                        slot.setDate(current);
                        slot.setStartTime(slotStart);
                        slot.setEndTime(slotEnd);
                        slot.setDurationMinutes(request.getSlotDurationMinutes());
                        slot.setBooked(false);
                        slot.setBlocked(false);
                        slot.setRecurrence(recurrenceLabel);
                        slotRepository.save(slot);
                        created++;
                    }

                    slotStart = slotEnd;
                }
            }

            current = current.plusDays(1);
        }

        return new BulkResult(created, skipped);
    }

    @Override
    @Cacheable(value = "slotsByProvider", key = "#providerId")
    public List<AvailabilitySlot> getSlotsByProvider(int providerId) {
        return slotRepository.findByProviderId(providerId);
    }

    @Override
    @Cacheable(value = "availableSlots", key = "#providerId + ':' + #date")
    public List<AvailabilitySlot> getAvailableSlots(int providerId, LocalDate date) {
        return slotRepository.findAvailableByProviderAndDate(providerId, date);
    }

    @Override
    public List<AvailabilitySlot> getSlotsByDateRange(
            int providerId, LocalDate startDate, LocalDate endDate) {
        return slotRepository.findByProviderIdAndDateBetween(
            providerId, startDate, endDate);
    }

    @Override
    @Cacheable(value = "slots", key = "#slotId")
    public AvailabilitySlot getSlotById(int slotId) {
        return slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException(
                    "Slot not found with id: " + slotId));
    }

    @Override
    public int countAvailableSlots(int providerId) {
        return slotRepository.countAvailableByProviderId(providerId);
    }

    @Override
    @Cacheable(value = "availableSlots", key = "'future:' + #providerId")
    public List<AvailabilitySlot> getFutureAvailableSlots(int providerId) {
        return slotRepository.findFutureAvailableByProvider(providerId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slots",           key = "#slotId"),
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true)
    })
    public void bookSlot(int slotId) {
        // FIX: fetch directly from DB (bypassing cache) before mutating, so we
        // don't operate on a stale cached object that shows the slot as un-booked.
        AvailabilitySlot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));

        if (slot.isBooked()) {
            throw new RuntimeException(
                "Slot " + slotId + " is already booked. Please choose another slot.");
        }
        if (slot.isBlocked()) {
            throw new RuntimeException(
                "Slot " + slotId + " is blocked and cannot be booked.");
        }

        slot.setBooked(true);
        slotRepository.save(slot);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slots",           key = "#slotId"),
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true)
    })
    public void releaseSlot(int slotId) {
        AvailabilitySlot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));
        slot.setBooked(false);
        slotRepository.save(slot);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slots",           key = "#slotId"),
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true)
    })
    public void blockSlot(int slotId) {
        AvailabilitySlot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));
        if (slot.isBooked()) {
            throw new RuntimeException(
                "Cannot block slot " + slotId + " — it is already booked.");
        }
        slot.setBlocked(true);
        slotRepository.save(slot);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slots",           key = "#slotId"),
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true)
    })
    public void unblockSlot(int slotId) {
        AvailabilitySlot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));
        slot.setBlocked(false);
        slotRepository.save(slot);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "slots",           key = "#slotId"),
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true)
    })
    public void deleteSlot(int slotId) {
        AvailabilitySlot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));
        if (slot.isBooked()) {
            throw new RuntimeException(
                "Cannot delete slot " + slotId + " — it has an active booking.");
        }
        slotRepository.deleteBySlotId(slotId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "availableSlots",  allEntries = true),
        @CacheEvict(value = "slotsByProvider", allEntries = true),
        @CacheEvict(value = "slots",           allEntries = true)
    })
    public int purgeExpiredSlots() {
        int deleted = slotRepository.deleteExpiredSlots(LocalDate.now());
        if (deleted > 0) {
            System.out.println("SlotExpiryJob: purged " + deleted + " expired slots");
        }
        return deleted;
    }
}