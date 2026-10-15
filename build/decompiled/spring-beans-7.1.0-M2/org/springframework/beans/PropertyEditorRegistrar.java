package org.springframework.beans;

public interface PropertyEditorRegistrar {
   void registerCustomEditors(PropertyEditorRegistry registry);

   default boolean overridesDefaultEditors() {
      return false;
   }
}
