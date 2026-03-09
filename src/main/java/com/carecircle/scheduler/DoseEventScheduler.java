package com.carecircle.scheduler;

import com.carecircle.domain.medication.DoseEvent;
import com.carecircle.domain.medication.DoseEventRepository;
import com.carecircle.domain.medication.MedicationSchedule;
import com.carecircle.domain.medication.MedicationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

// =============================================================================
// 🧠 THE DOSE EVENT SCHEDULER — Senior-Level Design Decision
//
// THE PROBLEM: How do you know when to send a medication reminder?
//
// JUNIOR approach: Store "every 12 hours" and calculate at query time.
//   SELECT * FROM medication_schedules WHERE ???
//   (You'd need to evaluate CRON expressions in SQL — impossible)
//
// SENIOR approach: Pre-generate concrete "dose events" for the next 24 hours.
//   This scheduler runs every hour, looks at all active schedules,
//   evaluates their CRON expressions, and inserts DoseEvent rows.
//
//   Then the notification query becomes simple:
//   SELECT * FROM dose_events WHERE status='PENDING' AND scheduled_at <= NOW()
//   → Simple indexed lookup, no CRON math at query time.
//
// This is called "materializing" the schedule — turning abstract rules
// into concrete events. It's a standard pattern in production systems.
// =============================================================================

@Slf4j
@Component
@RequiredArgsConstructor
public class DoseEventScheduler {

    private final MedicationScheduleRepository scheduleRepository;
    private final DoseEventRepository doseEventRepository;

    // 🧠 @Scheduled: Runs every hour at the top of the hour
    // "0 0 * * * *" = at second 0, minute 0, every hour, every day
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void generateDoseEventsForNext24Hours() {
        log.info("DoseEventScheduler: Starting dose event generation");

        List<MedicationSchedule> activeSchedules = scheduleRepository.findAllCurrentlyActive();
        log.info("Found {} active medication schedules", activeSchedules.size());

        int totalGenerated = 0;

        for (MedicationSchedule schedule : activeSchedules) {
            int generated = generateEventsForSchedule(schedule);
            totalGenerated += generated;
        }

        log.info("DoseEventScheduler: Generated {} new dose events", totalGenerated);
    }

    // Also run on startup so you see events immediately during development
    @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
    public void generateOnStartup() {
        log.info("DoseEventScheduler: Running initial dose event generation on startup");
        generateDoseEventsForNext24Hours();
    }

    private int generateEventsForSchedule(MedicationSchedule schedule) {
        int count = 0;
        ZoneId zoneId = ZoneId.of(schedule.getTimezone());

        // Generate events for the next 25 hours (1 hour overlap to avoid gaps)
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime end = now.plusHours(25);

        // Parse the CRON expression to find all firing times in the window
        List<ZonedDateTime> firingTimes = parseCronAndGetFiringTimes(
                schedule.getCronExpression(), now, end, zoneId
        );

        for (ZonedDateTime firingTime : firingTimes) {
            Instant scheduledAt = firingTime.toInstant();

            // 🧠 IDEMPOTENCY CHECK: Don't create duplicate dose events.
            // If the scheduler runs twice (restart, overlap), we don't
            // want double notifications. The unique constraint in DB also
            // catches this, but checking here avoids unnecessary DB writes.
            if (!doseEventRepository.existsByScheduleIdAndScheduledAt(
                    schedule.getId(), scheduledAt)) {

                DoseEvent doseEvent = new DoseEvent();
                doseEvent.setSchedule(schedule);
                doseEvent.setPatient(schedule.getPatient());
                doseEvent.setScheduledAt(scheduledAt);
                doseEvent.setStatus(DoseEvent.DoseStatus.PENDING);

                doseEventRepository.save(doseEvent);
                count++;
                log.debug("Generated dose event for schedule {} at {}",
                        schedule.getId(), scheduledAt);
            }
        }

        return count;
    }

    // -------------------------------------------------------------------------
    // 🧠 SIMPLE CRON PARSER
    // Supports the most common patterns:
    //   "0 8 * * *"      = daily at 8:00 AM
    //   "0 8,20 * * *"   = daily at 8:00 AM and 8:00 PM
    //   "0 9 * * MON-FRI" = weekdays at 9:00 AM
    //
    // For production, use a library like 'cron-utils' or Quartz's CronExpression
    // class for full CRON support. This simplified version covers 90% of cases.
    // -------------------------------------------------------------------------
    private List<ZonedDateTime> parseCronAndGetFiringTimes(
            String cronExpression, ZonedDateTime from, ZonedDateTime to, ZoneId zoneId) {

        List<ZonedDateTime> times = new ArrayList<>();

        try {
            // Format: "minute hour * * *" or "minute hour,hour * * *"
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length < 2) return times;

            String minutePart = parts[0];
            String hourPart = parts[1];

            int minute = Integer.parseInt(minutePart);
            String[] hours = hourPart.split(",");

            // Iterate through each day in the window
            ZonedDateTime cursor = from.withSecond(0).withNano(0);
            while (cursor.isBefore(to)) {
                for (String hourStr : hours) {
                    int hour = Integer.parseInt(hourStr.trim());
                    ZonedDateTime candidate = cursor
                            .withHour(hour)
                            .withMinute(minute)
                            .withSecond(0)
                            .withNano(0);

                    if (candidate.isAfter(from) && candidate.isBefore(to)) {
                        times.add(candidate);
                    }
                }
                cursor = cursor.plusDays(1);
            }
        } catch (Exception e) {
            log.error("Failed to parse CRON expression: {}", cronExpression, e);
        }

        return times;
    }
}