package org.springframework.web.server;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

public interface WebSession {
   String getId();

   Map<String, Object> getAttributes();

   default <T> @Nullable T getAttribute(String name) {
      return (T)this.getAttributes().get(name);
   }

   default <T> T getRequiredAttribute(String name) {
      T value = this.getAttribute(name);
      Assert.notNull(value, () -> "Required attribute '" + name + "' is missing.");
      return value;
   }

   default <T> T getAttributeOrDefault(String name, T defaultValue) {
      return (T)this.getAttributes().getOrDefault(name, defaultValue);
   }

   void start();

   boolean isStarted();

   Mono<Void> save();

   Mono<Void> changeSessionId();

   Mono<Void> invalidate();

   boolean isExpired();

   Instant getCreationTime();

   Instant getLastAccessTime();

   void setMaxIdleTime(Duration maxIdleTime);

   Duration getMaxIdleTime();
}
