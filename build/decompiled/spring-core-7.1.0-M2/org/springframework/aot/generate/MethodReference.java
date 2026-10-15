package org.springframework.aot.generate;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.TypeName;

public interface MethodReference {
   CodeBlock toCodeBlock();

   default CodeBlock toInvokeCodeBlock(MethodReference.ArgumentCodeGenerator argumentCodeGenerator) {
      return this.toInvokeCodeBlock(argumentCodeGenerator, null);
   }

   CodeBlock toInvokeCodeBlock(MethodReference.ArgumentCodeGenerator argumentCodeGenerator, @Nullable ClassName targetClassName);

   interface ArgumentCodeGenerator {
      @Nullable CodeBlock generateCode(TypeName argumentType);

      static MethodReference.ArgumentCodeGenerator none() {
         return from(type -> null);
      }

      static MethodReference.ArgumentCodeGenerator of(Class<?> argumentType, String argumentCode) {
         return from(candidateType -> candidateType.equals(ClassName.get(argumentType)) ? CodeBlock.of(argumentCode) : null);
      }

      static MethodReference.ArgumentCodeGenerator from(Function<TypeName, @Nullable CodeBlock> function) {
         return function::apply;
      }

      default MethodReference.ArgumentCodeGenerator and(Class<?> argumentType, String argumentCode) {
         return this.and(of(argumentType, argumentCode));
      }

      default MethodReference.ArgumentCodeGenerator and(MethodReference.ArgumentCodeGenerator argumentCodeGenerator) {
         return from(type -> {
            CodeBlock code = this.generateCode(type);
            return code != null ? code : argumentCodeGenerator.generateCode(type);
         });
      }
   }
}
