package org.springframework.core.metrics;

public interface ApplicationStartup {
   ApplicationStartup DEFAULT = new DefaultApplicationStartup();

   StartupStep start(String name);
}
