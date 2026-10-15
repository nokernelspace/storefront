package org.springframework.util;

import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface RouteMatcher {
   RouteMatcher.Route parseRoute(String routeValue);

   boolean isPattern(String route);

   String combine(String pattern1, String pattern2);

   boolean match(String pattern, RouteMatcher.Route route);

   @Nullable Map<String, String> matchAndExtract(String pattern, RouteMatcher.Route route);

   Comparator<String> getPatternComparator(RouteMatcher.Route route);

   interface Route {
      String value();
   }
}
