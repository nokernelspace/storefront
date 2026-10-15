package org.springframework.beans.factory.parsing;

import org.springframework.util.StringUtils;

public class PropertyEntry implements ParseState.Entry {
   private final String name;

   public PropertyEntry(String name) {
      if (!StringUtils.hasText(name)) {
         throw new IllegalArgumentException("Invalid property name '" + name + "'");
      }

      this.name = name;
   }

   @Override
   public String toString() {
      return "Property '" + this.name + "'";
   }
}
