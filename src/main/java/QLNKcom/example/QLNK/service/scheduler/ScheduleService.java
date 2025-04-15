package QLNKcom.example.QLNK.service.scheduler;

import QLNKcom.example.QLNK.DTO.user.CreateScheduleRequest;
import QLNKcom.example.QLNK.model.data.Schedule;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserProvider userProvider;
    private final Scheduler quartzScheduler;

    public Mono<Schedule> createSchedule(String email, String fullFeedKey, CreateScheduleRequest request) {
        return userProvider.findByEmail(email)
                .flatMap(user -> {
                    Schedule schedule = Schedule.builder()
                            .userId(user.getId())
                            .fullFeedKey(fullFeedKey)
                            .value(request.getValue())
                            .type(request.getType())
                            .time(request.getTime())
                            .day(request.getDay())
                            .note(request.getNote())
                            .dayOfWeek(request.getDayOfWeek())
                            .build();
                    return scheduleRepository.save(schedule)
                            .flatMap(savedSchedule -> {
                                savedSchedule.setJobKey(savedSchedule.getId());
                                scheduleJob(savedSchedule);
                                return scheduleRepository.save(savedSchedule);
                            });
                });
    }

    private void scheduleJob(Schedule schedule) {
        Mono.fromRunnable(() -> {
            try {
                JobDataMap jobDataMap = new JobDataMap();
                jobDataMap.put("userId", schedule.getUserId());
                jobDataMap.put("fullFeedKey", schedule.getFullFeedKey());
                jobDataMap.put("value", schedule.getValue());

                JobDetail job = JobBuilder.newJob(SendValueJob.class)
                        .withIdentity(schedule.getJobKey(), "schedule-jobs")
                        .usingJobData(jobDataMap)
                        .build();

                Trigger trigger = buildTrigger(schedule);
                quartzScheduler.scheduleJob(job, trigger);
                log.info("Scheduled job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
            } catch (SchedulerException e) {
                log.error("Failed to schedule job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey(), e);
                throw new RuntimeException("Failed to schedule job", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }


    public Flux<Schedule> getSchedulesByEmailAndFullFeedKey(String email, String fullFeedKey) {
        return userProvider.findByEmail(email)
                .flatMapMany(user -> scheduleRepository.findByUserIdAndFullFeedKey(user.getId(), fullFeedKey));
    }

    public Mono<Void> deleteSchedulesById(String idSchedule) {
        return scheduleRepository.findById(idSchedule)
                .flatMap(schedule -> Mono.fromRunnable(() -> {
                    if (schedule.getJobKey() != null) {
                        try {
                            JobKey jobKey = new JobKey(schedule.getJobKey(), "schedule-jobs");
                            if (quartzScheduler.checkExists(jobKey)) {
                                quartzScheduler.deleteJob(jobKey);
                                log.info("Deleted Quartz job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            } else {
                                log.info("No Quartz job found for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            }
                        } catch (SchedulerException e) {
                            log.error("Failed to delete Quartz job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey(), e);
                            throw new RuntimeException("Failed to delete Quartz job", e);
                        }
                    } else {
                        log.warn("No jobKey found for schedule: {}", schedule.getId());
                    }
                }).subscribeOn(Schedulers.boundedElastic()).then(scheduleRepository.deleteById(idSchedule)));
    }


    public Mono<Void> deleteSchedulesByUserIdAndFullFeedKey(String userId, String fullFeedKey) {
        return scheduleRepository.findByUserIdAndFullFeedKey(userId, fullFeedKey)
                .flatMap(schedule -> Mono.fromRunnable(() -> {
                    if (schedule.getJobKey() != null) {
                        try {
                            JobKey jobKey = new JobKey(schedule.getJobKey(), "schedule-jobs");
                            if (quartzScheduler.checkExists(jobKey)) {
                                quartzScheduler.deleteJob(jobKey);
                                log.info("Deleted Quartz job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            } else {
                                log.info("No Quartz job found for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            }
                        } catch (SchedulerException e) {
                            log.error("Failed to delete Quartz job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey(), e);
                            throw new RuntimeException("Failed to delete Quartz job", e);
                        }
                    } else {
                        log.warn("No jobKey found for schedule: {}", schedule.getId());
                    }
                }).subscribeOn(Schedulers.boundedElastic()).thenReturn(schedule))
                .then(scheduleRepository.deleteByUserIdAndFullFeedKey(userId, fullFeedKey));
    }


    public Mono<Void> rescheduleUserSchedules(String userId) {
        return scheduleRepository.findByUserId(userId)
                .flatMap(schedule -> {
                    if (schedule.getJobKey() == null) {
                        schedule.setJobKey(schedule.getId());
                        return scheduleRepository.save(schedule)
                                .doOnSuccess(this::scheduleJob);
                    }

                    return Mono.fromRunnable(() -> {
                        try {
                            JobKey jobKey = new JobKey(schedule.getJobKey(), "schedule-jobs");
                            if (!quartzScheduler.checkExists(jobKey)) {
                                scheduleJob(schedule);
                                log.info("Rescheduled job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            } else {
                                log.info("Job already exists for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey());
                            }
                        } catch (SchedulerException e) {
                            log.error("Failed to reschedule job for schedule: {}, jobKey: {}", schedule.getId(), schedule.getJobKey(), e);
                            throw new RuntimeException("Failed to reschedule Quartz job", e);
                        }
                    }).subscribeOn(Schedulers.boundedElastic()).thenReturn(schedule);
                })
                .then();
    }


    private Trigger buildTrigger(Schedule schedule) {
        String[] timeParts = schedule.getTime().split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        return switch (schedule.getType()) {
            case ONCE -> {
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.now()
                        .withDayOfMonth(schedule.getDay())
                        .withHour(hour)
                        .withMinute(minute)
                        .withSecond(0);
                yield TriggerBuilder.newTrigger()
                        .withIdentity(schedule.getId(), "schedule-triggers")
                        .startAt(java.util.Date.from(dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                        .build();
            }
            case DAILY -> TriggerBuilder.newTrigger()
                    .withIdentity(schedule.getId(), "schedule-triggers")
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(hour, minute))
                    .build();
            case WEEKLY -> TriggerBuilder.newTrigger()
                    .withIdentity(schedule.getId(), "schedule-triggers")
                    .withSchedule(CronScheduleBuilder.weeklyOnDayAndHourAndMinute(schedule.getDayOfWeek(), hour, minute))
                    .build();
            case MONTHLY -> TriggerBuilder.newTrigger()
                    .withIdentity(schedule.getId(), "schedule-triggers")
                    .withSchedule(CronScheduleBuilder.monthlyOnDayAndHourAndMinute(schedule.getDay(), hour, minute))
                    .build();
            default -> throw new IllegalArgumentException("Invalid schedule type");
        };
    }

    public Flux<Schedule> getUserSchedules(String email) {
        return userProvider.findByEmail(email)
                .flatMapMany(user -> scheduleRepository.findByUserId(user.getId()));
    }

    public Mono<Void> updateSchedulesForFeed(String userId, String oldFullFeedKey, String newFullFeedKey) {
        return scheduleRepository.findByUserIdAndFullFeedKey(userId, oldFullFeedKey)
                .flatMap(schedule -> {
                    schedule.setFullFeedKey(newFullFeedKey);
                    return scheduleRepository.save(schedule).then(Mono.empty());
                })
                .then();
    }
}