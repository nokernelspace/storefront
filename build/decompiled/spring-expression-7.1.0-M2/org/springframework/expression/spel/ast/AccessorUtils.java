package org.springframework.expression.spel.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.TargetedAccessor;
import org.springframework.util.ObjectUtils;

abstract class AccessorUtils {
   static <T extends TargetedAccessor> List<T> getAccessorsToTry(@Nullable Object targetObject, List<T> accessors) {
      Class<?> targetType = targetObject != null ? targetObject.getClass() : null;
      return getAccessorsToTry(targetType, accessors);
   }

   static <T extends TargetedAccessor> List<T> getAccessorsToTry(@Nullable Class<?> targetType, List<T> accessors) {
      if (accessors.isEmpty()) {
         return Collections.emptyList();
      }

      List<T> exactMatches = new ArrayList<>();
      List<T> inexactMatches = new ArrayList<>();
      List<T> genericMatches = new ArrayList<>();

      for (T accessor : accessors) {
         Class<?>[] targets = accessor.getSpecificTargetClasses();
         if (ObjectUtils.isEmpty(targets)) {
            genericMatches.add(accessor);
         } else if (targetType != null) {
            for (Class<?> clazz : targets) {
               if (clazz == targetType) {
                  exactMatches.add(accessor);
               } else if (clazz.isAssignableFrom(targetType)) {
                  inexactMatches.add(accessor);
               }
            }
         }
      }

      int size = exactMatches.size() + inexactMatches.size() + genericMatches.size();
      if (size == 0) {
         return Collections.emptyList();
      }

      List<T> result = new ArrayList<>(size);
      result.addAll(exactMatches);
      result.addAll(inexactMatches);
      result.addAll(genericMatches);
      return result;
   }
}
