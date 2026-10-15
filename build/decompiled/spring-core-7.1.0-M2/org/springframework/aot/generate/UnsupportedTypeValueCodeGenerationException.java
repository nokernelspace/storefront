package org.springframework.aot.generate;

public class UnsupportedTypeValueCodeGenerationException extends ValueCodeGenerationException {
   public UnsupportedTypeValueCodeGenerationException(Object value) {
      super("Code generation does not support " + value.getClass().getName(), value, null);
   }
}
