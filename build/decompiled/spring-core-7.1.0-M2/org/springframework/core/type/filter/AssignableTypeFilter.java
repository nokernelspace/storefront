package org.springframework.core.type.filter;

import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

public class AssignableTypeFilter extends AbstractTypeHierarchyTraversingFilter {
   private final Class<?> targetType;

   public AssignableTypeFilter(Class<?> targetType) {
      super(true, true);
      this.targetType = targetType;
   }

   public final Class<?> getTargetType() {
      return this.targetType;
   }

   @Override
   protected boolean matchClassName(String className) {
      return this.targetType.getName().equals(className);
   }

   @Override
   protected @Nullable Boolean matchSuperClass(String superClassName) {
      return this.matchTargetType(superClassName);
   }

   @Override
   protected @Nullable Boolean matchInterface(String interfaceName) {
      return this.matchTargetType(interfaceName);
   }

   protected @Nullable Boolean matchTargetType(String typeName) {
      if (this.targetType.getName().equals(typeName)) {
         return true;
      }

      if (Object.class.getName().equals(typeName)) {
         return false;
      }

      if (typeName.startsWith("java")) {
         try {
            Class<?> clazz = ClassUtils.forName(typeName, this.getClass().getClassLoader());
            return this.targetType.isAssignableFrom(clazz);
         } catch (Throwable var3) {
         }
      }

      return null;
   }
}
