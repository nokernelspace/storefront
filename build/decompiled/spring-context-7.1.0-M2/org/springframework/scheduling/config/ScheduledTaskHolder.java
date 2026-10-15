package org.springframework.scheduling.config;

import java.util.Set;

public interface ScheduledTaskHolder {
   Set<ScheduledTask> getScheduledTasks();
}
