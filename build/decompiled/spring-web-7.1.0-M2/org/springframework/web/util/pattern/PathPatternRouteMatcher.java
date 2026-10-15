package org.springframework.web.util.pattern;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.util.Assert;
import org.springframework.util.RouteMatcher;
import org.springframework.util.RouteMatcher.Route;

public class PathPatternRouteMatcher implements RouteMatcher {
   private final PathPatternParser parser;
   private final Map<String, PathPattern> pathPatternCache = new ConcurrentHashMap<>();

   public PathPatternRouteMatcher() {
      this.parser = new PathPatternParser();
      this.parser.setPathOptions(PathContainer.Options.MESSAGE_ROUTE);
   }

   public PathPatternRouteMatcher(PathPatternParser parser) {
      Assert.notNull(parser, "PathPatternParser must not be null");
      this.parser = parser;
   }

   public Route parseRoute(String routeValue) {
      return new PathPatternRouteMatcher.PathContainerRoute(PathContainer.parsePath(routeValue, this.parser.getPathOptions()));
   }

   public boolean isPattern(String route) {
      return this.getPathPattern(route).hasPatternSyntax();
   }

   public String combine(String pattern1, String pattern2) {
      return this.getPathPattern(pattern1).combine(this.getPathPattern(pattern2)).getPatternString();
   }

   public boolean match(String pattern, Route route) {
      return this.getPathPattern(pattern).matches(this.getPathContainer(route));
   }

   public @Nullable Map<String, String> matchAndExtract(String pattern, Route route) {
      PathPattern.PathMatchInfo info = this.getPathPattern(pattern).matchAndExtract(this.getPathContainer(route));
      return info != null ? info.getUriVariables() : null;
   }

   public Comparator<String> getPatternComparator(Route route) {
      return Comparator.comparing(this::getPathPattern);
   }

   private PathPattern getPathPattern(String pattern) {
      return this.pathPatternCache.computeIfAbsent(pattern, this.parser::parse);
   }

   private PathContainer getPathContainer(Route route) {
      Assert.isInstanceOf(PathPatternRouteMatcher.PathContainerRoute.class, route);
      return ((PathPatternRouteMatcher.PathContainerRoute)route).pathContainer;
   }

   private static class PathContainerRoute implements Route {
      private final PathContainer pathContainer;

      PathContainerRoute(PathContainer pathContainer) {
         this.pathContainer = pathContainer;
      }

      public String value() {
         return this.pathContainer.value();
      }

      @Override
      public String toString() {
         return this.value();
      }
   }
}
