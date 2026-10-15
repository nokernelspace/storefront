package org.springframework.util;

import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class SimpleRouteMatcher implements RouteMatcher {
   private final PathMatcher pathMatcher;

   public SimpleRouteMatcher(PathMatcher pathMatcher) {
      Assert.notNull(pathMatcher, "PathMatcher is required");
      this.pathMatcher = pathMatcher;
   }

   public PathMatcher getPathMatcher() {
      return this.pathMatcher;
   }

   @Override
   public RouteMatcher.Route parseRoute(String route) {
      return new SimpleRouteMatcher.DefaultRoute(route);
   }

   @Override
   public boolean isPattern(String route) {
      return this.pathMatcher.isPattern(route);
   }

   @Override
   public String combine(String pattern1, String pattern2) {
      return this.pathMatcher.combine(pattern1, pattern2);
   }

   @Override
   public boolean match(String pattern, RouteMatcher.Route route) {
      return this.pathMatcher.match(pattern, route.value());
   }

   @Override
   public @Nullable Map<String, String> matchAndExtract(String pattern, RouteMatcher.Route route) {
      return !this.match(pattern, route) ? null : this.pathMatcher.extractUriTemplateVariables(pattern, route.value());
   }

   @Override
   public Comparator<String> getPatternComparator(RouteMatcher.Route route) {
      return this.pathMatcher.getPatternComparator(route.value());
   }

   private static class DefaultRoute implements RouteMatcher.Route {
      private final String path;

      DefaultRoute(String path) {
         this.path = path;
      }

      @Override
      public String value() {
         return this.path;
      }

      @Override
      public String toString() {
         return this.value();
      }
   }
}
