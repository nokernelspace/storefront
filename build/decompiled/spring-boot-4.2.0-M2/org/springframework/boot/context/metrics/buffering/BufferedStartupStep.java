package org.springframework.boot.context.metrics.buffering;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.metrics.StartupStep;
import org.springframework.core.metrics.StartupStep.Tag;
import org.springframework.core.metrics.StartupStep.Tags;
import org.springframework.util.Assert;

class BufferedStartupStep implements StartupStep {
   private final String name;
   private final long id;
   private final @Nullable BufferedStartupStep parent;
   private final List<Tag> tags = new ArrayList<>();
   private final Consumer<BufferedStartupStep> recorder;
   private final Instant startTime;
   private final AtomicBoolean ended = new AtomicBoolean();

   BufferedStartupStep(@Nullable BufferedStartupStep parent, String name, long id, Instant startTime, Consumer<BufferedStartupStep> recorder) {
      this.parent = parent;
      this.name = name;
      this.id = id;
      this.startTime = startTime;
      this.recorder = recorder;
   }

   @Nullable BufferedStartupStep getParent() {
      return this.parent;
   }

   public String getName() {
      return this.name;
   }

   public long getId() {
      return this.id;
   }

   Instant getStartTime() {
      return this.startTime;
   }

   public @Nullable Long getParentId() {
      return this.parent != null ? this.parent.getId() : null;
   }

   public Tags getTags() {
      return Collections.unmodifiableList(this.tags)::iterator;
   }

   public StartupStep tag(String key, Supplier<String> value) {
      return this.tag(key, value.get());
   }

   public StartupStep tag(String key, String value) {
      Assert.state(!this.ended.get(), "StartupStep has already ended.");
      this.tags.add(new BufferedStartupStep.DefaultTag(key, value));
      return this;
   }

   public void end() {
      this.ended.set(true);
      this.recorder.accept(this);
   }

   boolean isEnded() {
      return this.ended.get();
   }

   static class DefaultTag implements Tag {
      private final String key;
      private final String value;

      DefaultTag(String key, String value) {
         this.key = key;
         this.value = value;
      }

      public String getKey() {
         return this.key;
      }

      public String getValue() {
         return this.value;
      }
   }
}
