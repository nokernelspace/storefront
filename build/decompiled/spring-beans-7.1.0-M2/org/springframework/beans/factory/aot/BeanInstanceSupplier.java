package org.springframework.beans.factory.aot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionValueResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.ThrowingBiFunction;
import org.springframework.util.function.ThrowingFunction;
import org.springframework.util.function.ThrowingSupplier;

public final class BeanInstanceSupplier<T> extends AutowiredElementResolver implements InstanceSupplier<T> {
   private final BeanInstanceSupplier.ExecutableLookup lookup;
   private final @Nullable ThrowingFunction<RegisteredBean, T> generatorWithoutArguments;
   private final @Nullable ThrowingBiFunction<RegisteredBean, AutowiredArguments, T> generatorWithArguments;
   private final String @Nullable [] shortcutBeanNames;

   private BeanInstanceSupplier(
      BeanInstanceSupplier.ExecutableLookup lookup,
      @Nullable ThrowingFunction<RegisteredBean, T> generatorWithoutArguments,
      @Nullable ThrowingBiFunction<RegisteredBean, AutowiredArguments, T> generatorWithArguments,
      String @Nullable [] shortcutBeanNames
   ) {
      this.lookup = lookup;
      this.generatorWithoutArguments = generatorWithoutArguments;
      this.generatorWithArguments = generatorWithArguments;
      this.shortcutBeanNames = shortcutBeanNames;
   }

   public static <T> BeanInstanceSupplier<T> forConstructor(Class<?>... parameterTypes) {
      Assert.notNull(parameterTypes, "'parameterTypes' must not be null");
      Assert.noNullElements(parameterTypes, "'parameterTypes' must not contain null elements");
      return new BeanInstanceSupplier<>(new BeanInstanceSupplier.ConstructorLookup(parameterTypes), null, null, null);
   }

   public static <T> BeanInstanceSupplier<T> forFactoryMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
      Assert.notNull(declaringClass, "'declaringClass' must not be null");
      Assert.hasText(methodName, "'methodName' must not be empty");
      Assert.notNull(parameterTypes, "'parameterTypes' must not be null");
      Assert.noNullElements(parameterTypes, "'parameterTypes' must not contain null elements");
      return new BeanInstanceSupplier<>(new BeanInstanceSupplier.FactoryMethodLookup(declaringClass, methodName, parameterTypes), null, null, null);
   }

   BeanInstanceSupplier.ExecutableLookup getLookup() {
      return this.lookup;
   }

   public BeanInstanceSupplier<T> withGenerator(ThrowingBiFunction<RegisteredBean, AutowiredArguments, T> generator) {
      Assert.notNull(generator, "'generator' must not be null");
      return new BeanInstanceSupplier<>(this.lookup, null, generator, this.shortcutBeanNames);
   }

   public BeanInstanceSupplier<T> withGenerator(ThrowingFunction<RegisteredBean, T> generator) {
      Assert.notNull(generator, "'generator' must not be null");
      return new BeanInstanceSupplier<>(this.lookup, generator, null, this.shortcutBeanNames);
   }

   public BeanInstanceSupplier<T> withShortcut(String... beanNames) {
      return new BeanInstanceSupplier<>(this.lookup, this.generatorWithoutArguments, this.generatorWithArguments, beanNames);
   }

   @Override
   public T get(RegisteredBean registeredBean) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      if (this.generatorWithoutArguments != null) {
         Executable executable = this.getFactoryMethodForGenerator();
         return this.invokeBeanSupplier(executable, () -> this.generatorWithoutArguments.apply(registeredBean));
      } else if (this.generatorWithArguments != null) {
         Executable executable = this.getFactoryMethodForGenerator();
         AutowiredArguments arguments = this.resolveArguments(registeredBean, executable != null ? executable : this.lookup.get(registeredBean));
         return this.invokeBeanSupplier(executable, () -> this.generatorWithArguments.apply(registeredBean, arguments));
      } else {
         Executable executable = this.lookup.get(registeredBean);
         Object[] arguments = this.resolveArguments(registeredBean, executable).toArray();
         return this.invokeBeanSupplier(executable, () -> this.instantiate(registeredBean, executable, arguments));
      }
   }

   @Override
   public @Nullable Method getFactoryMethod() {
      return this.lookup instanceof BeanInstanceSupplier.FactoryMethodLookup factoryMethodLookup ? factoryMethodLookup.get() : null;
   }

   private @Nullable Method getFactoryMethodForGenerator() {
      return this.lookup instanceof BeanInstanceSupplier.FactoryMethodLookup factoryMethodLookup && factoryMethodLookup.declaringClass.getName().contains("$$")
         ? factoryMethodLookup.get()
         : null;
   }

   private T invokeBeanSupplier(@Nullable Executable executable, ThrowingSupplier<T> beanSupplier) {
      return (T)(executable instanceof Method method ? SimpleInstantiationStrategy.instantiateWithFactoryMethod(method, beanSupplier) : beanSupplier.get());
   }

   AutowiredArguments resolveArguments(RegisteredBean registeredBean) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      return this.resolveArguments(registeredBean, this.lookup.get(registeredBean));
   }

   private AutowiredArguments resolveArguments(RegisteredBean registeredBean, Executable executable) {
      int parameterCount = executable.getParameterCount();
      Object[] resolved = new Object[parameterCount];
      Assert.isTrue(
         this.shortcutBeanNames == null || this.shortcutBeanNames.length == resolved.length, () -> "'shortcuts' must contain " + resolved.length + " elements"
      );
      ConstructorArgumentValues.ValueHolder[] argumentValues = this.resolveArgumentValues(registeredBean, executable);
      Set<String> autowiredBeanNames = new LinkedHashSet<>(resolved.length * 2);
      int startIndex = executable instanceof Constructor<?> constructor && ClassUtils.isInnerClass(constructor.getDeclaringClass()) ? 1 : 0;

      for (int i = startIndex; i < parameterCount; i++) {
         MethodParameter parameter = this.getMethodParameter(executable, i);
         DependencyDescriptor descriptor = new DependencyDescriptor(parameter, true);
         String shortcut = this.shortcutBeanNames != null ? this.shortcutBeanNames[i] : null;
         if (shortcut != null) {
            descriptor = new AutowiredElementResolver.ShortcutDependencyDescriptor(descriptor, shortcut);
         }

         ConstructorArgumentValues.ValueHolder argumentValue = argumentValues[i];
         resolved[i] = this.resolveAutowiredArgument(registeredBean, descriptor, argumentValue, autowiredBeanNames);
      }

      this.registerDependentBeans(registeredBean.getBeanFactory(), registeredBean.getBeanName(), autowiredBeanNames);
      return AutowiredArguments.of(resolved);
   }

   private MethodParameter getMethodParameter(Executable executable, int index) {
      if (executable instanceof Constructor<?> constructor) {
         return new MethodParameter(constructor, index);
      } else if (executable instanceof Method method) {
         return new MethodParameter(method, index);
      } else {
         throw new IllegalStateException("Unsupported executable: " + executable.getClass().getName());
      }
   }

   private ConstructorArgumentValues.ValueHolder[] resolveArgumentValues(RegisteredBean registeredBean, Executable executable) {
      Parameter[] parameters = executable.getParameters();
      ConstructorArgumentValues.ValueHolder[] resolved = new ConstructorArgumentValues.ValueHolder[parameters.length];
      RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
      if (beanDefinition.hasConstructorArgumentValues() && registeredBean.getBeanFactory() instanceof AbstractAutowireCapableBeanFactory beanFactory) {
         BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(
            beanFactory, registeredBean.getBeanName(), beanDefinition, beanFactory.getTypeConverter()
         );
         ConstructorArgumentValues values = this.resolveConstructorArguments(valueResolver, beanDefinition.getConstructorArgumentValues());
         Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = CollectionUtils.newHashSet(parameters.length);

         for (int i = 0; i < parameters.length; i++) {
            Class<?> parameterType = parameters[i].getType();
            String parameterName = parameters[i].isNamePresent() ? parameters[i].getName() : null;
            ConstructorArgumentValues.ValueHolder valueHolder = values.getArgumentValue(i, parameterType, parameterName, usedValueHolders);
            if (valueHolder != null) {
               resolved[i] = valueHolder;
               usedValueHolders.add(valueHolder);
            }
         }
      }

      return resolved;
   }

   private ConstructorArgumentValues resolveConstructorArguments(BeanDefinitionValueResolver valueResolver, ConstructorArgumentValues constructorArguments) {
      ConstructorArgumentValues resolvedConstructorArguments = new ConstructorArgumentValues();

      for (Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : constructorArguments.getIndexedArgumentValues().entrySet()) {
         resolvedConstructorArguments.addIndexedArgumentValue(entry.getKey(), this.resolveArgumentValue(valueResolver, entry.getValue()));
      }

      for (ConstructorArgumentValues.ValueHolder valueHolder : constructorArguments.getGenericArgumentValues()) {
         resolvedConstructorArguments.addGenericArgumentValue(this.resolveArgumentValue(valueResolver, valueHolder));
      }

      return resolvedConstructorArguments;
   }

   private ConstructorArgumentValues.ValueHolder resolveArgumentValue(BeanDefinitionValueResolver resolver, ConstructorArgumentValues.ValueHolder valueHolder) {
      if (valueHolder.isConverted()) {
         return valueHolder;
      }

      Object value = resolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
      ConstructorArgumentValues.ValueHolder resolvedHolder = new ConstructorArgumentValues.ValueHolder(value, valueHolder.getType(), valueHolder.getName());
      resolvedHolder.setSource(valueHolder);
      return resolvedHolder;
   }

   private @Nullable Object resolveAutowiredArgument(
      RegisteredBean registeredBean,
      DependencyDescriptor descriptor,
      ConstructorArgumentValues.@Nullable ValueHolder argumentValue,
      Set<String> autowiredBeanNames
   ) {
      TypeConverter typeConverter = registeredBean.getBeanFactory().getTypeConverter();
      if (argumentValue != null) {
         return argumentValue.isConverted()
            ? argumentValue.getConvertedValue()
            : typeConverter.convertIfNecessary(argumentValue.getValue(), descriptor.getDependencyType(), descriptor.getMethodParameter());
      }

      try {
         return registeredBean.resolveAutowiredArgument(descriptor, typeConverter, autowiredBeanNames);
      } catch (BeansException ex) {
         throw new UnsatisfiedDependencyException(null, registeredBean.getBeanName(), descriptor, ex);
      }
   }

   private Object instantiate(RegisteredBean registeredBean, Executable executable, @Nullable Object[] args) {
      if (executable instanceof Constructor<?> constructor) {
         return registeredBean.getBeanFactory() instanceof DefaultListableBeanFactory dlbf && registeredBean.getMergedBeanDefinition().hasMethodOverrides()
            ? dlbf.getInstantiationStrategy()
               .instantiate(registeredBean.getMergedBeanDefinition(), registeredBean.getBeanName(), registeredBean.getBeanFactory())
            : BeanUtils.instantiateClass(constructor, args);
      } else if (executable instanceof Method method) {
         Object target = null;
         String factoryBeanName = registeredBean.getMergedBeanDefinition().getFactoryBeanName();
         if (factoryBeanName != null) {
            target = registeredBean.getBeanFactory().getBean(factoryBeanName, method.getDeclaringClass());
         } else if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("Cannot invoke instance method without factoryBeanName: " + method);
         }

         try {
            ReflectionUtils.makeAccessible(method);
            return method.invoke(target, args);
         } catch (Throwable ex) {
            throw new BeanInstantiationException(method, ex.getMessage(), ex);
         }
      } else {
         throw new IllegalStateException("Unsupported executable " + executable.getClass().getName());
      }
   }

   private static String toCommaSeparatedNames(Class<?>... parameterTypes) {
      return Arrays.stream(parameterTypes).map(Class::getName).collect(Collectors.joining(", "));
   }

   private static class ConstructorLookup extends BeanInstanceSupplier.ExecutableLookup {
      private final Class<?>[] parameterTypes;

      ConstructorLookup(Class<?>[] parameterTypes) {
         this.parameterTypes = parameterTypes;
      }

      @Override
      public Executable get(RegisteredBean registeredBean) {
         Class<?> beanClass = registeredBean.getMergedBeanDefinition().getBeanClass();

         try {
            return beanClass.getDeclaredConstructor(this.parameterTypes);
         } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("%s cannot be found on %s".formatted(this, beanClass.getName()), ex);
         }
      }

      @Override
      public String toString() {
         return "Constructor with parameter types [%s]".formatted(BeanInstanceSupplier.toCommaSeparatedNames(this.parameterTypes));
      }
   }

   abstract static class ExecutableLookup {
      abstract Executable get(RegisteredBean registeredBean);
   }

   private static class FactoryMethodLookup extends BeanInstanceSupplier.ExecutableLookup {
      private final Class<?> declaringClass;
      private final String methodName;
      private final Class<?>[] parameterTypes;
      private volatile @Nullable Method resolvedMethod;

      FactoryMethodLookup(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
         this.declaringClass = declaringClass;
         this.methodName = methodName;
         this.parameterTypes = parameterTypes;
      }

      @Override
      public Executable get(RegisteredBean registeredBean) {
         return this.get();
      }

      Method get() {
         Method method = this.resolvedMethod;
         if (method == null) {
            method = ReflectionUtils.findMethod(ClassUtils.getUserClass(this.declaringClass), this.methodName, this.parameterTypes);
            Assert.notNull(method, () -> "%s cannot be found".formatted(this));
            this.resolvedMethod = method;
         }

         return method;
      }

      @Override
      public String toString() {
         return "Factory method '%s' with parameter types [%s] declared on %s"
            .formatted(this.methodName, BeanInstanceSupplier.toCommaSeparatedNames(this.parameterTypes), this.declaringClass);
      }
   }
}
