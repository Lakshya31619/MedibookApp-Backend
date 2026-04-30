package com.medibook.schedule.resource;

import com.medibook.schedule.dto.ScheduleDto.*;
import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/slots")
public class ScheduleResource {

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping("/available/{providerId}")
    public ResponseEntity<List<SlotSummary>> getAvailable(
            @PathVariable int providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotSummary> slots = scheduleService
                .getAvailableSlots(providerId, date)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/provider/{providerId}/available")
    public ResponseEntity<List<SlotSummary>> getFutureAvailable(
            @PathVariable int providerId) {
        return ResponseEntity.ok(
            scheduleService.getFutureAvailableSlots(providerId)
                .stream().map(this::toSummary).toList()
        );
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> addSlot(@Valid @RequestBody AddSlotRequest request) {
        try {
            AvailabilitySlot slot = scheduleService.addSlot(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(slot));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> addBulk(@Valid @RequestBody BulkSlotRequest request) {
        try {
            BulkResult result = scheduleService.addBulkSlots(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/recurring")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> generateRecurring(
            @Valid @RequestBody RecurringSlotRequest request) {
        try {
            BulkResult result = scheduleService.generateRecurringSlots(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<SlotResponse>> getByProvider(
            @PathVariable int providerId) {
        return ResponseEntity.ok(
            scheduleService.getSlotsByProvider(providerId)
                .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/provider/{providerId}/range")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<SlotResponse>> getByRange(
            @PathVariable int providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
            scheduleService.getSlotsByDateRange(providerId, startDate, endDate)
                .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<?> getById(@PathVariable int slotId) {
        try {
            return ResponseEntity.ok(toResponse(scheduleService.getSlotById(slotId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerId}/count")
    public ResponseEntity<?> countAvailable(@PathVariable int providerId) {
        return ResponseEntity.ok(Map.of(
            "providerId", providerId,
            "availableSlots", scheduleService.countAvailableSlots(providerId)
        ));
    }

    @PutMapping("/{slotId}/book")
    public ResponseEntity<?> book(@PathVariable int slotId) {
        try {
            scheduleService.bookSlot(slotId);
            return ResponseEntity.ok(Map.of(
                "message", "Slot booked successfully",
                "slotId", slotId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{slotId}/release")
    public ResponseEntity<?> release(@PathVariable int slotId) {
        try {
            scheduleService.releaseSlot(slotId);
            return ResponseEntity.ok(Map.of(
                "message", "Slot released successfully",
                "slotId", slotId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{slotId}/block")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> block(@PathVariable int slotId) {
        try {
            scheduleService.blockSlot(slotId);
            return ResponseEntity.ok(Map.of("message", "Slot blocked", "slotId", slotId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{slotId}/unblock")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> unblock(@PathVariable int slotId) {
        try {
            scheduleService.unblockSlot(slotId);
            return ResponseEntity.ok(Map.of("message", "Slot unblocked", "slotId", slotId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> delete(@PathVariable int slotId) {
        try {
            scheduleService.deleteSlot(slotId);
            return ResponseEntity.ok(Map.of("message", "Slot deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> purge() {
        int deleted = scheduleService.purgeExpiredSlots();
        return ResponseEntity.ok(Map.of(
            "message", "Purge complete",
            "deletedSlots", deleted
        ));
    }

    private SlotResponse toResponse(AvailabilitySlot s) {
        SlotResponse r = new SlotResponse();
        r.setSlotId(s.getSlotId());
        r.setProviderId(s.getProviderId());
        r.setDate(s.getDate().toString());
        r.setStartTime(s.getStartTime().toString());
        r.setEndTime(s.getEndTime().toString());
        r.setDurationMinutes(s.getDurationMinutes());
        r.setBooked(s.isBooked());
        r.setBlocked(s.isBlocked());
        r.setRecurrence(s.getRecurrence());
        r.setCreatedAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
        return r;
    }

    private SlotSummary toSummary(AvailabilitySlot s) {
        SlotSummary sum = new SlotSummary();
        sum.setSlotId(s.getSlotId());
        sum.setDate(s.getDate().toString());
        sum.setStartTime(s.getStartTime().toString());
        sum.setEndTime(s.getEndTime().toString());
        sum.setDurationMinutes(s.getDurationMinutes());
        return sum;
    }
}