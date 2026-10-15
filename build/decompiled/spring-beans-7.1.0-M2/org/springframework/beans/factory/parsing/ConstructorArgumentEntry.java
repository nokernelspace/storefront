package org.springframework.beans.factory.parsing;

import org.springframework.util.Assert;

public class ConstructorArgumentEntry implements ParseState.Entry {
   private final int index;

   public ConstructorArgumentEntry() {
      this.index = -1;
   }

   public ConstructorArgumentEntry(int index) {
      Assert.isTrue(index >= 0, "Constructor argument index must be greater than or equal to zero");
      this.index = index;
   }

   @Override
   public String toString() {
      return "Constructor-arg" + (this.index >= 0 ? " #" + this.index : "");
   }
}
