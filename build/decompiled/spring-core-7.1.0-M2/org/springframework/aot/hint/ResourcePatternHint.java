package org.springframework.aot.hint;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

public final class ResourcePatternHint implements ConditionalHint {
   private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
   private final String pattern;
   private final @Nullable TypeReference reachableType;

   ResourcePatternHint(String pattern, @Nullable TypeReference reachableType) {
      Assert.isTrue(
         "/".equals(pattern) || !pattern.startsWith("/"),
         () -> "Resource pattern [%s] must not start with a '/' unless it is the root directory".formatted(pattern)
      );
      this.pattern = pattern;
      this.reachableType = reachableType;
   }

   public String getPattern() {
      return this.pattern;
   }

   public boolean matches(String path) {
      return PATH_MATCHER.match(this.pattern, path);
   }

   @Override
   public @Nullable TypeReference getReachableType() {
      return this.reachableType;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ResourcePatternHint that && this.pattern.equals(that.pattern) && Objects.equals(this.reachableType, that.reachableType);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.pattern, this.reachableType);
   }
}
