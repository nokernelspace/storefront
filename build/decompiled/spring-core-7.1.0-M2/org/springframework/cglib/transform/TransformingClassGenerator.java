package org.springframework.cglib.transform;

import org.springframework.asm.ClassVisitor;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.ClassTransformer;

public class TransformingClassGenerator implements ClassGenerator {
   private final ClassGenerator gen;
   private final ClassTransformer t;

   public TransformingClassGenerator(ClassGenerator gen, ClassTransformer t) {
      this.gen = gen;
      this.t = t;
   }

   @Override
   public void generateClass(ClassVisitor v) throws Exception {
      this.t.setTarget(v);
      this.gen.generateClass(this.t);
   }
}
