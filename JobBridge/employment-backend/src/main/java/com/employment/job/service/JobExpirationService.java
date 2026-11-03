package com.employment.job.service;

import com.employment.global.common.Database;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class JobExpirationService {
    private final Database db;

    public JobExpirationService(Database db) {
        this.db = db;
    }

    // Catch up after downtime and retry each minute, including midnight in Korea.
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void closeExpiredJobs() {
        closeExpiredJobs(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    int closeExpiredJobs(LocalDate today) {
        return db.update(
            "UPDATE job_postings SET status='CLOSED', updated_at=CURRENT_TIMESTAMP " +
                "WHERE status='PUBLISHED' AND end_date<?",
            today
        );
    }
}
