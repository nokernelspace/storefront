package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ClassArrayEditor extends PropertyEditorSupport {
   private final @Nullable ClassLoader classLoader;

   public ClassArrayEditor() {
      this(null);
   }

   public ClassArrayEditor(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (StringUtils.hasText(text)) {
         String[] classNames = StringUtils.commaDelimitedListToStringArray(text);
         Class<?>[] classes = new Class[classNames.length];

         for (int i = 0; i < classNames.length; i++) {
            String className = classNames[i].trim();
            classes[i] = ClassUtils.resolveClassName(className, this.classLoader);
         }

         this.setValue(classes);
      } else {
         this.setValue(null);
      }
   }

   @Override
   public String getAsText() {
      Class<?>[] classes = (Class<?>[])this.getValue();
      if (ObjectUtils.isEmpty(classes)) {
         return "";
      }

      StringJoiner sj = new StringJoiner(",");

      for (Class<?> clazz : classes) {
         sj.add(clazz.getTypeName());
      }

      return sj.toString();
   }
}
