package org.springframework.cglib.transform;

import org.springframework.asm.ClassReader;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.ClassTransformer;

public class TransformingClassLoader extends AbstractClassLoader {
   private final ClassTransformerFactory t;

   public TransformingClassLoader(ClassLoader parent, ClassFilter filter, ClassTransformerFactory t) {
      super(parent, parent, filter);
      this.t = t;
   }

   @Override
   protected ClassGenerator getGenerator(ClassReader r) {
      ClassTransformer t2 = this.t.newInstance();
      return new TransformingClassGenerator(super.getGenerator(r), t2);
   }
}
