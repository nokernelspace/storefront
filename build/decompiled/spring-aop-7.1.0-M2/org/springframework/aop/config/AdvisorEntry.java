package org.springframework.aop.config;

import org.springframework.beans.factory.parsing.ParseState.Entry;

public class AdvisorEntry implements Entry {
   private final String name;

   public AdvisorEntry(String name) {
      this.name = name;
   }

   @Override
   public String toString() {
      return "Advisor '" + this.name + "'";
   }
}
