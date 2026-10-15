package org.springframework.boot.ansi;

import org.springframework.util.Assert;

public final class Ansi8BitColor implements AnsiElement {
   private final String prefix;
   private final int code;

   private Ansi8BitColor(String prefix, int code) {
      Assert.isTrue(code >= 0 && code <= 255, "'code' must be between 0 and 255");
      this.prefix = prefix;
      this.code = code;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         Ansi8BitColor other = (Ansi8BitColor)obj;
         return this.prefix.equals(other.prefix) && this.code == other.code;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.prefix.hashCode() * 31 + this.code;
   }

   @Override
   public String toString() {
      return this.prefix + this.code;
   }

   public static Ansi8BitColor foreground(int code) {
      return new Ansi8BitColor("38;5;", code);
   }

   public static Ansi8BitColor background(int code) {
      return new Ansi8BitColor("48;5;", code);
   }
}
