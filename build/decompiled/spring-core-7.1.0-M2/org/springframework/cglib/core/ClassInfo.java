package org.springframework.cglib.core;

import org.springframework.asm.Type;

public abstract class ClassInfo {
   protected ClassInfo() {
   }

   public abstract Type getType();

   public abstract Type getSuperType();

   public abstract Type[] getInterfaces();

   public abstract int getModifiers();

   @Override
   public boolean equals(Object o) {
      if (o == null) {
         return false;
      } else {
         return o instanceof ClassInfo classInfo ? this.getType().equals(classInfo.getType()) : false;
      }
   }

   @Override
   public int hashCode() {
      return this.getType().hashCode();
   }

   @Override
   public String toString() {
      return this.getType().getClassName();
   }
}
