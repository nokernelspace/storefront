package org.springframework.expression.spel;

import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.expression.IndexAccessor;

public interface CompilableIndexAccessor extends IndexAccessor, Opcodes {
   boolean isCompilable();

   Class<?> getIndexedValueType();

   void generateCode(SpelNode indexNode, MethodVisitor methodVisitor, CodeFlow codeFlow);
}
