package org.springframework.beans.factory.aot;

import java.util.List;
import java.util.function.BiConsumer;
import javax.lang.model.element.Modifier;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;

class BeanRegistrationsAotContribution implements BeanFactoryInitializationAotContribution {
   private static final String BEAN_FACTORY_PARAMETER_NAME = "beanFactory";
   private static final int MAX_REGISTRATIONS_PER_FILE = 5000;
   private static final int MAX_REGISTRATIONS_PER_METHOD = 1000;
   private static final ArgumentCodeGenerator argumentCodeGenerator = ArgumentCodeGenerator.of(DefaultListableBeanFactory.class, "beanFactory");
   private final List<BeanRegistrationsAotContribution.Registration> registrations;

   BeanRegistrationsAotContribution(List<BeanRegistrationsAotContribution.Registration> registrations) {
      this.registrations = registrations;
   }

   @Override
   public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
      GeneratedClass generatedClass = createBeanFactoryRegistrationClass(generationContext);
      BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator codeGenerator = new BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator(
         generatedClass
      );
      GeneratedMethod generatedBeanDefinitionsMethod = this.generateBeanRegistrationCode(generationContext, generatedClass, codeGenerator);
      beanFactoryInitializationCode.addInitializer(generatedBeanDefinitionsMethod.toMethodReference());
      GeneratedMethod generatedAliasesMethod = codeGenerator.getMethods().add("registerAliases", this::generateRegisterAliasesMethod);
      beanFactoryInitializationCode.addInitializer(generatedAliasesMethod.toMethodReference());
      this.generateRegisterHints(generationContext.getRuntimeHints(), this.registrations);
   }

   private GeneratedMethod generateBeanRegistrationCode(
      GenerationContext generationContext, GeneratedClass mainGeneratedClass, BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator mainCodeGenerator
   ) {
      return this.registrations.size() < 5000
         ? this.generateBeanRegistrationClass(generationContext, mainCodeGenerator, 0, this.registrations.size())
         : mainGeneratedClass.getMethods()
            .add(
               "registerBeanDefinitions",
               method -> {
                  method.addJavadoc("Register the bean definitions.", new Object[0]);
                  method.addModifiers(new Modifier[]{Modifier.PUBLIC});
                  method.addParameter(DefaultListableBeanFactory.class, "beanFactory", new Modifier[0]);
                  Builder body = CodeBlock.builder();
                  BeanRegistrationsAotContribution.Registration.doWithSlice(
                     this.registrations,
                     5000,
                     (start, end) -> {
                        GeneratedClass sliceGeneratedClass = createBeanFactoryRegistrationClass(generationContext);
                        BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator sliceCodeGenerator = new BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator(
                           sliceGeneratedClass
                        );
                        GeneratedMethod generatedMethod = this.generateBeanRegistrationClass(generationContext, sliceCodeGenerator, start, end);
                        body.addStatement(generatedMethod.toMethodReference().toInvokeCodeBlock(argumentCodeGenerator));
                     }
                  );
                  method.addCode(body.build());
               }
            );
   }

   private GeneratedMethod generateBeanRegistrationClass(
      GenerationContext generationContext, BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator codeGenerator, int start, int end
   ) {
      return codeGenerator.getMethods()
         .add(
            "registerBeanDefinitions",
            method -> {
               method.addJavadoc("Register the bean definitions.", new Object[0]);
               method.addModifiers(new Modifier[]{Modifier.PUBLIC});
               method.addParameter(DefaultListableBeanFactory.class, "beanFactory", new Modifier[0]);
               List<BeanRegistrationsAotContribution.Registration> sliceRegistrations = this.registrations.subList(start, end);
               new BeanRegistrationsAotContribution.BeanDefinitionsRegistrationGenerator(generationContext, codeGenerator, sliceRegistrations, start)
                  .generateBeanRegistrationsCode(method);
            }
         );
   }

   private static GeneratedClass createBeanFactoryRegistrationClass(GenerationContext generationContext) {
      return generationContext.getGeneratedClasses().addForFeature("BeanFactoryRegistrations", type -> {
         type.addJavadoc("Register bean definitions for the bean factory.", new Object[0]);
         type.addModifiers(new Modifier[]{Modifier.PUBLIC});
      });
   }

   private void generateRegisterAliasesMethod(org.springframework.javapoet.MethodSpec.Builder method) {
      method.addJavadoc("Register the aliases.", new Object[0]);
      method.addModifiers(new Modifier[]{Modifier.PUBLIC});
      method.addParameter(DefaultListableBeanFactory.class, "beanFactory", new Modifier[0]);
      Builder code = CodeBlock.builder();
      this.registrations.forEach(registration -> {
         for (String alias : registration.aliases()) {
            code.addStatement("$L.registerAlias($S, $S)", new Object[]{"beanFactory", registration.beanName(), alias});
         }
      });
      method.addCode(code.build());
   }

   private void generateRegisterHints(RuntimeHints runtimeHints, List<BeanRegistrationsAotContribution.Registration> registrations) {
      registrations.forEach(registration -> {
         ReflectionHints hints = runtimeHints.reflection();
         Class<?> beanClass = registration.registeredBean.getBeanClass();
         hints.registerType(beanClass, new MemberCategory[0]);
         hints.registerForInterfaces(beanClass, rec$ -> rec$.withMembers(new MemberCategory[0]));
      });
   }

   static final class BeanDefinitionsRegistrationGenerator {
      private final GenerationContext generationContext;
      private final BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator codeGenerator;
      private final List<BeanRegistrationsAotContribution.Registration> registrations;
      private final int globalStart;

      BeanDefinitionsRegistrationGenerator(
         GenerationContext generationContext,
         BeanRegistrationsAotContribution.BeanRegistrationsCodeGenerator codeGenerator,
         List<BeanRegistrationsAotContribution.Registration> registrations,
         int globalStart
      ) {
         this.generationContext = generationContext;
         this.codeGenerator = codeGenerator;
         this.registrations = registrations;
         this.globalStart = globalStart;
      }

      void generateBeanRegistrationsCode(org.springframework.javapoet.MethodSpec.Builder method) {
         if (this.registrations.size() <= 1000) {
            this.generateRegisterBeanDefinitionMethods(method, this.registrations);
         } else {
            Builder code = CodeBlock.builder();
            code.add("// Registration is sliced to avoid exceeding size limit\n", new Object[0]);
            BeanRegistrationsAotContribution.Registration.doWithSlice(
               this.registrations,
               1000,
               (start, end) -> {
                  GeneratedMethod sliceMethod = this.generateSliceMethod(start, end);
                  code.addStatement(
                     sliceMethod.toMethodReference()
                        .toInvokeCodeBlock(BeanRegistrationsAotContribution.argumentCodeGenerator, this.codeGenerator.getClassName())
                  );
               }
            );
            method.addCode(code.build());
         }
      }

      private GeneratedMethod generateSliceMethod(int start, int end) {
         String description = "Register the bean definitions from %s to %s.".formatted(this.globalStart + start, this.globalStart + end - 1);
         List<BeanRegistrationsAotContribution.Registration> slice = this.registrations.subList(start, end);
         return this.codeGenerator.getMethods().add("registerBeanDefinitions", method -> {
            method.addJavadoc(description, new Object[0]);
            method.addModifiers(new Modifier[]{Modifier.PRIVATE});
            method.addParameter(DefaultListableBeanFactory.class, "beanFactory", new Modifier[0]);
            this.generateRegisterBeanDefinitionMethods(method, slice);
         });
      }

      private void generateRegisterBeanDefinitionMethods(
         org.springframework.javapoet.MethodSpec.Builder method, Iterable<BeanRegistrationsAotContribution.Registration> registrations
      ) {
         Builder code = CodeBlock.builder();
         registrations.forEach(registration -> {
            try {
               CodeBlock methodInvocation = this.generateBeanRegistration(registration);
               code.addStatement("$L.registerBeanDefinition($S, $L)", new Object[]{"beanFactory", registration.beanName(), methodInvocation});
            } catch (AotException ex) {
               throw ex;
            } catch (Exception ex) {
               throw new AotBeanProcessingException(registration.registeredBean, "failed to generate code for bean definition", ex);
            }
         });
         method.addCode(code.build());
      }

      private CodeBlock generateBeanRegistration(BeanRegistrationsAotContribution.Registration registration) {
         MethodReference beanDefinitionMethod = registration.methodGenerator.generateBeanDefinitionMethod(this.generationContext, this.codeGenerator);
         return beanDefinitionMethod.toInvokeCodeBlock(ArgumentCodeGenerator.none(), this.codeGenerator.getClassName());
      }
   }

   static class BeanRegistrationsCodeGenerator implements BeanRegistrationsCode {
      private final GeneratedClass generatedClass;

      public BeanRegistrationsCodeGenerator(GeneratedClass generatedClass) {
         this.generatedClass = generatedClass;
      }

      @Override
      public ClassName getClassName() {
         return this.generatedClass.getName();
      }

      @Override
      public GeneratedMethods getMethods() {
         return this.generatedClass.getMethods();
      }
   }

   record Registration(RegisteredBean registeredBean, BeanDefinitionMethodGenerator methodGenerator, String[] aliases) {
      String beanName() {
         return this.registeredBean.getBeanName();
      }

      static void doWithSlice(List<BeanRegistrationsAotContribution.Registration> registrations, int sliceSize, BiConsumer<Integer, Integer> action) {
         int index = 0;

         for (int end = 0; end < registrations.size(); index++) {
            int start = index * sliceSize;
            end = Math.min(start + sliceSize, registrations.size());
            action.accept(start, end);
         }
      }
   }
}
