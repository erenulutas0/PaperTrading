package com.finance.core.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledLockAnnotationTest {

    private static final List<Class<?>> SCHEDULED_SERVICES = List.of(
            LeaderboardService.class,
            LiquidationService.class,
            OutcomeResolutionService.class,
            PerformanceTrackingService.class,
            TournamentService.class,
            PriceAlertService.class,
            TrustScoreService.class,
            StrategyBotForwardTestSchedulerService.class,
            com.finance.core.observability.WebSocketCanaryService.class,
            com.finance.core.observability.IdempotencyObservabilityService.class
    );

    @Test
    void scheduledMethodsShouldDeclareSchedulerLock() {
        List<String> missing = new ArrayList<>();

        for (Class<?> serviceClass : SCHEDULED_SERVICES) {
            for (Method method : serviceClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Scheduled.class)
                        && !method.isAnnotationPresent(SchedulerLock.class)) {
                    missing.add(serviceClass.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "Missing @SchedulerLock on scheduled methods: " + String.join(", ", missing));
    }

    @Test
    void schedulerLockNamesShouldBeUnique() {
        Set<String> names = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (Class<?> serviceClass : SCHEDULED_SERVICES) {
            for (Method method : serviceClass.getDeclaredMethods()) {
                SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);
                if (schedulerLock == null) {
                    continue;
                }
                if (!names.add(schedulerLock.name())) {
                    duplicates.add(schedulerLock.name());
                }
            }
        }

        assertTrue(duplicates.isEmpty(),
                "Duplicate @SchedulerLock names found: " + String.join(", ", duplicates));
    }

    @Test
    void schedulerLockNamesShouldFitShedLockSchema() {
        List<String> tooLong = new ArrayList<>();

        for (Class<?> serviceClass : SCHEDULED_SERVICES) {
            for (Method method : serviceClass.getDeclaredMethods()) {
                SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);
                if (schedulerLock == null) {
                    continue;
                }
                if (schedulerLock.name().length() > 64) {
                    tooLong.add(serviceClass.getSimpleName() + "." + method.getName() + "=" + schedulerLock.name().length());
                }
            }
        }

        assertTrue(tooLong.isEmpty(),
                "Overlong @SchedulerLock names found: " + String.join(", ", tooLong));
    }
}
