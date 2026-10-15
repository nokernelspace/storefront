package org.springframework.aop.config;

import org.springframework.beans.factory.parsing.ParseState.Entry;

public class PointcutEntry implements Entry {
   private final String name;

   public PointcutEntry(String name) {
      this.name = name;
   }

   @Override
   public String toString() {
      return "Pointcut '" + this.name + "'";
   }
}
