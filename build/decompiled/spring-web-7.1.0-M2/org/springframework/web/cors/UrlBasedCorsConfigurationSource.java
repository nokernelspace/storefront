package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class UrlBasedCorsConfigurationSource implements CorsConfigurationSource {
   private static final PathMatcher defaultPathMatcher = new AntPathMatcher();
   private final PathPatternParser patternParser;
   private UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance;
   private PathMatcher pathMatcher = defaultPathMatcher;
   private boolean allowInitLookupPath = true;
   private final Map<PathPattern, CorsConfiguration> corsConfigurations = new LinkedHashMap<>();

   public UrlBasedCorsConfigurationSource() {
      this(PathPatternParser.defaultInstance);
   }

   public UrlBasedCorsConfigurationSource(PathPatternParser parser) {
      Assert.notNull(parser, "PathPatternParser must not be null");
      this.patternParser = parser;
   }

   @Deprecated(since = "5.3", forRemoval = true)
   public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
      this.initUrlPathHelper();
      this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
   }

   @Deprecated(since = "5.3", forRemoval = true)
   public void setUrlDecode(boolean urlDecode) {
      this.initUrlPathHelper();
      this.urlPathHelper.setUrlDecode(urlDecode);
   }

   @Deprecated(since = "5.3", forRemoval = true)
   public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
      this.initUrlPathHelper();
      this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
   }

   private void initUrlPathHelper() {
      if (this.urlPathHelper == UrlPathHelper.defaultInstance) {
         this.urlPathHelper = new UrlPathHelper();
      }
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
      Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
      this.urlPathHelper = urlPathHelper;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public void setAllowInitLookupPath(boolean allowInitLookupPath) {
      this.allowInitLookupPath = allowInitLookupPath;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public void setPathMatcher(PathMatcher pathMatcher) {
      this.pathMatcher = pathMatcher;
   }

   public void setCorsConfigurations(@Nullable Map<String, CorsConfiguration> corsConfigurations) {
      this.corsConfigurations.clear();
      if (corsConfigurations != null) {
         corsConfigurations.forEach(this::registerCorsConfiguration);
      }
   }

   public void registerCorsConfiguration(String pattern, CorsConfiguration config) {
      this.corsConfigurations.put(this.patternParser.parse(pattern), config);
   }

   public Map<String, CorsConfiguration> getCorsConfigurations() {
      Map<String, CorsConfiguration> result = CollectionUtils.newHashMap(this.corsConfigurations.size());
      this.corsConfigurations.forEach((pattern, config) -> result.put(pattern.getPatternString(), config));
      return Collections.unmodifiableMap(result);
   }

   @Override
   public @Nullable CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
      Object path = this.resolvePath(request);
      boolean isPathContainer = path instanceof PathContainer;

      for (Entry<PathPattern, CorsConfiguration> entry : this.corsConfigurations.entrySet()) {
         if (this.match(path, isPathContainer, entry.getKey())) {
            return entry.getValue();
         }
      }

      return null;
   }

   private Object resolvePath(HttpServletRequest request) {
      if (this.allowInitLookupPath && !ServletRequestPathUtils.hasCachedPath(request)) {
         return this.urlPathHelper.getLookupPathForRequest(request);
      }

      Object lookupPath = ServletRequestPathUtils.getCachedPath(request);
      if (this.pathMatcher != defaultPathMatcher) {
         lookupPath = lookupPath.toString();
      }

      return lookupPath;
   }

   private boolean match(Object path, boolean isPathContainer, PathPattern pattern) {
      return isPathContainer ? pattern.matches((PathContainer)path) : this.pathMatcher.match(pattern.getPatternString(), (String)path);
   }
}
