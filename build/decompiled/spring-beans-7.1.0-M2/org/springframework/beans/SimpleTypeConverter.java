package org.springframework.beans;

public class SimpleTypeConverter extends TypeConverterSupport {
   public SimpleTypeConverter() {
      this.typeConverterDelegate = new TypeConverterDelegate(this);
      this.registerDefaultEditors();
   }
}
