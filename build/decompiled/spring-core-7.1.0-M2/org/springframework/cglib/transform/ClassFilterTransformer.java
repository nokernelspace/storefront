package org.springframework.cglib.transform;

import org.springframework.cglib.core.ClassTransformer;

public class ClassFilterTransformer extends AbstractClassFilterTransformer {
   private ClassFilter filter;

   public ClassFilterTransformer(ClassFilter filter, ClassTransformer pass) {
      super(pass);
      this.filter = filter;
   }

   @Override
   protected boolean accept(int version, int access, String name, String signature, String superName, String[] interfaces) {
      return this.filter.accept(name.replace('/', '.'));
   }
}
