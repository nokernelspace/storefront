package org.springframework.beans.factory.aot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.AccessControl;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.ValueCodeGenerator;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.generate.ValueCodeGenerator.Delegate;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.ParameterizedTypeName;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.SingletonSupplier;

class DefaultBeanRegistrationCodeFragments implements BeanRegistrationCodeFragments {
   private static final ValueCodeGenerator valueCodeGenerator = ValueCodeGenerator.withDefaults();
   private final BeanRegistrationsCode beanRegistrationsCode;
   private final RegisteredBean registeredBean;
   private final BeanDefinitionMethodGeneratorFactory beanDefinitionMethodGeneratorFactory;
   private final Supplier<RegisteredBean.InstantiationDescriptor> instantiationDescriptor;

   DefaultBeanRegistrationCodeFragments(
      BeanRegistrationsCode beanRegistrationsCode, RegisteredBean registeredBean, BeanDefinitionMethodGeneratorFactory beanDefinitionMethodGeneratorFactory
   ) {
      this.beanRegistrationsCode = beanRegistrationsCode;
      this.registeredBean = registeredBean;
      this.beanDefinitionMethodGeneratorFactory = beanDefinitionMethodGeneratorFactory;
      this.instantiationDescriptor = SingletonSupplier.of(registeredBean::resolveInstantiationDescriptor);
   }

   @Override
   public ClassName getTarget(RegisteredBean registeredBean) {
      if (this.hasInstanceSupplier()) {
         throw new AotBeanProcessingException(registeredBean, "instance supplier is not supported");
      }

      Class<?> target = this.extractDeclaringClass(registeredBean, this.instantiationDescriptor.get());

      while (target.getName().startsWith("java.") && registeredBean.isInnerBean()) {
         RegisteredBean parent = registeredBean.getParent();
         Assert.state(parent != null, "No parent available for inner bean");
         target = parent.getBeanClass();
      }

      return target.isArray() ? ClassName.get(target.getComponentType()) : ClassName.get(target);
   }

   private Class<?> extractDeclaringClass(RegisteredBean registeredBean, RegisteredBean.InstantiationDescriptor instantiationDescriptor) {
      Class<?> declaringClass = ClassUtils.getUserClass(instantiationDescriptor.targetClass());
      return instantiationDescriptor.executable() instanceof Constructor<?> ctor
            && AccessControl.forMember(ctor).isPublic()
            && FactoryBean.class.isAssignableFrom(declaringClass)
         ? this.extractTargetClassFromFactoryBean(declaringClass, registeredBean.getBeanType())
         : declaringClass;
   }

   private Class<?> extractTargetClassFromFactoryBean(Class<?> factoryBeanType, ResolvableType beanType) {
      ResolvableType target = ResolvableType.forType(factoryBeanType).as(FactoryBean.class).getGeneric(new int[]{0});
      if (target.getType().equals(Class.class)) {
         return target.toClass();
      } else {
         return factoryBeanType.isAssignableFrom(beanType.toClass()) ? beanType.as(FactoryBean.class).getGeneric(new int[]{0}).toClass() : beanType.toClass();
      }
   }

   @Override
   public CodeBlock generateNewBeanDefinitionCode(GenerationContext generationContext, ResolvableType beanType, BeanRegistrationCode beanRegistrationCode) {
      Builder code = CodeBlock.builder();
      RootBeanDefinition mbd = this.registeredBean.getMergedBeanDefinition();
      Class<?> beanClass = mbd.hasBeanClass() ? ClassUtils.getUserClass(mbd.getBeanClass()) : null;
      CodeBlock beanClassCode = this.generateBeanClassCode(
         beanRegistrationCode.getClassName().packageName(), beanClass != null ? beanClass : beanType.toClass()
      );
      code.addStatement("$T $L = new $T($L)", new Object[]{RootBeanDefinition.class, "beanDefinition", RootBeanDefinition.class, beanClassCode});
      if (this.targetTypeNecessary(beanType, beanClass)) {
         code.addStatement("$L.setTargetType($L)", new Object[]{"beanDefinition", this.generateBeanTypeCode(beanType)});
      }

      return code.build();
   }

   private CodeBlock generateBeanClassCode(String targetPackage, Class<?> beanClass) {
      return !Modifier.isPublic(beanClass.getModifiers()) && !targetPackage.equals(beanClass.getPackageName())
         ? CodeBlock.of("$S", new Object[]{beanClass.getName()})
         : CodeBlock.of("$T.class", new Object[]{beanClass});
   }

   private CodeBlock generateBeanTypeCode(ResolvableType beanType) {
      return !beanType.hasGenerics() ? valueCodeGenerator.generateCode(ClassUtils.getUserClass(beanType.toClass())) : valueCodeGenerator.generateCode(beanType);
   }

   private boolean targetTypeNecessary(ResolvableType beanType, @Nullable Class<?> beanClass) {
      if (beanType.hasGenerics()) {
         return true;
      } else {
         return beanClass != null && this.registeredBean.getMergedBeanDefinition().getFactoryMethodName() != null
            ? true
            : beanClass != null && !beanType.toClass().equals(ClassUtils.getUserClass(beanClass));
      }
   }

   @Override
   public CodeBlock generateSetBeanDefinitionPropertiesCode(
      GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition, Predicate<String> attributeFilter
   ) {
      AotServices.Loader loader = AotServices.factories(this.registeredBean.getBeanFactory().getBeanClassLoader());
      List<Delegate> additionalDelegates = loader.load(Delegate.class).asList();
      return new BeanDefinitionPropertiesCodeGenerator(
            generationContext.getRuntimeHints(),
            attributeFilter,
            beanRegistrationCode.getMethods(),
            additionalDelegates,
            (name, value) -> this.generateValueCode(generationContext, name, value)
         )
         .generateCode(beanDefinition);
   }

   protected @Nullable CodeBlock generateValueCode(GenerationContext generationContext, String name, Object value) {
      RegisteredBean innerRegisteredBean = this.getInnerRegisteredBean(value);
      if (innerRegisteredBean != null) {
         BeanDefinitionMethodGenerator methodGenerator = this.beanDefinitionMethodGeneratorFactory.getBeanDefinitionMethodGenerator(innerRegisteredBean, name);
         Assert.state(methodGenerator != null, "Unexpected filtering of inner-bean");
         MethodReference generatedMethod = methodGenerator.generateBeanDefinitionMethod(generationContext, this.beanRegistrationsCode);
         return generatedMethod.toInvokeCodeBlock(ArgumentCodeGenerator.none());
      } else {
         return null;
      }
   }

   private @Nullable RegisteredBean getInnerRegisteredBean(Object value) {
      if (value instanceof BeanDefinitionHolder beanDefinitionHolder) {
         return RegisteredBean.ofInnerBean(this.registeredBean, beanDefinitionHolder);
      } else {
         return value instanceof BeanDefinition beanDefinition ? RegisteredBean.ofInnerBean(this.registeredBean, beanDefinition) : null;
      }
   }

   @Override
   public CodeBlock generateSetBeanInstanceSupplierCode(
      GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, CodeBlock instanceSupplierCode, List<MethodReference> postProcessors
   ) {
      Builder code = CodeBlock.builder();
      if (postProcessors.isEmpty()) {
         code.addStatement("$L.setInstanceSupplier($L)", new Object[]{"beanDefinition", instanceSupplierCode});
         return code.build();
      }

      code.addStatement(
         "$T $L = $L",
         new Object[]{
            ParameterizedTypeName.get(InstanceSupplier.class, new Type[]{this.registeredBean.getBeanClass()}), "instanceSupplier", instanceSupplierCode
         }
      );

      for (MethodReference postProcessor : postProcessors) {
         code.addStatement("$L = $L.andThen($L)", new Object[]{"instanceSupplier", "instanceSupplier", postProcessor.toCodeBlock()});
      }

      code.addStatement("$L.setInstanceSupplier($L)", new Object[]{"beanDefinition", "instanceSupplier"});
      return code.build();
   }

   @Override
   public CodeBlock generateInstanceSupplierCode(
      GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, boolean allowDirectSupplierShortcut
   ) {
      if (this.hasInstanceSupplier()) {
         throw new AotBeanProcessingException(this.registeredBean, "instance supplier is not supported");
      } else {
         return new InstanceSupplierCodeGenerator(
               generationContext, beanRegistrationCode.getClassName(), beanRegistrationCode.getMethods(), allowDirectSupplierShortcut
            )
            .generateCode(this.registeredBean, this.instantiationDescriptor.get());
      }
   }

   @Override
   public CodeBlock generateReturnCode(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
      Builder code = CodeBlock.builder();
      code.addStatement("return $L", new Object[]{"beanDefinition"});
      return code.build();
   }

   private boolean hasInstanceSupplier() {
      return this.registeredBean.getMergedBeanDefinition().getInstanceSupplier() != null;
   }
}
