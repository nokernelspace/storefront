package org.springframework.cglib.core;

import org.springframework.asm.Type;

public interface FieldTypeCustomizer extends KeyFactoryCustomizer {
   void customize(CodeEmitter e, int index, Type type);

   Type getOutType(int index, Type type);
}
