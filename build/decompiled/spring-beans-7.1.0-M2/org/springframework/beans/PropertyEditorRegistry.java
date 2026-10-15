package org.springframework.beans;

import java.beans.PropertyEditor;
import org.jspecify.annotations.Nullable;

public interface PropertyEditorRegistry {
   void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor);

   void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath, PropertyEditor propertyEditor);

   @Nullable PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath);
}
