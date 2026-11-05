package com.employment.job.service;

import com.employment.global.common.Database;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExpirationService {
    private final Database db;

    public JobExpirationService(Database db) {
        this.db = db;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void closeExpiredJobs() {
        db.update("UPDATE job_postings SET status='CLOSED',updated_at=CURRENT_TIMESTAMP WHERE status='PUBLISHED' AND end_date<?", EmploymentService.today());
        com.employment.ads.AdvertisingLifecycle.refresh(db);
    }
}
