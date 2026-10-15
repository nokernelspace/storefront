package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jspecify.annotations.Nullable;

public class MethodOverrides {
   private final Set<MethodOverride> overrides = new CopyOnWriteArraySet<>();

   public MethodOverrides() {
   }

   public MethodOverrides(MethodOverrides other) {
      this.addOverrides(other);
   }

   public void addOverrides(@Nullable MethodOverrides other) {
      if (other != null) {
         this.overrides.addAll(other.overrides);
      }
   }

   public void addOverride(MethodOverride override) {
      this.overrides.add(override);
   }

   public Set<MethodOverride> getOverrides() {
      return this.overrides;
   }

   public boolean isEmpty() {
      return this.overrides.isEmpty();
   }

   public @Nullable MethodOverride getOverride(Method method) {
      MethodOverride match = null;

      for (MethodOverride candidate : this.overrides) {
         if (candidate.matches(method)) {
            match = candidate;
         }
      }

      return match;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof MethodOverrides that && this.overrides.equals(that.overrides);
   }

   @Override
   public int hashCode() {
      return this.overrides.hashCode();
   }
}
