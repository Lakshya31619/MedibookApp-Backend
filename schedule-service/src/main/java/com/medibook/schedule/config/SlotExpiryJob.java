package com.medibook.schedule.config;

import com.medibook.schedule.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlotExpiryJob {

    @Autowired
    private ScheduleService scheduleService;

    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredSlots() {
        System.out.println("SlotExpiryJob: running at " + java.time.LocalDateTime.now());
        int deleted = scheduleService.purgeExpiredSlots();
        System.out.println("SlotExpiryJob: deleted " + deleted + " expired slots");
    }
}