package org.springframework.format.datetime;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.format.Formatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class DateFormatter implements Formatter<Date> {
   private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
   private static final Map<DateTimeFormat.ISO, String> ISO_PATTERNS;
   private static final Map<DateTimeFormat.ISO, String> ISO_FALLBACK_PATTERNS;
   private @Nullable Object source;
   private @Nullable String pattern;
   private String @Nullable [] fallbackPatterns;
   private int style = 2;
   private @Nullable String stylePattern;
   private DateTimeFormat.@Nullable ISO iso;
   private @Nullable TimeZone timeZone;
   private boolean lenient = false;

   public DateFormatter() {
   }

   public DateFormatter(String pattern) {
      this.pattern = pattern;
   }

   public void setSource(Object source) {
      this.source = source;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   public void setFallbackPatterns(String... fallbackPatterns) {
      this.fallbackPatterns = fallbackPatterns;
   }

   public void setIso(DateTimeFormat.ISO iso) {
      this.iso = iso;
   }

   public void setStyle(int style) {
      this.style = style;
   }

   public void setStylePattern(String stylePattern) {
      this.stylePattern = stylePattern;
   }

   public void setTimeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
   }

   public void setLenient(boolean lenient) {
      this.lenient = lenient;
   }

   public String print(Date date, Locale locale) {
      return this.getDateFormat(locale).format(date);
   }

   public Date parse(String text, Locale locale) throws ParseException {
      try {
         return this.getDateFormat(locale).parse(text);
      } catch (ParseException ex) {
         Set<String> fallbackPatterns = new LinkedHashSet<>();
         String isoPattern = ISO_FALLBACK_PATTERNS.get(this.iso);
         if (isoPattern != null) {
            fallbackPatterns.add(isoPattern);
         }

         if (!ObjectUtils.isEmpty(this.fallbackPatterns)) {
            Collections.addAll(fallbackPatterns, this.fallbackPatterns);
         }

         if (!fallbackPatterns.isEmpty()) {
            for (String pattern : fallbackPatterns) {
               try {
                  DateFormat dateFormat = this.configureDateFormat(new SimpleDateFormat(pattern, locale));
                  if (this.iso != null && this.iso != DateTimeFormat.ISO.NONE) {
                     dateFormat.setTimeZone(UTC);
                  }

                  return dateFormat.parse(text);
               } catch (ParseException var9) {
               }
            }
         }

         if (this.source != null) {
            ParseException parseException = new ParseException(
               String.format("Unable to parse date time value \"%s\" using configuration from %s", text, this.source), ex.getErrorOffset()
            );
            parseException.initCause(ex);
            throw parseException;
         } else {
            throw ex;
         }
      }
   }

   protected DateFormat getDateFormat(Locale locale) {
      return this.configureDateFormat(this.createDateFormat(locale));
   }

   private DateFormat configureDateFormat(DateFormat dateFormat) {
      if (this.timeZone != null) {
         dateFormat.setTimeZone(this.timeZone);
      }

      dateFormat.setLenient(this.lenient);
      return dateFormat;
   }

   private DateFormat createDateFormat(Locale locale) {
      if (StringUtils.hasLength(this.pattern)) {
         return new SimpleDateFormat(this.pattern, locale);
      }

      if (this.iso != null && this.iso != DateTimeFormat.ISO.NONE) {
         String pattern = ISO_PATTERNS.get(this.iso);
         if (pattern == null) {
            throw new IllegalStateException("Unsupported ISO format " + this.iso);
         }

         SimpleDateFormat format = new SimpleDateFormat(pattern);
         format.setTimeZone(UTC);
         return format;
      } else if (StringUtils.hasLength(this.stylePattern)) {
         int dateStyle = this.getStylePatternForChar(0);
         int timeStyle = this.getStylePatternForChar(1);
         if (dateStyle != -1 && timeStyle != -1) {
            return DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale);
         } else if (dateStyle != -1) {
            return DateFormat.getDateInstance(dateStyle, locale);
         } else if (timeStyle != -1) {
            return DateFormat.getTimeInstance(timeStyle, locale);
         } else {
            throw this.unsupportedStylePatternException();
         }
      } else {
         return DateFormat.getDateInstance(this.style, locale);
      }
   }

   private int getStylePatternForChar(int index) {
      if (this.stylePattern != null && this.stylePattern.length() > index) {
         char ch = this.stylePattern.charAt(index);

         return switch (ch) {
            case '-' -> -1;
            case 'F' -> 0;
            case 'L' -> 1;
            case 'M' -> 2;
            case 'S' -> 3;
            default -> throw this.unsupportedStylePatternException();
         };
      } else {
         throw this.unsupportedStylePatternException();
      }
   }

   private IllegalStateException unsupportedStylePatternException() {
      return new IllegalStateException("Unsupported style pattern '" + this.stylePattern + "'");
   }

   static {
      Map<DateTimeFormat.ISO, String> formats = new EnumMap<>(DateTimeFormat.ISO.class);
      formats.put(DateTimeFormat.ISO.DATE, "yyyy-MM-dd");
      formats.put(DateTimeFormat.ISO.TIME, "HH:mm:ss.SSSXXX");
      formats.put(DateTimeFormat.ISO.DATE_TIME, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
      ISO_PATTERNS = Collections.unmodifiableMap(formats);
      Map<DateTimeFormat.ISO, String> fallbackFormats = new EnumMap<>(DateTimeFormat.ISO.class);
      fallbackFormats.put(DateTimeFormat.ISO.TIME, "HH:mm:ssXXX");
      fallbackFormats.put(DateTimeFormat.ISO.DATE_TIME, "yyyy-MM-dd'T'HH:mm:ssXXX");
      ISO_FALLBACK_PATTERNS = Collections.unmodifiableMap(fallbackFormats);
   }
}
