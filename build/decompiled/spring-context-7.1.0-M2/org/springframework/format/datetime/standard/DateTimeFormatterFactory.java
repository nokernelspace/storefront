package org.springframework.format.datetime.standard;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DateTimeFormatterFactory {
   private @Nullable String pattern;
   private DateTimeFormat.@Nullable ISO iso;
   private @Nullable FormatStyle dateStyle;
   private @Nullable FormatStyle timeStyle;
   private @Nullable TimeZone timeZone;

   public DateTimeFormatterFactory() {
   }

   public DateTimeFormatterFactory(String pattern) {
      this.pattern = pattern;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   public void setIso(DateTimeFormat.ISO iso) {
      this.iso = iso;
   }

   public void setDateStyle(FormatStyle dateStyle) {
      this.dateStyle = dateStyle;
   }

   public void setTimeStyle(FormatStyle timeStyle) {
      this.timeStyle = timeStyle;
   }

   public void setDateTimeStyle(FormatStyle dateTimeStyle) {
      this.dateStyle = dateTimeStyle;
      this.timeStyle = dateTimeStyle;
   }

   public void setStylePattern(String style) {
      Assert.isTrue(style.length() == 2, "Style pattern must consist of two characters");
      this.dateStyle = this.convertStyleCharacter(style.charAt(0));
      this.timeStyle = this.convertStyleCharacter(style.charAt(1));
   }

   private @Nullable FormatStyle convertStyleCharacter(char c) {
      return switch (c) {
         case '-' -> null;
         case 'F' -> FormatStyle.FULL;
         case 'L' -> FormatStyle.LONG;
         case 'M' -> FormatStyle.MEDIUM;
         case 'S' -> FormatStyle.SHORT;
         default -> throw new IllegalArgumentException("Invalid style character '" + c + "'");
      };
   }

   public void setTimeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
   }

   public DateTimeFormatter createDateTimeFormatter() {
      return this.createDateTimeFormatter(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
   }

   public DateTimeFormatter createDateTimeFormatter(DateTimeFormatter fallbackFormatter) {
      DateTimeFormatter dateTimeFormatter = null;
      if (StringUtils.hasLength(this.pattern)) {
         dateTimeFormatter = DateTimeFormatterUtils.createStrictDateTimeFormatter(this.pattern);
      } else if (this.iso != null && this.iso != DateTimeFormat.ISO.NONE) {
         dateTimeFormatter = switch (this.iso) {
            case DATE -> DateTimeFormatter.ISO_DATE;
            case TIME -> DateTimeFormatter.ISO_TIME;
            case DATE_TIME -> DateTimeFormatter.ISO_DATE_TIME;
            default -> throw new IllegalStateException("Unsupported ISO format: " + this.iso);
         };
      } else if (this.dateStyle != null && this.timeStyle != null) {
         dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(this.dateStyle, this.timeStyle);
      } else if (this.dateStyle != null) {
         dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(this.dateStyle);
      } else if (this.timeStyle != null) {
         dateTimeFormatter = DateTimeFormatter.ofLocalizedTime(this.timeStyle);
      }

      if (dateTimeFormatter != null && this.timeZone != null) {
         dateTimeFormatter = dateTimeFormatter.withZone(this.timeZone.toZoneId());
      }

      return dateTimeFormatter != null ? dateTimeFormatter : fallbackFormatter;
   }
}
