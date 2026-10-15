package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class ClassEditor extends PropertyEditorSupport {
   private final @Nullable ClassLoader classLoader;

   public ClassEditor() {
      this(null);
   }

   public ClassEditor(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (StringUtils.hasText(text)) {
         this.setValue(ClassUtils.resolveClassName(text.trim(), this.classLoader));
      } else {
         this.setValue(null);
      }
   }

   @Override
   public String getAsText() {
      Class<?> clazz = (Class<?>)this.getValue();
      return clazz != null ? clazz.getTypeName() : "";
   }
}
