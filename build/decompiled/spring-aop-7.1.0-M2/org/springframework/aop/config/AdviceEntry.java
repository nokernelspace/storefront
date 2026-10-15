package org.springframework.aop.config;

import org.springframework.beans.factory.parsing.ParseState.Entry;

public class AdviceEntry implements Entry {
   private final String kind;

   public AdviceEntry(String kind) {
      this.kind = kind;
   }

   @Override
   public String toString() {
      return "Advice (" + this.kind + ")";
   }
}
