package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ResourceBundleEditor extends PropertyEditorSupport {
   public static final String BASE_NAME_SEPARATOR = "_";

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      Assert.hasText(text, "'text' must not be empty");
      String name = text.trim();
      int separator = name.indexOf("_");
      if (separator == -1) {
         this.setValue(ResourceBundle.getBundle(name));
      } else {
         String baseName = name.substring(0, separator);
         if (!StringUtils.hasText(baseName)) {
            throw new IllegalArgumentException("Invalid ResourceBundle name: '" + text + "'");
         }

         String localeString = name.substring(separator + 1);
         Locale locale = StringUtils.parseLocaleString(localeString);
         this.setValue(locale != null ? ResourceBundle.getBundle(baseName, locale) : ResourceBundle.getBundle(baseName));
      }
   }
}
