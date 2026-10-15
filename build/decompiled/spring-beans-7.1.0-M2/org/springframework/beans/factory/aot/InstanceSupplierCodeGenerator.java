package org.springframework.beans.factory.aot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.lang.model.element.Modifier;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.AccessControl;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.AccessControl.Visibility;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.ParameterizedTypeName;
import org.springframework.javapoet.MethodSpec.Builder;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.ThrowingSupplier;

public class InstanceSupplierCodeGenerator {
   private static final String REGISTERED_BEAN_PARAMETER_NAME = "registeredBean";
   private static final String ARGS_PARAMETER_NAME = "args";
   private static final Modifier[] PRIVATE_STATIC = new Modifier[]{Modifier.PRIVATE, Modifier.STATIC};
   private static final CodeBlock NO_ARGS = CodeBlock.of("", new Object[0]);
   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();
   private final GenerationContext generationContext;
   private final ClassName className;
   private final GeneratedMethods generatedMethods;
   private final boolean allowDirectSupplierShortcut;

   public InstanceSupplierCodeGenerator(
      GenerationContext generationContext, ClassName className, GeneratedMethods generatedMethods, boolean allowDirectSupplierShortcut
   ) {
      this.generationContext = generationContext;
      this.className = className;
      this.generatedMethods = generatedMethods;
      this.allowDirectSupplierShortcut = allowDirectSupplierShortcut;
   }

   @Deprecated(since = "6.1.7")
   public CodeBlock generateCode(RegisteredBean registeredBean, Executable constructorOrFactoryMethod) {
      return this.generateCode(
         registeredBean, new RegisteredBean.InstantiationDescriptor(constructorOrFactoryMethod, constructorOrFactoryMethod.getDeclaringClass())
      );
   }

   public CodeBlock generateCode(RegisteredBean registeredBean, RegisteredBean.InstantiationDescriptor instantiationDescriptor) {
      Executable constructorOrFactoryMethod = instantiationDescriptor.executable();
      this.registerRuntimeHintsIfNecessary(registeredBean, constructorOrFactoryMethod);
      if (constructorOrFactoryMethod instanceof Constructor<?> constructor) {
         return this.generateCodeForConstructor(registeredBean, constructor);
      } else if (constructorOrFactoryMethod instanceof Method method && !KotlinDetector.isSuspendingFunction(method)) {
         return this.generateCodeForFactoryMethod(registeredBean, method, instantiationDescriptor.targetClass());
      } else {
         throw new AotBeanProcessingException(registeredBean, "no suitable constructor or factory method found");
      }
   }

   private void registerRuntimeHintsIfNecessary(RegisteredBean registeredBean, Executable constructorOrFactoryMethod) {
      if (registeredBean.getBeanFactory() instanceof DefaultListableBeanFactory dlbf) {
         RuntimeHints runtimeHints = this.generationContext.getRuntimeHints();
         InstanceSupplierCodeGenerator.ProxyRuntimeHintsRegistrar registrar = new InstanceSupplierCodeGenerator.ProxyRuntimeHintsRegistrar(
            dlbf.getAutowireCandidateResolver()
         );
         registrar.registerRuntimeHints(runtimeHints, constructorOrFactoryMethod);
      }
   }

   private CodeBlock generateCodeForConstructor(RegisteredBean registeredBean, Constructor<?> constructor) {
      InstanceSupplierCodeGenerator.ConstructorDescriptor descriptor = new InstanceSupplierCodeGenerator.ConstructorDescriptor(
         registeredBean.getBeanName(), constructor, registeredBean.getBeanClass()
      );
      Class<?> publicType = descriptor.publicType();
      if (KOTLIN_REFLECT_PRESENT
         && KotlinDetector.isKotlinType(publicType)
         && InstanceSupplierCodeGenerator.KotlinDelegate.hasConstructorWithOptionalParameter(publicType)) {
         return this.generateCodeForInaccessibleConstructor(
            descriptor, hints -> hints.registerType(publicType, new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS})
         );
      } else {
         return this.isVisible(constructor, constructor.getDeclaringClass()) && !registeredBean.getMergedBeanDefinition().hasMethodOverrides()
            ? this.generateCodeForAccessibleConstructor(descriptor)
            : this.generateCodeForInaccessibleConstructor(descriptor, hints -> hints.registerConstructor(constructor, ExecutableMode.INVOKE));
      }
   }

   private CodeBlock generateCodeForAccessibleConstructor(InstanceSupplierCodeGenerator.ConstructorDescriptor descriptor) {
      Constructor<?> constructor = descriptor.constructor();
      this.generationContext.getRuntimeHints().reflection().registerType(constructor.getDeclaringClass(), new MemberCategory[0]);
      if (constructor.getParameterCount() == 0) {
         if (!this.allowDirectSupplierShortcut) {
            return CodeBlock.of("$T.using($T::new)", new Object[]{InstanceSupplier.class, descriptor.actualType()});
         } else {
            return !this.isThrowingCheckedException(constructor)
               ? CodeBlock.of("$T::new", new Object[]{descriptor.actualType()})
               : CodeBlock.of("$T.of($T::new)", new Object[]{ThrowingSupplier.class, descriptor.actualType()});
         }
      } else {
         GeneratedMethod generatedMethod = this.generateGetInstanceSupplierMethod(
            method -> this.buildGetInstanceMethodForConstructor(method, descriptor, PRIVATE_STATIC)
         );
         return this.generateReturnStatement(generatedMethod);
      }
   }

   private CodeBlock generateCodeForInaccessibleConstructor(InstanceSupplierCodeGenerator.ConstructorDescriptor descriptor, Consumer<ReflectionHints> hints) {
      Constructor<?> constructor = descriptor.constructor();
      CodeWarnings codeWarnings = new CodeWarnings();
      codeWarnings.detectDeprecation(constructor.getDeclaringClass(), constructor)
         .detectDeprecation(Arrays.stream(constructor.getParameters()).map(Parameter::getType));
      hints.accept(this.generationContext.getRuntimeHints().reflection());
      GeneratedMethod generatedMethod = this.generateGetInstanceSupplierMethod(method -> {
         method.addJavadoc("Get the bean instance supplier for '$L'.", new Object[]{descriptor.beanName()});
         method.addModifiers(PRIVATE_STATIC);
         codeWarnings.suppress(method);
         method.returns(ParameterizedTypeName.get(BeanInstanceSupplier.class, new Type[]{descriptor.publicType()}));
         method.addStatement(this.generateResolverForConstructor(descriptor));
      });
      return this.generateReturnStatement(generatedMethod);
   }

   private void buildGetInstanceMethodForConstructor(Builder method, InstanceSupplierCodeGenerator.ConstructorDescriptor descriptor, Modifier... modifiers) {
      Constructor<?> constructor = descriptor.constructor();
      Class<?> publicType = descriptor.publicType();
      Class<?> actualType = descriptor.actualType();
      CodeWarnings codeWarnings = new CodeWarnings();
      codeWarnings.detectDeprecation(actualType, constructor).detectDeprecation(Arrays.stream(constructor.getParameters()).map(Parameter::getType));
      method.addJavadoc("Get the bean instance supplier for '$L'.", new Object[]{descriptor.beanName()});
      method.addModifiers(modifiers);
      codeWarnings.suppress(method);
      method.returns(ParameterizedTypeName.get(BeanInstanceSupplier.class, new Type[]{publicType}));
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
      code.add(this.generateResolverForConstructor(descriptor));
      boolean hasArguments = constructor.getParameterCount() > 0;
      boolean onInnerClass = ClassUtils.isInnerClass(actualType);
      CodeBlock arguments = hasArguments
         ? new AutowiredArgumentsCodeGenerator(actualType, constructor).generateCode(constructor.getParameterTypes(), onInnerClass ? 1 : 0)
         : NO_ARGS;
      CodeBlock newInstance = this.generateNewInstanceCodeForConstructor(actualType, arguments);
      code.add(this.generateWithGeneratorCode(hasArguments, newInstance));
      method.addStatement(code.build());
   }

   private CodeBlock generateResolverForConstructor(InstanceSupplierCodeGenerator.ConstructorDescriptor descriptor) {
      CodeBlock parameterTypes = this.generateParameterTypesCode(descriptor.constructor().getParameterTypes());
      return CodeBlock.of("return $T.<$T>forConstructor($L)", new Object[]{BeanInstanceSupplier.class, descriptor.publicType(), parameterTypes});
   }

   private CodeBlock generateNewInstanceCodeForConstructor(Class<?> declaringClass, CodeBlock args) {
      return ClassUtils.isInnerClass(declaringClass)
         ? CodeBlock.of(
            "$L.getBeanFactory().getBean($T.class).new $L($L)",
            new Object[]{"registeredBean", declaringClass.getEnclosingClass(), declaringClass.getSimpleName(), args}
         )
         : CodeBlock.of("new $T($L)", new Object[]{declaringClass, args});
   }

   private CodeBlock generateCodeForFactoryMethod(RegisteredBean registeredBean, Method factoryMethod, Class<?> targetClass) {
      return !this.isVisible(factoryMethod, targetClass)
         ? this.generateCodeForInaccessibleFactoryMethod(registeredBean.getBeanName(), factoryMethod, targetClass)
         : this.generateCodeForAccessibleFactoryMethod(
            registeredBean.getBeanName(), factoryMethod, targetClass, registeredBean.getMergedBeanDefinition().getFactoryBeanName()
         );
   }

   private CodeBlock generateCodeForAccessibleFactoryMethod(String beanName, Method factoryMethod, Class<?> targetClass, @Nullable String factoryBeanName) {
      this.generationContext.getRuntimeHints().reflection().registerType(factoryMethod.getDeclaringClass(), new MemberCategory[0]);
      if (factoryBeanName == null && factoryMethod.getParameterCount() == 0) {
         Class<?> suppliedType = ClassUtils.resolvePrimitiveIfNecessary(factoryMethod.getReturnType());
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         code.add("$T.<$T>forFactoryMethod($T.class, $S)", new Object[]{BeanInstanceSupplier.class, suppliedType, targetClass, factoryMethod.getName()});
         code.add(".withGenerator(($L) -> $T.$L())", new Object[]{"registeredBean", ClassUtils.getUserClass(targetClass), factoryMethod.getName()});
         return code.build();
      } else {
         GeneratedMethod getInstanceMethod = this.generateGetInstanceSupplierMethod(
            method -> this.buildGetInstanceMethodForFactoryMethod(method, beanName, factoryMethod, targetClass, factoryBeanName, PRIVATE_STATIC)
         );
         return this.generateReturnStatement(getInstanceMethod);
      }
   }

   private CodeBlock generateCodeForInaccessibleFactoryMethod(String beanName, Method factoryMethod, Class<?> targetClass) {
      this.generationContext.getRuntimeHints().reflection().registerMethod(factoryMethod, ExecutableMode.INVOKE);
      GeneratedMethod getInstanceMethod = this.generateGetInstanceSupplierMethod(method -> {
         CodeWarnings codeWarnings = new CodeWarnings();
         Class<?> suppliedType = ClassUtils.resolvePrimitiveIfNecessary(factoryMethod.getReturnType());
         codeWarnings.detectDeprecation(suppliedType, factoryMethod);
         method.addJavadoc("Get the bean instance supplier for '$L'.", new Object[]{beanName});
         method.addModifiers(PRIVATE_STATIC);
         codeWarnings.suppress(method);
         method.returns(ParameterizedTypeName.get(BeanInstanceSupplier.class, new Type[]{suppliedType}));
         method.addStatement(this.generateInstanceSupplierForFactoryMethod(factoryMethod, suppliedType, targetClass, factoryMethod.getName()));
      });
      return this.generateReturnStatement(getInstanceMethod);
   }

   private void buildGetInstanceMethodForFactoryMethod(
      Builder method, String beanName, Method factoryMethod, Class<?> targetClass, @Nullable String factoryBeanName, Modifier... modifiers
   ) {
      String factoryMethodName = factoryMethod.getName();
      Class<?> suppliedType = ClassUtils.resolvePrimitiveIfNecessary(factoryMethod.getReturnType());
      CodeWarnings codeWarnings = new CodeWarnings();
      codeWarnings.detectDeprecation(ClassUtils.getUserClass(targetClass), factoryMethod, suppliedType)
         .detectDeprecation(Arrays.stream(factoryMethod.getParameters()).map(Parameter::getType));
      method.addJavadoc("Get the bean instance supplier for '$L'.", new Object[]{beanName});
      method.addModifiers(modifiers);
      codeWarnings.suppress(method);
      method.returns(ParameterizedTypeName.get(BeanInstanceSupplier.class, new Type[]{suppliedType}));
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
      code.add(this.generateInstanceSupplierForFactoryMethod(factoryMethod, suppliedType, targetClass, factoryMethodName));
      boolean hasArguments = factoryMethod.getParameterCount() > 0;
      CodeBlock arguments = hasArguments
         ? new AutowiredArgumentsCodeGenerator(ClassUtils.getUserClass(targetClass), factoryMethod).generateCode(factoryMethod.getParameterTypes())
         : NO_ARGS;
      CodeBlock newInstance = this.generateNewInstanceCodeForMethod(factoryBeanName, ClassUtils.getUserClass(targetClass), factoryMethodName, arguments);
      code.add(this.generateWithGeneratorCode(hasArguments, newInstance));
      method.addStatement(code.build());
   }

   private CodeBlock generateInstanceSupplierForFactoryMethod(Method factoryMethod, Class<?> suppliedType, Class<?> targetClass, String factoryMethodName) {
      if (factoryMethod.getParameterCount() == 0) {
         return CodeBlock.of(
            "return $T.<$T>forFactoryMethod($T.class, $S)", new Object[]{BeanInstanceSupplier.class, suppliedType, targetClass, factoryMethodName}
         );
      }

      CodeBlock parameterTypes = this.generateParameterTypesCode(factoryMethod.getParameterTypes());
      return CodeBlock.of(
         "return $T.<$T>forFactoryMethod($T.class, $S, $L)",
         new Object[]{BeanInstanceSupplier.class, suppliedType, targetClass, factoryMethodName, parameterTypes}
      );
   }

   private CodeBlock generateNewInstanceCodeForMethod(@Nullable String factoryBeanName, Class<?> targetClass, String factoryMethodName, CodeBlock args) {
      return factoryBeanName == null
         ? CodeBlock.of("$T.$L($L)", new Object[]{targetClass, factoryMethodName, args})
         : CodeBlock.of(
            "$L.getBeanFactory().getBean(\"$L\", $T.class).$L($L)", new Object[]{"registeredBean", factoryBeanName, targetClass, factoryMethodName, args}
         );
   }

   private CodeBlock generateReturnStatement(GeneratedMethod generatedMethod) {
      return generatedMethod.toMethodReference().toInvokeCodeBlock(ArgumentCodeGenerator.none(), this.className);
   }

   private CodeBlock generateWithGeneratorCode(boolean hasArguments, CodeBlock newInstance) {
      CodeBlock lambdaArguments = hasArguments
         ? CodeBlock.of("($L, $L)", new Object[]{"registeredBean", "args"})
         : CodeBlock.of("($L)", new Object[]{"registeredBean"});
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
      code.add("\n", new Object[0]);
      code.indent().indent();
      code.add(".withGenerator($L -> $L)", new Object[]{lambdaArguments, newInstance});
      code.unindent().unindent();
      return code.build();
   }

   private boolean isVisible(Member member, Class<?> targetClass) {
      AccessControl classAccessControl = AccessControl.forClass(targetClass);
      AccessControl memberAccessControl = AccessControl.forMember(member);
      Visibility visibility = AccessControl.lowest(new AccessControl[]{classAccessControl, memberAccessControl}).getVisibility();
      return visibility == Visibility.PUBLIC
         || visibility != Visibility.PRIVATE && member.getDeclaringClass().getPackageName().equals(this.className.packageName());
   }

   private CodeBlock generateParameterTypesCode(Class<?>[] parameterTypes) {
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();

      for (int i = 0; i < parameterTypes.length; i++) {
         code.add(i > 0 ? ", " : "", new Object[0]);
         code.add("$T.class", new Object[]{parameterTypes[i]});
      }

      return code.build();
   }

   private GeneratedMethod generateGetInstanceSupplierMethod(Consumer<Builder> method) {
      return this.generatedMethods.add("getInstanceSupplier", method);
   }

   private boolean isThrowingCheckedException(Executable executable) {
      return Arrays.stream(executable.getGenericExceptionTypes())
         .map(ResolvableType::forType)
         .<Class<?>>map(ResolvableType::toClass)
         .anyMatch(Exception.class::isAssignableFrom);
   }

   record ConstructorDescriptor(String beanName, Constructor<?> constructor, Class<?> publicType) {
      Class<?> actualType() {
         return this.constructor.getDeclaringClass();
      }
   }

   private static class KotlinDelegate {
      public static boolean hasConstructorWithOptionalParameter(Class<?> beanClass) {
         KClass<?> kClass = JvmClassMappingKt.getKotlinClass(beanClass);

         for (KFunction<?> constructor : kClass.getConstructors()) {
            for (KParameter parameter : constructor.getParameters()) {
               if (parameter.isOptional()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private record ProxyRuntimeHintsRegistrar(AutowireCandidateResolver candidateResolver) {
      public void registerRuntimeHints(RuntimeHints runtimeHints, Executable executable) {
         Class<?>[] parameterTypes = executable.getParameterTypes();

         for (int i = 0; i < parameterTypes.length; i++) {
            MethodParameter methodParam = MethodParameter.forExecutable(executable, i);
            DependencyDescriptor dependencyDescriptor = new DependencyDescriptor(methodParam, true);
            this.registerProxyIfNecessary(runtimeHints, dependencyDescriptor);
         }
      }

      private void registerProxyIfNecessary(RuntimeHints runtimeHints, DependencyDescriptor dependencyDescriptor) {
         Class<?> proxyType = this.candidateResolver.getLazyResolutionProxyClass(dependencyDescriptor, null);
         if (proxyType != null && Proxy.isProxyClass(proxyType)) {
            runtimeHints.proxies().registerJdkProxy(proxyType.getInterfaces());
         }
      }
   }
}
