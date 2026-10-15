package org.springframework.aot.generate;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.MethodSpec;
import org.springframework.javapoet.ParameterSpec;
import org.springframework.javapoet.TypeName;
import org.springframework.util.Assert;

public class DefaultMethodReference implements MethodReference {
   private final MethodSpec method;
   private final @Nullable ClassName declaringClass;

   public DefaultMethodReference(MethodSpec method, @Nullable ClassName declaringClass) {
      this.method = method;
      this.declaringClass = declaringClass;
   }

   @Override
   public CodeBlock toCodeBlock() {
      String methodName = this.method.name();
      if (this.isStatic()) {
         Assert.state(this.declaringClass != null, "Static method reference must define a declaring class");
         return CodeBlock.of("$T::$L", this.declaringClass, methodName);
      } else {
         return CodeBlock.of("this::$L", methodName);
      }
   }

   @Override
   public CodeBlock toInvokeCodeBlock(MethodReference.ArgumentCodeGenerator argumentCodeGenerator, @Nullable ClassName targetClassName) {
      String methodName = this.method.name();
      CodeBlock.Builder code = CodeBlock.builder();
      if (this.isStatic()) {
         Assert.state(this.declaringClass != null, "Static method reference must define a declaring class");
         if (this.declaringClass.equals(targetClassName)) {
            code.add("$L", methodName);
         } else {
            code.add("$T.$L", this.declaringClass, methodName);
         }
      } else {
         if (this.declaringClass != null && !this.declaringClass.equals(targetClassName)) {
            code.add(this.instantiateDeclaringClass(this.declaringClass));
         }

         code.add("$L", methodName);
      }

      code.add("(");
      this.addArguments(code, argumentCodeGenerator);
      code.add(")");
      return code.build();
   }

   protected void addArguments(CodeBlock.Builder code, MethodReference.ArgumentCodeGenerator argumentCodeGenerator) {
      List<CodeBlock> arguments = new ArrayList<>();
      TypeName[] argumentTypes = this.method.parameters().stream().map(ParameterSpec::type).toArray(TypeName[]::new);

      for (int i = 0; i < argumentTypes.length; i++) {
         TypeName argumentType = argumentTypes[i];
         CodeBlock argumentCode = argumentCodeGenerator.generateCode(argumentType);
         if (argumentCode == null) {
            throw new IllegalArgumentException("Could not generate code for " + this + ": parameter " + i + " of type " + argumentType + " is not supported");
         }

         arguments.add(argumentCode);
      }

      code.add(CodeBlock.join(arguments, ", "));
   }

   protected CodeBlock instantiateDeclaringClass(ClassName declaringClass) {
      return CodeBlock.of("new $T().", declaringClass);
   }

   private boolean isStatic() {
      return this.method.modifiers().contains(Modifier.STATIC);
   }

   @Override
   public String toString() {
      String methodName = this.method.name();
      return this.isStatic()
         ? this.declaringClass + "::" + methodName
         : (this.declaringClass != null ? "<" + this.declaringClass + ">" : "<instance>") + "::" + methodName;
   }
}
