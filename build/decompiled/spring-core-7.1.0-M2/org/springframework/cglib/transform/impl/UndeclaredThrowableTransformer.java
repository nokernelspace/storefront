package org.springframework.cglib.transform.impl;

import java.lang.reflect.Constructor;
import org.springframework.asm.Type;
import org.springframework.cglib.core.Block;
import org.springframework.cglib.core.CodeEmitter;
import org.springframework.cglib.core.Constants;
import org.springframework.cglib.core.EmitUtils;
import org.springframework.cglib.core.Signature;
import org.springframework.cglib.core.TypeUtils;
import org.springframework.cglib.transform.ClassEmitterTransformer;

public class UndeclaredThrowableTransformer extends ClassEmitterTransformer {
   private final Type wrapper;

   public UndeclaredThrowableTransformer(Class wrapper) {
      this.wrapper = Type.getType(wrapper);
      boolean found = false;
      Constructor[] cstructs = wrapper.getConstructors();

      for (Constructor cstruct : cstructs) {
         Class[] types = cstruct.getParameterTypes();
         if (types.length == 1 && types[0].equals(Throwable.class)) {
            found = true;
            break;
         }
      }

      if (!found) {
         throw new IllegalArgumentException(wrapper + " does not have a single-arg constructor that takes a Throwable");
      }
   }

   @Override
   public CodeEmitter begin_method(int access, final Signature sig, final Type[] exceptions) {
      CodeEmitter e = super.begin_method(access, sig, exceptions);
      return !TypeUtils.isAbstract(access) && !sig.equals(Constants.SIG_STATIC) ? new CodeEmitter(e) {
         private final boolean isConstructor = "<init>".equals(sig.getName());
         private Block handler = this.begin_block();
         private boolean callToSuperSeen;

         @Override
         public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (this.isConstructor && !this.callToSuperSeen && "<init>".equals(name)) {
               this.handler = this.begin_block();
               this.callToSuperSeen = true;
            }
         }

         @Override
         public void visitMaxs(int maxStack, int maxLocals) {
            this.handler.end();
            EmitUtils.wrap_undeclared_throwable(this, this.handler, exceptions, UndeclaredThrowableTransformer.this.wrapper);
            super.visitMaxs(maxStack, maxLocals);
         }
      } : e;
   }
}
