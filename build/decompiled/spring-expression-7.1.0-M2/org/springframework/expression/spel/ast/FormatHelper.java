package org.springframework.expression.spel.ast;

import java.util.List;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;

abstract class FormatHelper {
   static String formatMethodForMessage(String name, List<TypeDescriptor> argumentTypes) {
      StringJoiner sj = new StringJoiner(",", "(", ")");

      for (TypeDescriptor typeDescriptor : argumentTypes) {
         String className = typeDescriptor != null ? formatClassNameForMessage(typeDescriptor.getType()) : "null";
         sj.add(className);
      }

      return name + sj;
   }

   static String formatClassNameForMessage(@Nullable Class<?> clazz) {
      return clazz != null ? clazz.getTypeName() : "null";
   }
}
