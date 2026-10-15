package org.springframework.expression.spel.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodFilter;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;

public class ReflectiveMethodResolver implements MethodResolver {
   private final boolean useDistance;
   private @Nullable Map<Class<?>, MethodFilter> filters;

   public ReflectiveMethodResolver() {
      this(true);
   }

   public ReflectiveMethodResolver(boolean useDistance) {
      this.useDistance = useDistance;
   }

   public void registerMethodFilter(Class<?> type, @Nullable MethodFilter filter) {
      if (this.filters == null) {
         this.filters = new HashMap<>();
      }

      if (filter != null) {
         this.filters.put(type, filter);
      } else {
         this.filters.remove(type);
      }
   }

   @Override
   public @Nullable MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException {
      try {
         TypeConverter typeConverter = context.getTypeConverter();
         Class<?> type = targetObject instanceof Class<?> clazz ? clazz : targetObject.getClass();
         ArrayList<Method> methods = new ArrayList<>(this.getMethods(type, targetObject));
         methods.removeIf(methodx -> !methodx.getName().equals(name));
         MethodFilter filter = this.filters != null ? this.filters.get(type) : null;
         if (filter != null) {
            List<Method> filtered = filter.filter(methods);
            methods = filtered instanceof ArrayList<Method> arrayList ? arrayList : new ArrayList<>(filtered);
         }

         if (methods.size() > 1) {
            methods.sort((m1, m2) -> {
               int m1pl = m1.getParameterCount();
               int m2pl = m2.getParameterCount();
               if (m1pl == m2pl) {
                  if (!m1.isVarArgs() && m2.isVarArgs()) {
                     return -1;
                  } else {
                     return m1.isVarArgs() && !m2.isVarArgs() ? 1 : 0;
                  }
               } else {
                  return Integer.compare(m1pl, m2pl);
               }
            });
         }

         for (int i = 0; i < methods.size(); i++) {
            methods.set(i, BridgeMethodResolver.findBridgedMethod(methods.get(i)));
         }

         Set<Method> methodsToIterate = new LinkedHashSet<>(methods);
         Method closeMatch = null;
         int closeMatchDistance = Integer.MAX_VALUE;
         Method matchRequiringConversion = null;
         boolean multipleOptions = false;

         for (Method method : methodsToIterate) {
            int paramCount = method.getParameterCount();
            List<TypeDescriptor> paramDescriptors = new ArrayList<>(paramCount);

            for (int i = 0; i < paramCount; i++) {
               paramDescriptors.add(new TypeDescriptor(new MethodParameter(method, i)));
            }

            ReflectionHelper.ArgumentsMatchKind matchKind = null;
            if (method.isVarArgs() && argumentTypes.size() >= paramCount - 1) {
               matchKind = ReflectionHelper.compareArgumentsVarargs(paramDescriptors, argumentTypes, typeConverter);
            } else if (paramCount == argumentTypes.size()) {
               matchKind = ReflectionHelper.compareArguments(paramDescriptors, argumentTypes, typeConverter);
            }

            if (matchKind != null) {
               if (matchKind.isExactMatch()) {
                  return new ReflectiveMethodExecutor(method, type);
               }

               if (matchKind.isCloseMatch()) {
                  if (this.useDistance) {
                     int matchDistance = ReflectionHelper.getTypeDifferenceWeight(paramDescriptors, argumentTypes);
                     if (closeMatch == null || matchDistance < closeMatchDistance) {
                        closeMatch = method;
                        closeMatchDistance = matchDistance;
                     }
                  } else if (closeMatch == null) {
                     closeMatch = method;
                  }
               } else if (matchKind.isMatchRequiringConversion()) {
                  if (matchRequiringConversion != null) {
                     multipleOptions = true;
                  }

                  matchRequiringConversion = method;
               }
            }
         }

         if (closeMatch != null) {
            return new ReflectiveMethodExecutor(closeMatch, type);
         } else if (matchRequiringConversion == null) {
            return null;
         } else if (multipleOptions) {
            throw new SpelEvaluationException(SpelMessage.MULTIPLE_POSSIBLE_METHODS, name);
         } else {
            return new ReflectiveMethodExecutor(matchRequiringConversion, type);
         }
      } catch (EvaluationException ex) {
         throw new AccessException("Failed to resolve method", ex);
      }
   }

   private Set<Method> getMethods(Class<?> type, Object targetObject) {
      if (targetObject instanceof Class) {
         Set<Method> result = new LinkedHashSet<>();

         for (Method method : this.getMethods(type)) {
            if (Modifier.isStatic(method.getModifiers())) {
               result.add(method);
            }
         }

         Collections.addAll(result, this.getMethods(Class.class));
         return result;
      } else if (!Proxy.isProxyClass(type)) {
         Set<Method> result = new LinkedHashSet<>();

         for (Method method : this.getMethods(type)) {
            if (this.isCandidateForInvocation(method, type)) {
               result.add(method);
            }
         }

         return result;
      } else {
         Set<Method> result = new LinkedHashSet<>();

         for (Class<?> ifc : type.getInterfaces()) {
            for (Method method : this.getMethods(ifc)) {
               if (this.isCandidateForInvocation(method, type)) {
                  result.add(method);
               }
            }
         }

         for (Method method : this.getMethods(Object.class)) {
            if (this.isCandidateForInvocation(method, type)) {
               result.add(method);
            }
         }

         return result;
      }
   }

   protected Method[] getMethods(Class<?> type) {
      return type.getMethods();
   }

   protected boolean isCandidateForInvocation(Method method, Class<?> targetClass) {
      return true;
   }
}
