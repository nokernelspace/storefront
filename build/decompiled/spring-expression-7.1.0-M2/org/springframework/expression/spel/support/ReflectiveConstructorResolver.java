package org.springframework.expression.spel.support;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.ConstructorExecutor;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;

public class ReflectiveConstructorResolver implements ConstructorResolver {
   @Override
   public @Nullable ConstructorExecutor resolve(EvaluationContext context, String typeName, List<TypeDescriptor> argumentTypes) throws AccessException {
      try {
         TypeConverter typeConverter = context.getTypeConverter();
         Class<?> type = context.getTypeLocator().findType(typeName);
         Constructor<?>[] ctors = type.getConstructors();
         Arrays.sort(ctors, Comparator.comparingInt(Constructor::getParameterCount));
         Constructor<?> closeMatch = null;
         Constructor<?> matchRequiringConversion = null;

         for (Constructor<?> ctor : ctors) {
            int paramCount = ctor.getParameterCount();
            List<TypeDescriptor> paramDescriptors = new ArrayList<>(paramCount);

            for (int i = 0; i < paramCount; i++) {
               paramDescriptors.add(new TypeDescriptor(new MethodParameter(ctor, i)));
            }

            ReflectionHelper.ArgumentsMatchKind matchKind = null;
            if (ctor.isVarArgs() && argumentTypes.size() >= paramCount - 1) {
               matchKind = ReflectionHelper.compareArgumentsVarargs(paramDescriptors, argumentTypes, typeConverter);
            } else if (paramCount == argumentTypes.size()) {
               matchKind = ReflectionHelper.compareArguments(paramDescriptors, argumentTypes, typeConverter);
            }

            if (matchKind != null) {
               if (matchKind.isExactMatch()) {
                  return new ReflectiveConstructorExecutor(ctor);
               }

               if (matchKind.isCloseMatch()) {
                  closeMatch = ctor;
               } else if (matchKind.isMatchRequiringConversion()) {
                  matchRequiringConversion = ctor;
               }
            }
         }

         if (closeMatch != null) {
            return new ReflectiveConstructorExecutor(closeMatch);
         } else {
            return matchRequiringConversion != null ? new ReflectiveConstructorExecutor(matchRequiringConversion) : null;
         }
      } catch (EvaluationException ex) {
         throw new AccessException("Failed to resolve constructor", ex);
      }
   }
}
