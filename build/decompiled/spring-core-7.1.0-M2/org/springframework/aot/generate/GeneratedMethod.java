package org.springframework.aot.generate;

import java.util.function.Consumer;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.MethodSpec;
import org.springframework.util.Assert;

public final class GeneratedMethod {
   private final ClassName className;
   private final String name;
   private final MethodSpec methodSpec;

   GeneratedMethod(ClassName className, String name, Consumer<MethodSpec.Builder> method) {
      this.className = className;
      this.name = name;
      MethodSpec.Builder builder = MethodSpec.methodBuilder(this.name);
      method.accept(builder);
      this.methodSpec = builder.build();
      Assert.state(this.name.equals(this.methodSpec.name()), "'method' consumer must not change the generated method name");
   }

   public String getName() {
      return this.name;
   }

   public MethodReference toMethodReference() {
      return new DefaultMethodReference(this.methodSpec, this.className);
   }

   MethodSpec getMethodSpec() {
      return this.methodSpec;
   }

   @Override
   public String toString() {
      return this.name;
   }
}
