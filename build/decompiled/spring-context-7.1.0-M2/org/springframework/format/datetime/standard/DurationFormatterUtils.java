package org.springframework.format.datetime.standard;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class DurationFormatterUtils {
   private static final Pattern ISO_8601_PATTERN = Pattern.compile("^[+-]?[pP].*$");
   private static final Pattern SIMPLE_PATTERN = Pattern.compile("^([+-]?\\d+)([a-zA-Z]{0,2})$");
   private static final Pattern COMPOSITE_PATTERN = Pattern.compile(
      "^([+-]?)\\(?\\s?(\\d+d)?\\s?(\\d+h)?\\s?(\\d+m)?\\s?(\\d+s)?\\s?(\\d+ms)?\\s?(\\d+us)?\\s?(\\d+ns)?\\)?$"
   );

   public static String print(Duration value, DurationFormat.Style style) {
      return print(value, style, null);
   }

   public static String print(Duration value, DurationFormat.Style style, DurationFormat.@Nullable Unit unit) {
      return switch (style) {
         case ISO8601 -> value.toString();
         case SIMPLE -> printSimple(value, unit);
         case COMPOSITE -> printComposite(value);
      };
   }

   public static Duration parse(String value, DurationFormat.Style style) {
      return parse(value, style, null);
   }

   public static Duration parse(String value, DurationFormat.Style style, DurationFormat.@Nullable Unit unit) {
      Assert.hasText(value, () -> "Value must not be empty");

      return switch (style) {
         case ISO8601 -> parseIso8601(value);
         case SIMPLE -> parseSimple(value, unit);
         case COMPOSITE -> parseComposite(value);
      };
   }

   public static DurationFormat.Style detect(String value) {
      Assert.notNull(value, "Value must not be null");
      if (ISO_8601_PATTERN.matcher(value).matches()) {
         return DurationFormat.Style.ISO8601;
      } else if (SIMPLE_PATTERN.matcher(value).matches()) {
         return DurationFormat.Style.SIMPLE;
      } else if (COMPOSITE_PATTERN.matcher(value).matches()) {
         return DurationFormat.Style.COMPOSITE;
      } else {
         throw new IllegalArgumentException("'" + value + "' is not a valid duration, cannot detect any known style");
      }
   }

   public static Duration detectAndParse(String value) {
      return detectAndParse(value, null);
   }

   public static Duration detectAndParse(String value, DurationFormat.@Nullable Unit unit) {
      return parse(value, detect(value), unit);
   }

   private static Duration parseIso8601(String value) {
      try {
         return Duration.parse(value);
      } catch (Exception ex) {
         throw new IllegalArgumentException("'" + value + "' is not a valid ISO-8601 duration", ex);
      }
   }

   private static String printSimple(Duration duration, DurationFormat.@Nullable Unit unit) {
      unit = unit == null ? DurationFormat.Unit.MILLIS : unit;
      return unit.print(duration);
   }

   private static Duration parseSimple(String text, DurationFormat.@Nullable Unit fallbackUnit) {
      try {
         Matcher matcher = SIMPLE_PATTERN.matcher(text);
         Assert.state(matcher.matches(), "Does not match simple duration pattern");
         String suffix = matcher.group(2);
         DurationFormat.Unit parsingUnit = fallbackUnit == null ? DurationFormat.Unit.MILLIS : fallbackUnit;
         if (StringUtils.hasLength(suffix)) {
            parsingUnit = DurationFormat.Unit.fromSuffix(suffix);
         }

         return parsingUnit.parse(matcher.group(1));
      } catch (Exception ex) {
         throw new IllegalArgumentException("'" + text + "' is not a valid simple duration", ex);
      }
   }

   private static String printComposite(Duration duration) {
      if (duration.isZero()) {
         return DurationFormat.Unit.SECONDS.print(duration);
      }

      StringBuilder result = new StringBuilder();
      if (duration.isNegative()) {
         result.append('-');
         duration = duration.negated();
      }

      long days = duration.toDaysPart();
      if (days != 0L) {
         result.append(days).append(DurationFormat.Unit.DAYS.asSuffix());
      }

      int hours = duration.toHoursPart();
      if (hours != 0) {
         result.append(hours).append(DurationFormat.Unit.HOURS.asSuffix());
      }

      int minutes = duration.toMinutesPart();
      if (minutes != 0) {
         result.append(minutes).append(DurationFormat.Unit.MINUTES.asSuffix());
      }

      int seconds = duration.toSecondsPart();
      if (seconds != 0) {
         result.append(seconds).append(DurationFormat.Unit.SECONDS.asSuffix());
      }

      int millis = duration.toMillisPart();
      if (millis != 0) {
         result.append(millis).append(DurationFormat.Unit.MILLIS.asSuffix());
      }

      long nanos = duration.toNanosPart() - Duration.ofMillis(millis).toNanos();
      if (nanos != 0L) {
         long micros = nanos / 1000L;
         long remainder = nanos - micros * 1000L;
         if (micros > 0L) {
            result.append(micros).append(DurationFormat.Unit.MICROS.asSuffix());
         }

         if (remainder > 0L) {
            result.append(remainder).append(DurationFormat.Unit.NANOS.asSuffix());
         }
      }

      return result.toString();
   }

   private static Duration parseComposite(String text) {
      try {
         Matcher matcher = COMPOSITE_PATTERN.matcher(text);
         Assert.state(matcher.matches() && matcher.groupCount() > 1, "Does not match composite duration pattern");
         String sign = matcher.group(1);
         boolean negative = sign != null && sign.equals("-");
         Duration result = Duration.ZERO;
         DurationFormat.Unit[] units = DurationFormat.Unit.values();

         for (int i = 2; i < matcher.groupCount() + 1; i++) {
            String segment = matcher.group(i);
            if (StringUtils.hasText(segment)) {
               DurationFormat.Unit unit = units[units.length - i + 1];
               result = result.plus(unit.parse(segment.replace(unit.asSuffix(), "")));
            }
         }

         return negative ? result.negated() : result;
      } catch (Exception ex) {
         throw new IllegalArgumentException("'" + text + "' is not a valid composite duration", ex);
      }
   }
}
