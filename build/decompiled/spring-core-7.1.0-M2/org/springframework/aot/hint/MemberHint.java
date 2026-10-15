package org.springframework.aot.hint;

public abstract class MemberHint {
   private final String name;

   protected MemberHint(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }
}
