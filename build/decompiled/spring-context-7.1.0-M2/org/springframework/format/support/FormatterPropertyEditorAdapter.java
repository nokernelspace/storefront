package org.springframework.format.support;

import java.beans.PropertyEditorSupport;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.Formatter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class FormatterPropertyEditorAdapter extends PropertyEditorSupport {
   private final Formatter<Object> formatter;

   public FormatterPropertyEditorAdapter(Formatter<?> formatter) {
      Assert.notNull(formatter, "Formatter must not be null");
      this.formatter = (Formatter<Object>)formatter;
   }

   public Class<?> getFieldType() {
      return FormattingConversionService.getFieldType(this.formatter);
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (StringUtils.hasText(text)) {
         try {
            this.setValue(this.formatter.parse(text, LocaleContextHolder.getLocale()));
         } catch (IllegalArgumentException ex) {
            throw ex;
         } catch (Throwable ex) {
            throw new IllegalArgumentException("Parse attempt failed for value [" + text + "]", ex);
         }
      } else {
         this.setValue(null);
      }
   }

   @Override
   public String getAsText() {
      Object value = this.getValue();
      return value != null ? this.formatter.print(value, LocaleContextHolder.getLocale()) : "";
   }
}
