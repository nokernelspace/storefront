package org.springframework.beans.factory.aot;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.ValueCodeGenerator;
import org.springframework.aot.generate.ValueCodeGenerator.Delegate;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MethodOverride;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

class BeanDefinitionPropertiesCodeGenerator {
   private static final RootBeanDefinition DEFAULT_BEAN_DEFINITION = new RootBeanDefinition();
   private static final String BEAN_DEFINITION_VARIABLE = "beanDefinition";
   private final RuntimeHints hints;
   private final Predicate<String> attributeFilter;
   private final ValueCodeGenerator valueCodeGenerator;

   BeanDefinitionPropertiesCodeGenerator(
      RuntimeHints hints,
      Predicate<String> attributeFilter,
      GeneratedMethods generatedMethods,
      List<Delegate> additionalDelegates,
      BiFunction<String, Object, CodeBlock> customValueCodeGenerator
   ) {
      this.hints = hints;
      this.attributeFilter = attributeFilter;
      List<Delegate> customDelegates = new ArrayList<>();
      customDelegates.add((valueCodeGenerator, value) -> customValueCodeGenerator.apply(BeanDefinitionPropertiesCodeGenerator.PropertyNamesStack.peek(), value));
      customDelegates.addAll(additionalDelegates);
      this.valueCodeGenerator = BeanDefinitionPropertyValueCodeGeneratorDelegates.createValueCodeGenerator(generatedMethods, customDelegates);
   }

   CodeBlock generateCode(RootBeanDefinition beanDefinition) {
      Builder code = CodeBlock.builder();
      this.addStatementForValue(code, beanDefinition, BeanDefinition::getScope, this::hasScope, "$L.setScope($S)");
      this.addStatementForValue(code, beanDefinition, AbstractBeanDefinition::isBackgroundInit, "$L.setBackgroundInit($L)");
      this.addStatementForValue(code, beanDefinition, AbstractBeanDefinition::getLazyInit, "$L.setLazyInit($L)");
      this.addStatementForValue(code, beanDefinition, BeanDefinition::getDependsOn, this::hasDependsOn, "$L.setDependsOn($L)", this::toStringVarArgs);
      this.addStatementForValue(code, beanDefinition, BeanDefinition::isAutowireCandidate, "$L.setAutowireCandidate($L)");
      this.addStatementForValue(code, beanDefinition, AbstractBeanDefinition::isDefaultCandidate, "$L.setDefaultCandidate($L)");
      this.addStatementForValue(code, beanDefinition, BeanDefinition::isPrimary, "$L.setPrimary($L)");
      this.addStatementForValue(code, beanDefinition, BeanDefinition::isFallback, "$L.setFallback($L)");
      this.addStatementForValue(code, beanDefinition, AbstractBeanDefinition::isSynthetic, "$L.setSynthetic($L)");
      this.addStatementForValue(code, beanDefinition, BeanDefinition::getRole, this::hasRole, "$L.setRole($L)", this::toRole);
      this.addInitDestroyMethods(code, beanDefinition, beanDefinition.getInitMethodNames(), "$L.setInitMethodNames($L)");
      this.addInitDestroyMethods(code, beanDefinition, beanDefinition.getDestroyMethodNames(), "$L.setDestroyMethodNames($L)");
      if (beanDefinition.getFactoryBeanName() != null) {
         this.addStatementForValue(code, beanDefinition, BeanDefinition::getFactoryBeanName, "$L.setFactoryBeanName(\"$L\")");
      }

      this.addConstructorArgumentValues(code, beanDefinition);
      this.addPropertyValues(code, beanDefinition);
      this.addAttributes(code, beanDefinition);
      this.addQualifiers(code, beanDefinition);
      this.addMethodOverrides(code, beanDefinition);
      return code.build();
   }

   private void addInitDestroyMethods(Builder code, AbstractBeanDefinition beanDefinition, String @Nullable [] methodNames, String format) {
      this.hints.reflection().registerType(TypeReference.of("org.reactivestreams.Publisher"), new MemberCategory[0]);
      if (!ObjectUtils.isEmpty(methodNames)) {
         Class<?> beanType = ClassUtils.getUserClass(beanDefinition.getResolvableType().toClass());
         Arrays.stream(methodNames).forEach(methodName -> this.addInitDestroyHint(beanType, methodName));
         CodeBlock arguments = Arrays.stream(methodNames).map(name -> CodeBlock.of("$S", new Object[]{name})).collect(CodeBlock.joining(", "));
         code.addStatement(format, new Object[]{"beanDefinition", arguments});
      }
   }

   private void addInitDestroyHint(Class<?> beanUserClass, String methodName) {
      Class<?> methodDeclaringClass = beanUserClass;
      int indexOfDot = methodName.lastIndexOf(46);
      if (indexOfDot > 0) {
         String className = methodName.substring(0, indexOfDot);
         methodName = methodName.substring(indexOfDot + 1);
         if (!beanUserClass.getName().equals(className)) {
            try {
               methodDeclaringClass = ClassUtils.forName(className, beanUserClass.getClassLoader());
            } catch (Throwable ex) {
               throw new IllegalStateException("Failed to load Class [" + className + "] from ClassLoader [" + beanUserClass.getClassLoader() + "]", ex);
            }
         }
      }

      Method method = ReflectionUtils.findMethod(methodDeclaringClass, methodName);
      if (method != null) {
         this.hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
         Method publiclyAccessibleMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(method, beanUserClass);
         if (!publiclyAccessibleMethod.equals(method)) {
            this.hints.reflection().registerMethod(publiclyAccessibleMethod, ExecutableMode.INVOKE);
         }
      }
   }

   private void addConstructorArgumentValues(Builder code, BeanDefinition beanDefinition) {
      ConstructorArgumentValues constructorValues = beanDefinition.getConstructorArgumentValues();
      Map<Integer, ConstructorArgumentValues.ValueHolder> indexedValues = constructorValues.getIndexedArgumentValues();
      if (!indexedValues.isEmpty()) {
         indexedValues.forEach((index, valueHolder) -> {
            Object value = valueHolder.getValue();
            CodeBlock valueCode = this.castIfNecessary(value == null, Object.class, this.generateValue(valueHolder.getName(), value));
            code.addStatement("$L.getConstructorArgumentValues().addIndexedArgumentValue($L, $L)", new Object[]{"beanDefinition", index, valueCode});
         });
      }

      List<ConstructorArgumentValues.ValueHolder> genericValues = constructorValues.getGenericArgumentValues();
      if (!genericValues.isEmpty()) {
         genericValues.forEach(
            valueHolder -> {
               String valueName = valueHolder.getName();
               CodeBlock valueCode = this.generateValue(valueName, valueHolder.getValue());
               if (valueName != null) {
                  CodeBlock valueTypeCode = this.valueCodeGenerator.generateCode(valueHolder.getType());
                  code.addStatement(
                     "$L.getConstructorArgumentValues().addGenericArgumentValue(new $T($L, $L, $S))",
                     new Object[]{"beanDefinition", ConstructorArgumentValues.ValueHolder.class, valueCode, valueTypeCode, valueName}
                  );
               } else if (valueHolder.getType() != null) {
                  code.addStatement(
                     "$L.getConstructorArgumentValues().addGenericArgumentValue($L, $S)", new Object[]{"beanDefinition", valueCode, valueHolder.getType()}
                  );
               } else {
                  code.addStatement("$L.getConstructorArgumentValues().addGenericArgumentValue($L)", new Object[]{"beanDefinition", valueCode});
               }
            }
         );
      }
   }

   private void addPropertyValues(Builder code, RootBeanDefinition beanDefinition) {
      MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
      if (!propertyValues.isEmpty()) {
         Class<?> infrastructureType = this.getInfrastructureType(beanDefinition);
         Map<String, Method> writeMethods = infrastructureType != Object.class ? this.getWriteMethods(infrastructureType) : Collections.emptyMap();

         for (PropertyValue propertyValue : propertyValues) {
            String name = propertyValue.getName();
            CodeBlock valueCode = this.generateValue(name, propertyValue.getValue());
            code.addStatement("$L.getPropertyValues().addPropertyValue($S, $L)", new Object[]{"beanDefinition", name, valueCode});
            Method writeMethod = writeMethods.get(name);
            if (writeMethod != null) {
               this.registerReflectionHints(beanDefinition, writeMethod);
            }
         }
      }
   }

   private void registerReflectionHints(RootBeanDefinition beanDefinition, Method writeMethod) {
      this.hints.reflection().registerMethod(writeMethod, ExecutableMode.INVOKE);

      for (Class<?> searchType = beanDefinition.getTargetType();
         searchType != null && searchType != writeMethod.getDeclaringClass();
         searchType = searchType.getSuperclass()
      ) {
         this.hints.reflection().registerType(searchType, new MemberCategory[]{MemberCategory.ACCESS_DECLARED_FIELDS});
      }

      this.hints.reflection().registerType(writeMethod.getDeclaringClass(), new MemberCategory[]{MemberCategory.ACCESS_DECLARED_FIELDS});
   }

   private void addQualifiers(Builder code, RootBeanDefinition beanDefinition) {
      Set<AutowireCandidateQualifier> qualifiers = beanDefinition.getQualifiers();
      if (!qualifiers.isEmpty()) {
         for (AutowireCandidateQualifier qualifier : qualifiers) {
            Collection<CodeBlock> arguments = new ArrayList<>();
            arguments.add(CodeBlock.of("$S", new Object[]{qualifier.getTypeName()}));
            Object qualifierValue = qualifier.getAttribute("value");
            if (qualifierValue != null) {
               arguments.add(this.generateValue("value", qualifierValue));
            }

            code.addStatement("$L.addQualifier(new $T($L))", new Object[]{"beanDefinition", AutowireCandidateQualifier.class, CodeBlock.join(arguments, ", ")});
         }
      }
   }

   private void addMethodOverrides(Builder code, RootBeanDefinition beanDefinition) {
      if (beanDefinition.hasMethodOverrides()) {
         for (MethodOverride methodOverride : beanDefinition.getMethodOverrides().getOverrides()) {
            if (methodOverride instanceof LookupOverride lookupOverride) {
               Collection<CodeBlock> arguments = new ArrayList<>();
               arguments.add(CodeBlock.of("$S", new Object[]{lookupOverride.getMethodName()}));
               arguments.add(CodeBlock.of("$S", new Object[]{lookupOverride.getBeanName()}));
               code.addStatement(
                  "$L.getMethodOverrides().addOverride(new $T($L))", new Object[]{"beanDefinition", LookupOverride.class, CodeBlock.join(arguments, ", ")}
               );
            } else {
               if (!(methodOverride instanceof ReplaceOverride replaceOverride)) {
                  throw new UnsupportedOperationException("Unexpected MethodOverride subclass: " + methodOverride.getClass().getName());
               }

               Collection<CodeBlock> arguments = new ArrayList<>();
               arguments.add(CodeBlock.of("$S", new Object[]{replaceOverride.getMethodName()}));
               arguments.add(CodeBlock.of("$S", new Object[]{replaceOverride.getMethodReplacerBeanName()}));
               List<String> typeIdentifiers = replaceOverride.getTypeIdentifiers();
               if (!typeIdentifiers.isEmpty()) {
                  arguments.add(CodeBlock.of("java.util.List.of($S)", new Object[]{StringUtils.collectionToDelimitedString(typeIdentifiers, ", ")}));
               }

               code.addStatement(
                  "$L.getMethodOverrides().addOverride(new $T($L))", new Object[]{"beanDefinition", ReplaceOverride.class, CodeBlock.join(arguments, ", ")}
               );
            }
         }
      }
   }

   private CodeBlock generateValue(@Nullable String name, @Nullable Object value) {
      BeanDefinitionPropertiesCodeGenerator.PropertyNamesStack.push(name);

      try {
         return this.valueCodeGenerator.generateCode(value);
      } finally {
         BeanDefinitionPropertiesCodeGenerator.PropertyNamesStack.pop();
      }
   }

   private Class<?> getInfrastructureType(RootBeanDefinition beanDefinition) {
      if (beanDefinition.hasBeanClass()) {
         Class<?> beanClass = beanDefinition.getBeanClass();
         if (FactoryBean.class.isAssignableFrom(beanClass)) {
            return beanClass;
         }
      }

      return ClassUtils.getUserClass(beanDefinition.getResolvableType().toClass());
   }

   private Map<String, Method> getWriteMethods(Class<?> clazz) {
      Map<String, Method> writeMethods = new HashMap<>();

      for (PropertyDescriptor propertyDescriptor : BeanUtils.getPropertyDescriptors(clazz)) {
         writeMethods.put(propertyDescriptor.getName(), propertyDescriptor.getWriteMethod());
      }

      return Collections.unmodifiableMap(writeMethods);
   }

   private void addAttributes(Builder code, BeanDefinition beanDefinition) {
      String[] attributeNames = beanDefinition.attributeNames();
      if (!ObjectUtils.isEmpty(attributeNames)) {
         for (String attributeName : attributeNames) {
            if (this.attributeFilter.test(attributeName)) {
               CodeBlock value = this.valueCodeGenerator.generateCode(beanDefinition.getAttribute(attributeName));
               code.addStatement("$L.setAttribute($S, $L)", new Object[]{"beanDefinition", attributeName, value});
            }
         }
      }
   }

   private boolean hasScope(String defaultValue, String actualValue) {
      return StringUtils.hasText(actualValue) && !"singleton".equals(actualValue);
   }

   private boolean hasDependsOn(String[] defaultValue, String[] actualValue) {
      return !ObjectUtils.isEmpty(actualValue);
   }

   private boolean hasRole(int defaultValue, int actualValue) {
      return actualValue != 0;
   }

   private CodeBlock toStringVarArgs(String[] strings) {
      return (CodeBlock)Arrays.stream(strings).map(string -> CodeBlock.of("$S", new Object[]{string})).collect(CodeBlock.joining(","));
   }

   private Object toRole(int value) {
      return switch (value) {
         case 1 -> CodeBlock.builder().add("$T.ROLE_SUPPORT", new Object[]{BeanDefinition.class}).build();
         case 2 -> CodeBlock.builder().add("$T.ROLE_INFRASTRUCTURE", new Object[]{BeanDefinition.class}).build();
         default -> value;
      };
   }

   private <B extends BeanDefinition, T> void addStatementForValue(Builder code, BeanDefinition beanDefinition, Function<B, @Nullable T> getter, String format) {
      this.addStatementForValue(code, beanDefinition, getter, (defaultValue, actualValue) -> !Objects.equals(defaultValue, actualValue), format);
   }

   private <B extends BeanDefinition, T> void addStatementForValue(
      Builder code, BeanDefinition beanDefinition, Function<B, @Nullable T> getter, BiPredicate<T, T> filter, String format
   ) {
      this.addStatementForValue(code, beanDefinition, getter, filter, format, actualValue -> actualValue);
   }

   private <B extends BeanDefinition, T> void addStatementForValue(
      Builder code, BeanDefinition beanDefinition, Function<B, @Nullable T> getter, BiPredicate<T, T> filter, String format, Function<T, Object> formatter
   ) {
      T defaultValue = getter.apply((B)DEFAULT_BEAN_DEFINITION);
      T actualValue = getter.apply((B)beanDefinition);
      if (filter.test(defaultValue, actualValue)) {
         code.addStatement(format, new Object[]{"beanDefinition", formatter.apply(actualValue)});
      }
   }

   private CodeBlock castIfNecessary(boolean castNecessary, Class<?> castType, CodeBlock valueCode) {
      return castNecessary ? CodeBlock.of("($T) $L", new Object[]{castType, valueCode}) : valueCode;
   }

   static class PropertyNamesStack {
      private static final ThreadLocal<ArrayDeque<String>> threadLocal = ThreadLocal.withInitial(ArrayDeque::new);

      static void push(@Nullable String name) {
         String valueToSet = name != null ? name : "";
         threadLocal.get().push(valueToSet);
      }

      static void pop() {
         threadLocal.get().pop();
      }

      static @Nullable String peek() {
         String value = threadLocal.get().peek();
         return "".equals(value) ? null : value;
      }
   }
}
