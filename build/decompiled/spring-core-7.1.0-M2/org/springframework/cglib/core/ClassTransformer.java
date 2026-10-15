package org.springframework.cglib.core;

import org.springframework.asm.ClassVisitor;

public abstract class ClassTransformer extends ClassVisitor {
   public ClassTransformer() {
      super(Constants.ASM_API);
   }

   public ClassTransformer(int opcode) {
      super(opcode);
   }

   public abstract void setTarget(ClassVisitor target);
}
