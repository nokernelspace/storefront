package org.springframework.boot.admin;

import org.jspecify.annotations.Nullable;

public interface SpringApplicationAdminMXBean {
   boolean isReady();

   boolean isEmbeddedWebApplication();

   @Nullable String getProperty(String key);

   void shutdown();
}
