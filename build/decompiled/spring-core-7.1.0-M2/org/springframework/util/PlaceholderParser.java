package org.springframework.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

final class PlaceholderParser {
   private static final Log logger = LogFactory.getLog(PlaceholderParser.class);
   private static final Map<String, String> wellKnownSimplePrefixes = Map.of("}", "{", "]", "[", ")", "(");
   private final String prefix;
   private final String suffix;
   private final String simplePrefix;
   private final @Nullable String separator;
   private final boolean ignoreUnresolvablePlaceholders;
   private final @Nullable Character escape;

   PlaceholderParser(String prefix, String suffix, @Nullable String separator, @Nullable Character escape, boolean ignoreUnresolvablePlaceholders) {
      this.prefix = prefix;
      this.suffix = suffix;
      String simplePrefixForSuffix = wellKnownSimplePrefixes.get(this.suffix);
      if (simplePrefixForSuffix != null && this.prefix.endsWith(simplePrefixForSuffix)) {
         this.simplePrefix = simplePrefixForSuffix;
      } else {
         this.simplePrefix = this.prefix;
      }

      this.separator = separator;
      this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
      this.escape = escape;
   }

   public String replacePlaceholders(String value, PropertyPlaceholderHelper.PlaceholderResolver placeholderResolver) {
      List<PlaceholderParser.Part> parts = this.parse(value, false);
      if (parts == null) {
         return value;
      }

      PlaceholderParser.ParsedValue parsedValue = new PlaceholderParser.ParsedValue(value, parts);
      PlaceholderParser.PartResolutionContext resolutionContext = new PlaceholderParser.PartResolutionContext(
         placeholderResolver, this.prefix, this.suffix, this.ignoreUnresolvablePlaceholders, candidate -> this.parse(candidate, false)
      );
      return parsedValue.resolve(resolutionContext);
   }

   private @Nullable List<PlaceholderParser.Part> parse(String value, boolean inPlaceholder) {
      int startIndex = this.nextStartPrefix(value, 0);
      if (startIndex == -1) {
         return null;
      }

      List<PlaceholderParser.Part> parts = new ArrayList<>(4);
      int position = 0;

      while (startIndex != -1) {
         int endIndex = this.nextValidEndPrefix(value, startIndex);
         if (endIndex == -1) {
            addText(value, position, startIndex + this.prefix.length(), parts);
            position = startIndex + this.prefix.length();
            startIndex = this.nextStartPrefix(value, position);
         } else if (this.isEscaped(value, startIndex)) {
            addText(value, position, startIndex - 1, parts);
            addText(value, startIndex, startIndex + this.prefix.length(), parts);
            position = startIndex + this.prefix.length();
            startIndex = this.nextStartPrefix(value, position);
         } else {
            addText(value, position, startIndex, parts);
            String placeholder = value.substring(startIndex + this.prefix.length(), endIndex);
            List<PlaceholderParser.Part> placeholderParts = this.parse(placeholder, true);
            if (placeholderParts == null) {
               parts.add(this.createSimplePlaceholderPart(placeholder));
            } else {
               parts.addAll(placeholderParts);
            }

            startIndex = this.nextStartPrefix(value, endIndex + this.suffix.length());
            position = endIndex + this.suffix.length();
         }
      }

      addText(value, position, value.length(), parts);
      return inPlaceholder ? List.of(this.createNestedPlaceholderPart(value, parts)) : parts;
   }

   private PlaceholderParser.SimplePlaceholderPart createSimplePlaceholderPart(String text) {
      PlaceholderParser.ParsedSection section = this.parseSection(text);
      return new PlaceholderParser.SimplePlaceholderPart(text, section.key(), section.fallback());
   }

   private PlaceholderParser.NestedPlaceholderPart createNestedPlaceholderPart(String text, List<PlaceholderParser.Part> parts) {
      if (this.separator == null) {
         return new PlaceholderParser.NestedPlaceholderPart(text, parts, null);
      }

      List<PlaceholderParser.Part> keyParts = new ArrayList<>();
      List<PlaceholderParser.Part> defaultParts = new ArrayList<>();

      for (int i = 0; i < parts.size(); i++) {
         PlaceholderParser.Part part = parts.get(i);
         if (!(part instanceof PlaceholderParser.TextPart)) {
            keyParts.add(part);
         } else {
            String candidate = part.text();
            PlaceholderParser.ParsedSection section = this.parseSection(candidate);
            keyParts.add(new PlaceholderParser.TextPart(section.key()));
            if (section.fallback() != null) {
               defaultParts.add(new PlaceholderParser.TextPart(section.fallback()));
               defaultParts.addAll(parts.subList(i + 1, parts.size()));
               return new PlaceholderParser.NestedPlaceholderPart(text, keyParts, defaultParts);
            }
         }
      }

      return new PlaceholderParser.NestedPlaceholderPart(text, keyParts, null);
   }

   private PlaceholderParser.ParsedSection parseSection(String value) {
      if (this.separator != null && value.contains(this.separator)) {
         int position = 0;
         int index = value.indexOf(this.separator, position);
         StringBuilder buffer = new StringBuilder();

         while (index != -1) {
            if (!this.isEscaped(value, index)) {
               buffer.append(value, position, index);
               String key = buffer.toString();
               String fallback = value.substring(index + this.separator.length());
               return new PlaceholderParser.ParsedSection(key, fallback);
            }

            buffer.append(value, position, index - 1);
            buffer.append(value, index, index + this.separator.length());
            position = index + this.separator.length();
            index = value.indexOf(this.separator, position);
         }

         buffer.append(value, position, value.length());
         return new PlaceholderParser.ParsedSection(buffer.toString(), null);
      } else {
         return new PlaceholderParser.ParsedSection(value, null);
      }
   }

   private int nextStartPrefix(String value, int index) {
      return value.indexOf(this.prefix, index);
   }

   private int nextValidEndPrefix(String value, int startIndex) {
      int index = startIndex + this.prefix.length();
      int withinNestedPlaceholder = 0;

      while (index < value.length()) {
         if (StringUtils.substringMatch(value, index, this.suffix)) {
            if (withinNestedPlaceholder <= 0) {
               return index;
            }

            withinNestedPlaceholder--;
            index += this.suffix.length();
         } else if (StringUtils.substringMatch(value, index, this.simplePrefix)) {
            withinNestedPlaceholder++;
            index += this.simplePrefix.length();
         } else {
            index++;
         }
      }

      return -1;
   }

   private boolean isEscaped(String value, int index) {
      return this.escape != null && index > 0 && value.charAt(index - 1) == this.escape;
   }

   private static void addText(String value, int start, int end, List<PlaceholderParser.Part> parts) {
      if (start < end) {
         String text = value.substring(start, end);
         if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof PlaceholderParser.TextPart textPart) {
            parts.set(parts.size() - 1, new PlaceholderParser.TextPart(textPart.text() + text));
         } else {
            parts.add(new PlaceholderParser.TextPart(text));
         }
      }
   }

   private abstract static class AbstractPart implements PlaceholderParser.Part {
      final String text;

      protected AbstractPart(String text) {
         this.text = text;
      }

      @Override
      public String text() {
         return this.text;
      }

      protected @Nullable String resolveRecursively(PlaceholderParser.PartResolutionContext resolutionContext, String key) {
         String resolvedValue = resolutionContext.resolvePlaceholder(key);
         if (resolvedValue == null) {
            return null;
         }

         List<PlaceholderParser.Part> nestedParts = resolutionContext.parse(resolvedValue);
         if (nestedParts == null) {
            return resolvedValue;
         }

         resolutionContext.flagPlaceholderAsVisited(key);
         String value = new PlaceholderParser.ParsedValue(resolvedValue, nestedParts).resolve(resolutionContext);
         resolutionContext.removePlaceholder(key);
         return value;
      }
   }

   private static class NestedPlaceholderPart extends PlaceholderParser.AbstractPart {
      private final List<PlaceholderParser.Part> keyParts;
      private final @Nullable List<PlaceholderParser.Part> defaultParts;

      NestedPlaceholderPart(String text, List<PlaceholderParser.Part> keyParts, @Nullable List<PlaceholderParser.Part> defaultParts) {
         super(text);
         this.keyParts = keyParts;
         this.defaultParts = defaultParts;
      }

      @Override
      public String resolve(PlaceholderParser.PartResolutionContext resolutionContext) {
         String resolvedKey = PlaceholderParser.Part.resolveAll(this.keyParts, resolutionContext);
         String value = this.resolveRecursively(resolutionContext, resolvedKey);
         if (value != null) {
            return value;
         } else {
            return this.defaultParts != null
               ? PlaceholderParser.Part.resolveAll(this.defaultParts, resolutionContext)
               : resolutionContext.handleUnresolvablePlaceholder(resolvedKey, this.text);
         }
      }
   }

   private record ParsedSection(String key, @Nullable String fallback) {
   }

   private record ParsedValue(String text, List<PlaceholderParser.Part> parts) {
      public String resolve(PlaceholderParser.PartResolutionContext resolutionContext) {
         try {
            return PlaceholderParser.Part.resolveAll(this.parts, resolutionContext);
         } catch (PlaceholderResolutionException ex) {
            throw ex.withValue(this.text);
         }
      }
   }

   private interface Part {
      String resolve(PlaceholderParser.PartResolutionContext resolutionContext);

      String text();

      static String resolveAll(Iterable<PlaceholderParser.Part> parts, PlaceholderParser.PartResolutionContext resolutionContext) {
         StringBuilder sb = new StringBuilder();

         for (PlaceholderParser.Part part : parts) {
            sb.append(part.resolve(resolutionContext));
         }

         return sb.toString();
      }
   }

   private static class PartResolutionContext implements PropertyPlaceholderHelper.PlaceholderResolver {
      private final String prefix;
      private final String suffix;
      private final boolean ignoreUnresolvablePlaceholders;
      private final Function<String, @Nullable List<PlaceholderParser.Part>> parser;
      private final PropertyPlaceholderHelper.PlaceholderResolver resolver;
      private @Nullable Set<String> visitedPlaceholders;

      PartResolutionContext(
         PropertyPlaceholderHelper.PlaceholderResolver resolver,
         String prefix,
         String suffix,
         boolean ignoreUnresolvablePlaceholders,
         Function<String, @Nullable List<PlaceholderParser.Part>> parser
      ) {
         this.prefix = prefix;
         this.suffix = suffix;
         this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
         this.parser = parser;
         this.resolver = resolver;
      }

      @Override
      public @Nullable String resolvePlaceholder(String placeholderName) {
         String value = this.resolver.resolvePlaceholder(placeholderName);
         if (value != null && PlaceholderParser.logger.isTraceEnabled()) {
            PlaceholderParser.logger.trace("Resolved placeholder '" + placeholderName + "'");
         }

         return value;
      }

      public String handleUnresolvablePlaceholder(String key, String text) {
         if (this.ignoreUnresolvablePlaceholders) {
            return this.toPlaceholderText(key);
         }

         String originalValue = !key.equals(text) ? this.toPlaceholderText(text) : null;
         throw new PlaceholderResolutionException("Could not resolve placeholder '%s'".formatted(key), key, originalValue);
      }

      private String toPlaceholderText(String text) {
         return this.prefix + text + this.suffix;
      }

      public @Nullable List<PlaceholderParser.Part> parse(String text) {
         return this.parser.apply(text);
      }

      public void flagPlaceholderAsVisited(String placeholder) {
         if (this.visitedPlaceholders == null) {
            this.visitedPlaceholders = new HashSet<>(4);
         }

         if (!this.visitedPlaceholders.add(placeholder)) {
            throw new PlaceholderResolutionException("Circular placeholder reference '%s'".formatted(placeholder), placeholder, null);
         }
      }

      public void removePlaceholder(String placeholder) {
         if (this.visitedPlaceholders != null) {
            this.visitedPlaceholders.remove(placeholder);
         }
      }
   }

   private static class SimplePlaceholderPart extends PlaceholderParser.AbstractPart {
      private final String key;
      private final @Nullable String fallback;

      public SimplePlaceholderPart(String text, String key, @Nullable String fallback) {
         super(text);
         this.key = key;
         this.fallback = fallback;
      }

      @Override
      public String resolve(PlaceholderParser.PartResolutionContext resolutionContext) {
         String value = this.resolveRecursively(resolutionContext);
         if (value != null) {
            return value;
         } else {
            return this.fallback != null ? this.fallback : resolutionContext.handleUnresolvablePlaceholder(this.key, this.text);
         }
      }

      private @Nullable String resolveRecursively(PlaceholderParser.PartResolutionContext resolutionContext) {
         if (!this.text.equals(this.key)) {
            String value = this.resolveRecursively(resolutionContext, this.text);
            if (value != null) {
               return value;
            }
         }

         return this.resolveRecursively(resolutionContext, this.key);
      }
   }

   private static class TextPart extends PlaceholderParser.AbstractPart {
      public TextPart(String text) {
         super(text);
      }

      @Override
      public String resolve(PlaceholderParser.PartResolutionContext resolutionContext) {
         return this.text;
      }
   }
}
