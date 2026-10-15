package org.springframework.cglib.core;

import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Type;

public class LocalVariablesSorter extends MethodVisitor {
   protected final int firstLocal;
   private final LocalVariablesSorter.State state;

   public LocalVariablesSorter(final int access, final String desc, final MethodVisitor mv) {
      super(Constants.ASM_API, mv);
      this.state = new LocalVariablesSorter.State();
      Type[] args = Type.getArgumentTypes(desc);
      this.state.nextLocal = (8 & access) != 0 ? 0 : 1;

      for (Type arg : args) {
         this.state.nextLocal = this.state.nextLocal + arg.getSize();
      }

      this.firstLocal = this.state.nextLocal;
   }

   public LocalVariablesSorter(LocalVariablesSorter lvs) {
      super(Constants.ASM_API, lvs.mv);
      this.state = lvs.state;
      this.firstLocal = lvs.firstLocal;
   }

   @Override
   public void visitVarInsn(final int opcode, final int var) {
      int size = switch (opcode) {
         case 22, 24, 55, 57 -> 2;
         default -> 1;
      };
      this.mv.visitVarInsn(opcode, this.remap(var, size));
   }

   @Override
   public void visitIincInsn(final int var, final int increment) {
      this.mv.visitIincInsn(this.remap(var, 1), increment);
   }

   @Override
   public void visitMaxs(final int maxStack, final int maxLocals) {
      this.mv.visitMaxs(maxStack, this.state.nextLocal);
   }

   @Override
   public void visitLocalVariable(final String name, final String desc, final String signature, final Label start, final Label end, final int index) {
      this.mv.visitLocalVariable(name, desc, signature, start, end, this.remap(index));
   }

   protected int newLocal(final int size) {
      int var = this.state.nextLocal;
      this.state.nextLocal += size;
      return var;
   }

   private int remap(final int var, final int size) {
      if (var < this.firstLocal) {
         return var;
      }

      int key = 2 * var + size - 1;
      int length = this.state.mapping.length;
      if (key >= length) {
         int[] newMapping = new int[Math.max(2 * length, key + 1)];
         System.arraycopy(this.state.mapping, 0, newMapping, 0, length);
         this.state.mapping = newMapping;
      }

      int value = this.state.mapping[key];
      if (value == 0) {
         value = this.state.nextLocal + 1;
         this.state.mapping[key] = value;
         this.state.nextLocal += size;
      }

      return value - 1;
   }

   private int remap(final int var) {
      if (var < this.firstLocal) {
         return var;
      }

      int key = 2 * var;
      int value = key < this.state.mapping.length ? this.state.mapping[key] : 0;
      if (value == 0) {
         value = key + 1 < this.state.mapping.length ? this.state.mapping[key + 1] : 0;
      }

      if (value == 0) {
         throw new IllegalStateException("Unknown local variable " + var);
      } else {
         return value - 1;
      }
   }

   private static class State {
      int[] mapping = new int[40];
      int nextLocal;
   }
}
