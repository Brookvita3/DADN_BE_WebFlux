package QLNKcom.example.QLNK.service.scheduler;

import QLNKcom.example.QLNK.DTO.user.ScheduleRequest;
import QLNKcom.example.QLNK.model.data.Schedule;
import QLNKcom.example.QLNK.provider.user.UserProvider;
import QLNKcom.example.QLNK.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserProvider userProvider;
    private final Scheduler quartzScheduler;

    public Mono<Schedule> createSchedule(String email, String fullFeedKey, ScheduleRequest request) {

        return userProvider.findByEmail(email)
                .flatMap(user -> {
                    Schedule schedule = Schedule.builder()
                            .userId(user.getId())
                            .fullFeedKey(fullFeedKey)
                            .value(request.getValue())
                            .type(request.getType())
                            .time(request.getTime())
                            .day(request.getDay())
                            .dayOfWeek(request.getDayOfWeek())
                            .build();
                    return scheduleRepository.save(schedule)
                            .doOnSuccess(this::scheduleJob)
                            .map(savedSchedule -> {
                                savedSchedule.setJobKey(savedSchedule.getId());
                                return savedSchedule;
                            });
                });
    }


    private void scheduleJob(Schedule schedule) {
        try {
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("userId", schedule.getUserId());
            jobDataMap.put("fullFeedKey", schedule.getFullFeedKey());
            jobDataMap.put("value", schedule.getValue());

            JobDetail job = JobBuilder.newJob(SendValueJob.class)
                    .withIdentity(schedule.getId(), "schedule-jobs")
                    .usingJobData(jobDataMap)
                    .build();

            Trigger trigger = buildTrigger(schedule);
            quartzScheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule job", e);
        }
    }

    public Flux<Schedule> getSchedulesByEmailAndFullFeedKey(String email, String fullFeedKey) {
        return Flux.from(userProvider.findByEmail(email))
                        .flatMap(user -> scheduleRepository.findByUserIdAndFullFeedKey(user.getId(), fullFeedKey));
    }

    public Mono<Void> deleteSchedulesById(String idSchedule) {
        return scheduleRepository.deleteById(idSchedule);
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

}
