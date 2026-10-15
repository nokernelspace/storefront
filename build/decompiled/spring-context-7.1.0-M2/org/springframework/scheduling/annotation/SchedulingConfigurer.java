package org.springframework.scheduling.annotation;

import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@FunctionalInterface
public interface SchedulingConfigurer {
   void configureTasks(ScheduledTaskRegistrar taskRegistrar);
}
