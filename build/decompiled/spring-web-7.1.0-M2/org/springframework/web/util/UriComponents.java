package org.springframework.web.util;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

public abstract class UriComponents implements Serializable {
   private static final Pattern NAMES_PATTERN = Pattern.compile("\\{([^/]+?)\\}");
   private final @Nullable String scheme;
   private final @Nullable String fragment;

   protected UriComponents(@Nullable String scheme, @Nullable String fragment) {
      this.scheme = scheme;
      this.fragment = fragment;
   }

   public final @Nullable String getScheme() {
      return this.scheme;
   }

   public final @Nullable String getFragment() {
      return this.fragment;
   }

   public abstract @Nullable String getSchemeSpecificPart();

   public abstract @Nullable String getUserInfo();

   public abstract @Nullable String getHost();

   public abstract int getPort();

   public abstract @Nullable String getPath();

   public abstract List<String> getPathSegments();

   public abstract @Nullable String getQuery();

   public abstract MultiValueMap<String, String> getQueryParams();

   public final UriComponents encode() {
      return this.encode(StandardCharsets.UTF_8);
   }

   public abstract UriComponents encode(Charset charset);

   public final UriComponents expand(Map<String, ? extends @Nullable Object> uriVariables) {
      Assert.notNull(uriVariables, "'uriVariables' must not be null");
      return this.expandInternal(new UriComponents.MapTemplateVariables(uriVariables));
   }

   public final UriComponents expand(@Nullable Object... uriVariableValues) {
      Assert.notNull(uriVariableValues, "'uriVariableValues' must not be null");
      return this.expandInternal(new UriComponents.VarArgsTemplateVariables(uriVariableValues));
   }

   public final UriComponents expand(UriComponents.UriTemplateVariables uriVariables) {
      Assert.notNull(uriVariables, "'uriVariables' must not be null");
      return this.expandInternal(uriVariables);
   }

   abstract UriComponents expandInternal(UriComponents.UriTemplateVariables uriVariables);

   public abstract UriComponents normalize();

   public abstract String toUriString();

   public abstract URI toUri();

   @Override
   public final String toString() {
      return this.toUriString();
   }

   protected abstract void copyToUriComponentsBuilder(UriComponentsBuilder builder);

   static @Nullable String expandUriComponent(@Nullable String source, UriComponents.UriTemplateVariables uriVariables) {
      return expandUriComponent(source, uriVariables, null);
   }

   static @Nullable String expandUriComponent(@Nullable String source, UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
      if (source == null) {
         return null;
      }

      if (source.indexOf(123) == -1) {
         return source;
      }

      if (source.indexOf(58) != -1) {
         source = sanitizeSource(source);
      }

      Matcher matcher = NAMES_PATTERN.matcher(source);
      StringBuilder sb = new StringBuilder();

      while (matcher.find()) {
         String match = matcher.group(1);
         String varName = getVariableName(match);
         Object varValue = uriVariables.getValue(varName);
         if (!UriComponents.UriTemplateVariables.SKIP_VALUE.equals(varValue)) {
            String formatted = getVariableValueAsString(varValue);
            formatted = encoder != null ? encoder.apply(formatted) : Matcher.quoteReplacement(formatted);
            matcher.appendReplacement(sb, formatted);
         }
      }

      matcher.appendTail(sb);
      return sb.toString();
   }

   private static String sanitizeSource(String source) {
      int level = 0;
      int lastCharIndex = 0;
      char[] chars = new char[source.length()];

      for (int i = 0; i < source.length(); i++) {
         char c = source.charAt(i);
         if (c == '{') {
            level++;
         }

         if (c == '}') {
            level--;
         }

         if (level <= 1 && (level != 1 || c != '}')) {
            chars[lastCharIndex++] = c;
         }
      }

      return new String(chars, 0, lastCharIndex);
   }

   private static String getVariableName(String match) {
      int colonIdx = match.indexOf(58);
      return colonIdx != -1 ? match.substring(0, colonIdx) : match;
   }

   private static String getVariableValueAsString(@Nullable Object variableValue) {
      return variableValue != null ? variableValue.toString() : "";
   }

   private static class MapTemplateVariables implements UriComponents.UriTemplateVariables {
      private final Map<String, ? extends @Nullable Object> uriVariables;

      public MapTemplateVariables(Map<String, ? extends @Nullable Object> uriVariables) {
         this.uriVariables = uriVariables;
      }

      @Override
      public @Nullable Object getValue(@Nullable String name) {
         if (!this.uriVariables.containsKey(name)) {
            throw new IllegalArgumentException("Map has no value for '" + name + "'");
         } else {
            return this.uriVariables.get(name);
         }
      }
   }

   public interface UriTemplateVariables {
      Object SKIP_VALUE = UriComponents.UriTemplateVariables.class;

      @Nullable Object getValue(@Nullable String name);
   }

   private static class VarArgsTemplateVariables implements UriComponents.UriTemplateVariables {
      private final Iterator<@Nullable Object> valueIterator;

      public VarArgsTemplateVariables(@Nullable Object... uriVariableValues) {
         this.valueIterator = Arrays.asList(uriVariableValues).iterator();
      }

      @Override
      public @Nullable Object getValue(@Nullable String name) {
         if (!this.valueIterator.hasNext()) {
            throw new IllegalArgumentException("Not enough variable values available to expand '" + name + "'");
         } else {
            return this.valueIterator.next();
         }
      }
   }
}
