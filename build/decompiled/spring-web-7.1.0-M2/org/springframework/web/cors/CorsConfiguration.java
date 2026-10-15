package org.springframework.web.cors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class CorsConfiguration {
   public static final String ALL = "*";
   private static final List<String> ALL_LIST = Collections.singletonList("*");
   private static final CorsConfiguration.OriginPattern ALL_PATTERN = new CorsConfiguration.OriginPattern("*");
   private static final List<CorsConfiguration.OriginPattern> ALL_PATTERN_LIST = Collections.singletonList(ALL_PATTERN);
   private static final List<String> DEFAULT_PERMIT_ALL = Collections.singletonList("*");
   private static final List<HttpMethod> DEFAULT_METHODS = List.of(HttpMethod.GET, HttpMethod.HEAD);
   private static final List<String> DEFAULT_PERMIT_METHODS = List.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name());
   private @Nullable List<String> allowedOrigins;
   private @Nullable List<CorsConfiguration.OriginPattern> allowedOriginPatterns;
   private @Nullable List<String> allowedMethods;
   private @Nullable List<HttpMethod> resolvedMethods = DEFAULT_METHODS;
   private @Nullable List<String> allowedHeaders;
   private @Nullable List<String> exposedHeaders;
   private @Nullable Boolean allowCredentials;
   private @Nullable Boolean allowPrivateNetwork;
   private @Nullable Long maxAge;

   public CorsConfiguration() {
   }

   public CorsConfiguration(CorsConfiguration other) {
      this.allowedOrigins = other.allowedOrigins;
      this.allowedOriginPatterns = other.allowedOriginPatterns;
      this.allowedMethods = other.allowedMethods;
      this.resolvedMethods = other.resolvedMethods;
      this.allowedHeaders = other.allowedHeaders;
      this.exposedHeaders = other.exposedHeaders;
      this.allowCredentials = other.allowCredentials;
      this.allowPrivateNetwork = other.allowPrivateNetwork;
      this.maxAge = other.maxAge;
   }

   public void setAllowedOrigins(@Nullable List<String> origins) {
      if (origins == null) {
         this.allowedOrigins = null;
      } else {
         this.allowedOrigins = new ArrayList<>(origins.size());

         for (String origin : origins) {
            this.addAllowedOrigin(origin);
         }
      }
   }

   private String trimTrailingSlash(String origin) {
      return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
   }

   public @Nullable List<String> getAllowedOrigins() {
      return this.allowedOrigins;
   }

   public void addAllowedOrigin(@Nullable String origin) {
      if (origin != null) {
         if (this.allowedOrigins == null) {
            this.allowedOrigins = new ArrayList<>(4);
         } else if (this.allowedOrigins == DEFAULT_PERMIT_ALL && CollectionUtils.isEmpty(this.allowedOriginPatterns)) {
            this.setAllowedOrigins(DEFAULT_PERMIT_ALL);
         }

         parseCommaDelimitedOrigin(origin, value -> {
            value = this.trimTrailingSlash(value);
            this.allowedOrigins.add(value);
         });
      }
   }

   public CorsConfiguration setAllowedOriginPatterns(@Nullable List<String> allowedOriginPatterns) {
      if (allowedOriginPatterns == null) {
         this.allowedOriginPatterns = null;
      } else {
         this.allowedOriginPatterns = new ArrayList<>(allowedOriginPatterns.size());

         for (String patternValue : allowedOriginPatterns) {
            this.addAllowedOriginPattern(patternValue);
         }
      }

      return this;
   }

   public @Nullable List<String> getAllowedOriginPatterns() {
      return this.allowedOriginPatterns == null ? null : this.allowedOriginPatterns.stream().map(CorsConfiguration.OriginPattern::getDeclaredPattern).toList();
   }

   public void addAllowedOriginPattern(@Nullable String originPattern) {
      if (originPattern != null) {
         if (this.allowedOriginPatterns == null) {
            this.allowedOriginPatterns = new ArrayList<>(4);
         }

         parseCommaDelimitedOrigin(originPattern, value -> {
            value = this.trimTrailingSlash(value);
            this.allowedOriginPatterns.add(new CorsConfiguration.OriginPattern(value));
            if (this.allowedOrigins == DEFAULT_PERMIT_ALL) {
               this.allowedOrigins = null;
            }
         });
      }
   }

   private static void parseCommaDelimitedOrigin(String rawValue, Consumer<String> valueConsumer) {
      if (rawValue.indexOf(44) == -1) {
         valueConsumer.accept(rawValue);
      } else {
         int start = 0;
         boolean withinPortRange = false;

         for (int current = 0; current < rawValue.length(); current++) {
            switch (rawValue.charAt(current)) {
               case ',':
                  if (!withinPortRange) {
                     String originValue = rawValue.substring(start, current).trim();
                     valueConsumer.accept(originValue);
                     start = current + 1;
                  }
                  break;
               case '[':
                  withinPortRange = true;
                  break;
               case ']':
                  withinPortRange = false;
            }
         }

         if (start < rawValue.length()) {
            String originValue = rawValue.substring(start).trim();
            valueConsumer.accept(originValue);
         }
      }
   }

   public void setAllowedMethods(@Nullable List<String> allowedMethods) {
      this.allowedMethods = allowedMethods != null ? new ArrayList<>(allowedMethods) : null;
      if (!CollectionUtils.isEmpty(allowedMethods)) {
         this.resolvedMethods = new ArrayList<>(allowedMethods.size());

         for (String method : allowedMethods) {
            if ("*".equals(method)) {
               this.resolvedMethods = null;
               break;
            }

            this.resolvedMethods.add(HttpMethod.valueOf(method));
         }
      } else {
         this.resolvedMethods = DEFAULT_METHODS;
      }
   }

   public @Nullable List<String> getAllowedMethods() {
      return this.allowedMethods;
   }

   public void addAllowedMethod(HttpMethod method) {
      this.addAllowedMethod(method.name());
   }

   public void addAllowedMethod(String method) {
      if (StringUtils.hasText(method)) {
         if (this.allowedMethods == null) {
            this.allowedMethods = new ArrayList<>(4);
            this.resolvedMethods = new ArrayList<>(4);
         } else if (this.allowedMethods == DEFAULT_PERMIT_METHODS) {
            this.setAllowedMethods(DEFAULT_PERMIT_METHODS);
         }

         this.allowedMethods.add(method);
         if ("*".equals(method)) {
            this.resolvedMethods = null;
         } else if (this.resolvedMethods != null) {
            this.resolvedMethods.add(HttpMethod.valueOf(method));
         }
      }
   }

   public void setAllowedHeaders(@Nullable List<String> allowedHeaders) {
      this.allowedHeaders = allowedHeaders != null ? new ArrayList<>(allowedHeaders) : null;
   }

   public @Nullable List<String> getAllowedHeaders() {
      return this.allowedHeaders;
   }

   public void addAllowedHeader(String allowedHeader) {
      if (this.allowedHeaders == null) {
         this.allowedHeaders = new ArrayList<>(4);
      } else if (this.allowedHeaders == DEFAULT_PERMIT_ALL) {
         this.setAllowedHeaders(DEFAULT_PERMIT_ALL);
      }

      this.allowedHeaders.add(allowedHeader);
   }

   public void setExposedHeaders(@Nullable List<String> exposedHeaders) {
      this.exposedHeaders = exposedHeaders != null ? new ArrayList<>(exposedHeaders) : null;
   }

   public @Nullable List<String> getExposedHeaders() {
      return this.exposedHeaders;
   }

   public void addExposedHeader(String exposedHeader) {
      if (this.exposedHeaders == null) {
         this.exposedHeaders = new ArrayList<>(4);
      }

      this.exposedHeaders.add(exposedHeader);
   }

   public void setAllowCredentials(@Nullable Boolean allowCredentials) {
      this.allowCredentials = allowCredentials;
   }

   public @Nullable Boolean getAllowCredentials() {
      return this.allowCredentials;
   }

   public void setAllowPrivateNetwork(@Nullable Boolean allowPrivateNetwork) {
      this.allowPrivateNetwork = allowPrivateNetwork;
   }

   public @Nullable Boolean getAllowPrivateNetwork() {
      return this.allowPrivateNetwork;
   }

   public void setMaxAge(Duration maxAge) {
      this.maxAge = maxAge.getSeconds();
   }

   public void setMaxAge(@Nullable Long maxAge) {
      this.maxAge = maxAge;
   }

   public @Nullable Long getMaxAge() {
      return this.maxAge;
   }

   public CorsConfiguration applyPermitDefaultValues() {
      if (this.allowedOrigins == null && this.allowedOriginPatterns == null) {
         this.allowedOrigins = DEFAULT_PERMIT_ALL;
      }

      if (this.allowedMethods == null) {
         this.allowedMethods = DEFAULT_PERMIT_METHODS;
         this.resolvedMethods = DEFAULT_PERMIT_METHODS.stream().map(HttpMethod::valueOf).toList();
      }

      if (this.allowedHeaders == null) {
         this.allowedHeaders = DEFAULT_PERMIT_ALL;
      }

      if (this.maxAge == null) {
         this.maxAge = 1800L;
      }

      return this;
   }

   public void validateAllowCredentials() {
      if (this.allowCredentials == Boolean.TRUE && this.allowedOrigins != null && this.allowedOrigins.contains("*")) {
         throw new IllegalArgumentException(
            "When allowCredentials is true, allowedOrigins cannot contain the special value \"*\" since that cannot be set on the \"Access-Control-Allow-Origin\" response header. To allow credentials to a set of origins, list them explicitly or consider using \"allowedOriginPatterns\" instead."
         );
      }
   }

   public void validateAllowPrivateNetwork() {
      if (this.allowPrivateNetwork == Boolean.TRUE && this.allowedOrigins != null && this.allowedOrigins.contains("*")) {
         throw new IllegalArgumentException(
            "When allowPrivateNetwork is true, allowedOrigins cannot contain the special value \"*\" as it is not recommended from a security perspective. To allow private network access to a set of origins, list them explicitly or consider using \"allowedOriginPatterns\" instead."
         );
      }
   }

   public CorsConfiguration combine(@Nullable CorsConfiguration other) {
      if (other == null) {
         return this;
      }

      CorsConfiguration config = new CorsConfiguration(this);
      List<String> origins = this.combine(this.getAllowedOrigins(), other.getAllowedOrigins());
      List<CorsConfiguration.OriginPattern> patterns = this.combinePatterns(this.allowedOriginPatterns, other.allowedOriginPatterns);
      config.allowedOrigins = origins == DEFAULT_PERMIT_ALL && !CollectionUtils.isEmpty(patterns) ? null : origins;
      config.allowedOriginPatterns = patterns;
      config.setAllowedMethods(this.combine(this.getAllowedMethods(), other.getAllowedMethods()));
      config.setAllowedHeaders(this.combine(this.getAllowedHeaders(), other.getAllowedHeaders()));
      config.setExposedHeaders(this.combine(this.getExposedHeaders(), other.getExposedHeaders()));
      Boolean allowCredentials = other.getAllowCredentials();
      if (allowCredentials != null) {
         config.setAllowCredentials(allowCredentials);
      }

      Boolean allowPrivateNetwork = other.getAllowPrivateNetwork();
      if (allowPrivateNetwork != null) {
         config.setAllowPrivateNetwork(allowPrivateNetwork);
      }

      Long maxAge = other.getMaxAge();
      if (maxAge != null) {
         config.setMaxAge(maxAge);
      }

      return config;
   }

   private List<String> combine(@Nullable List<String> source, @Nullable List<String> other) {
      if (other == null) {
         return source != null ? source : Collections.emptyList();
      } else if (source == null) {
         return other;
      } else if (source == DEFAULT_PERMIT_ALL || source == DEFAULT_PERMIT_METHODS) {
         return other;
      } else if (other == DEFAULT_PERMIT_ALL || other == DEFAULT_PERMIT_METHODS) {
         return source;
      } else if (!source.contains("*") && !other.contains("*")) {
         Set<String> combined = CollectionUtils.newLinkedHashSet(source.size() + other.size());
         combined.addAll(source);
         combined.addAll(other);
         return new ArrayList<>(combined);
      } else {
         return ALL_LIST;
      }
   }

   private List<CorsConfiguration.OriginPattern> combinePatterns(
      @Nullable List<CorsConfiguration.OriginPattern> source, @Nullable List<CorsConfiguration.OriginPattern> other
   ) {
      if (other == null) {
         return source != null ? source : Collections.emptyList();
      } else if (source == null) {
         return other;
      } else if (!source.contains(ALL_PATTERN) && !other.contains(ALL_PATTERN)) {
         Set<CorsConfiguration.OriginPattern> combined = CollectionUtils.newLinkedHashSet(source.size() + other.size());
         combined.addAll(source);
         combined.addAll(other);
         return new ArrayList<>(combined);
      } else {
         return ALL_PATTERN_LIST;
      }
   }

   public @Nullable String checkOrigin(@Nullable String origin) {
      if (!StringUtils.hasText(origin)) {
         return null;
      }

      String originToCheck = this.trimTrailingSlash(origin);
      if (!ObjectUtils.isEmpty(this.allowedOrigins)) {
         if (this.allowedOrigins.contains("*")) {
            this.validateAllowCredentials();
            this.validateAllowPrivateNetwork();
            return "*";
         }

         for (String allowedOrigin : this.allowedOrigins) {
            if (originToCheck.equalsIgnoreCase(allowedOrigin)) {
               return origin;
            }
         }
      }

      if (!ObjectUtils.isEmpty(this.allowedOriginPatterns)) {
         for (CorsConfiguration.OriginPattern p : this.allowedOriginPatterns) {
            if (p.getDeclaredPattern().equals("*") || p.getPattern().matcher(originToCheck).matches()) {
               return origin;
            }
         }
      }

      return null;
   }

   public @Nullable List<HttpMethod> checkHttpMethod(@Nullable HttpMethod requestMethod) {
      if (requestMethod == null) {
         return null;
      } else if (this.resolvedMethods == null) {
         return Collections.singletonList(requestMethod);
      } else {
         return this.resolvedMethods.contains(requestMethod) ? this.resolvedMethods : null;
      }
   }

   public @Nullable List<String> checkHeaders(@Nullable List<String> requestHeaders) {
      if (requestHeaders == null) {
         return null;
      }

      if (requestHeaders.isEmpty()) {
         return Collections.emptyList();
      }

      if (ObjectUtils.isEmpty(this.allowedHeaders)) {
         return null;
      }

      boolean allowAnyHeader = this.allowedHeaders.contains("*");
      int maxResultSize = allowAnyHeader ? requestHeaders.size() : Math.min(requestHeaders.size(), this.allowedHeaders.size());
      List<String> result = new ArrayList<>(maxResultSize);

      for (String requestHeader : requestHeaders) {
         if (StringUtils.hasText(requestHeader)) {
            requestHeader = requestHeader.trim();
            if (allowAnyHeader) {
               result.add(requestHeader);
            } else {
               for (String allowedHeader : this.allowedHeaders) {
                  if (requestHeader.equalsIgnoreCase(allowedHeader)) {
                     result.add(requestHeader);
                     break;
                  }
               }
            }
         }
      }

      return result.isEmpty() ? null : result;
   }

   private static class OriginPattern {
      private static final Pattern PORTS_PATTERN = Pattern.compile("(.*):\\[(\\*|\\d+(,\\d+)*)]");
      private final String declaredPattern;
      private final Pattern pattern;

      OriginPattern(String declaredPattern) {
         this.declaredPattern = declaredPattern;
         this.pattern = initPattern(declaredPattern);
      }

      private static Pattern initPattern(String patternValue) {
         String portList = null;
         Matcher matcher = PORTS_PATTERN.matcher(patternValue);
         if (matcher.matches()) {
            patternValue = matcher.group(1);
            portList = matcher.group(2);
         }

         patternValue = "\\Q" + patternValue + "\\E";
         patternValue = patternValue.replace("*", "\\E.*\\Q");
         if (portList != null) {
            patternValue = patternValue + (portList.equals("*") ? "(:\\d+)?" : ":(" + portList.replace(',', '|') + ")");
         }

         return Pattern.compile(patternValue);
      }

      public String getDeclaredPattern() {
         return this.declaredPattern;
      }

      public Pattern getPattern() {
         return this.pattern;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else {
            return other != null && this.getClass().equals(other.getClass())
               ? ObjectUtils.nullSafeEquals(this.declaredPattern, ((CorsConfiguration.OriginPattern)other).declaredPattern)
               : false;
         }
      }

      @Override
      public int hashCode() {
         return this.declaredPattern.hashCode();
      }

      @Override
      public String toString() {
         return this.declaredPattern;
      }
   }
}
