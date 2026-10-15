package org.springframework.boot.web.error;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ErrorAttributeOptions {
   private final Set<ErrorAttributeOptions.Include> includes;

   private ErrorAttributeOptions(Set<ErrorAttributeOptions.Include> includes) {
      this.includes = includes;
   }

   public boolean isIncluded(ErrorAttributeOptions.Include include) {
      return this.includes.contains(include);
   }

   public Set<ErrorAttributeOptions.Include> getIncludes() {
      return this.includes;
   }

   public ErrorAttributeOptions including(ErrorAttributeOptions.Include... includes) {
      EnumSet<ErrorAttributeOptions.Include> updated = this.copyIncludes();
      updated.addAll(Arrays.asList(includes));
      return new ErrorAttributeOptions(Collections.unmodifiableSet(updated));
   }

   public ErrorAttributeOptions excluding(ErrorAttributeOptions.Include... excludes) {
      EnumSet<ErrorAttributeOptions.Include> updated = this.copyIncludes();
      Arrays.stream(excludes).forEach(updated::remove);
      return new ErrorAttributeOptions(Collections.unmodifiableSet(updated));
   }

   public void retainIncluded(Map<String, @Nullable Object> map) {
      for (ErrorAttributeOptions.Include candidate : ErrorAttributeOptions.Include.values()) {
         if (!this.includes.contains(candidate)) {
            map.remove(candidate.key);
         }
      }
   }

   private EnumSet<ErrorAttributeOptions.Include> copyIncludes() {
      return this.includes.isEmpty() ? EnumSet.noneOf(ErrorAttributeOptions.Include.class) : EnumSet.copyOf(this.includes);
   }

   public static ErrorAttributeOptions defaults() {
      return of(ErrorAttributeOptions.Include.PATH, ErrorAttributeOptions.Include.STATUS, ErrorAttributeOptions.Include.ERROR);
   }

   public static ErrorAttributeOptions of(ErrorAttributeOptions.Include... includes) {
      return of(Arrays.asList(includes));
   }

   public static ErrorAttributeOptions of(Collection<ErrorAttributeOptions.Include> includes) {
      return new ErrorAttributeOptions(includes.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(includes)));
   }

   public enum Include {
      EXCEPTION("exception"),
      STACK_TRACE("trace"),
      MESSAGE("message"),
      BINDING_ERRORS("errors"),
      STATUS("status"),
      ERROR("error"),
      PATH("path");

      private final String key;

      Include(String key) {
         this.key = key;
      }
   }
}
