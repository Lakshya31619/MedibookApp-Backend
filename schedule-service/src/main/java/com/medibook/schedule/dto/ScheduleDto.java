package com.medibook.schedule.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ScheduleDto {

    @Data
    public static class AddSlotRequest {

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "date is required")
        private LocalDate date;

        @NotNull(message = "startTime is required")
        private LocalTime startTime;

        @NotNull(message = "endTime is required")
        private LocalTime endTime;
    }

    @Data
    public static class BulkSlotRequest {

        @NotNull
        private Integer providerId;

        @NotNull
        @Size(min = 1, message = "At least one slot required")
        private List<SlotEntry> slots;

        @Data
        public static class SlotEntry {
            private LocalDate date;
            private LocalTime startTime;
            private LocalTime endTime;
        }
    }

    @Data
    public static class RecurringSlotRequest {

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "startDate is required")
        private LocalDate startDate;

        @NotNull(message = "endDate is required")
        private LocalDate endDate;

        @NotNull(message = "startTime is required")
        private LocalTime startTime;

        @NotNull(message = "endTime is required")
        private LocalTime endTime;

        @Min(value = 5, message = "Slot duration must be at least 5 minutes")
        private int slotDurationMinutes = 30;

        @NotBlank(message = "recurrenceType is required")
        private String recurrenceType;

        private List<String> daysOfWeek;
    }

    @Data
    public static class SlotResponse {
        private int slotId;
        private int providerId;
        private String date;
        private String startTime;
        private String endTime;
        private int durationMinutes;
        private boolean isBooked;
        private boolean isBlocked;
        private String recurrence;
        private String createdAt;
    }

    @Data
    public static class SlotSummary {
        private int slotId;
        private String date;
        private String startTime;
        private String endTime;
        private int durationMinutes;
    }

    @Data
    public static class BulkResult {
        private int slotsCreated;
        private int slotsSkipped;
        private String message;

        public BulkResult(int created, int skipped) {
            this.slotsCreated = created;
            this.slotsSkipped = skipped;
            this.message = created + " slots created, " + skipped + " skipped (duplicates)";
        }
    }
}