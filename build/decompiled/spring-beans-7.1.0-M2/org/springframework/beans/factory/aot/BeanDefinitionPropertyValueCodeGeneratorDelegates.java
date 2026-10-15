package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.ValueCodeGenerator;
import org.springframework.aot.generate.ValueCodeGeneratorDelegates;
import org.springframework.aot.generate.ValueCodeGenerator.Delegate;
import org.springframework.aot.generate.ValueCodeGeneratorDelegates.CollectionDelegate;
import org.springframework.aot.generate.ValueCodeGeneratorDelegates.MapDelegate;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.javapoet.AnnotationSpec;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;

public abstract class BeanDefinitionPropertyValueCodeGeneratorDelegates {
   public static final List<Delegate> INSTANCES = List.of(
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.ManagedListDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.ManagedSetDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.ManagedMapDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.LinkedHashMapDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.BeanReferenceDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.TypedStringValueDelegate(),
      new BeanDefinitionPropertyValueCodeGeneratorDelegates.AutowiredPropertyMarkerDelegate()
   );

   public static ValueCodeGenerator createValueCodeGenerator(GeneratedMethods generatedMethods, List<Delegate> customDelegates) {
      List<Delegate> allDelegates = new ArrayList<>();
      allDelegates.addAll(customDelegates);
      allDelegates.addAll(INSTANCES);
      allDelegates.addAll(ValueCodeGeneratorDelegates.INSTANCES);
      return ValueCodeGenerator.with(allDelegates).scoped(generatedMethods);
   }

   private static class AutowiredPropertyMarkerDelegate implements Delegate {
      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         return value instanceof AutowiredPropertyMarker ? CodeBlock.of("$T.INSTANCE", new Object[]{AutowiredPropertyMarker.class}) : null;
      }
   }

   private static class BeanReferenceDelegate implements Delegate {
      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         if (value instanceof RuntimeBeanReference runtimeBeanReference && runtimeBeanReference.getBeanType() != null) {
            return CodeBlock.of(
               "new $T($S, $T.class)", new Object[]{RuntimeBeanReference.class, runtimeBeanReference.getBeanName(), runtimeBeanReference.getBeanType()}
            );
         } else {
            return value instanceof BeanReference beanReference
               ? CodeBlock.of("new $T($S)", new Object[]{RuntimeBeanReference.class, beanReference.getBeanName()})
               : null;
         }
      }
   }

   private static class LinkedHashMapDelegate extends MapDelegate {
      protected @Nullable CodeBlock generateMapCode(ValueCodeGenerator valueCodeGenerator, Map<?, ?> map) {
         GeneratedMethods generatedMethods = valueCodeGenerator.getGeneratedMethods();
         return map instanceof LinkedHashMap && generatedMethods != null
            ? this.generateLinkedHashMapCode(valueCodeGenerator, generatedMethods, map)
            : super.generateMapCode(valueCodeGenerator, map);
      }

      private CodeBlock generateLinkedHashMapCode(ValueCodeGenerator valueCodeGenerator, GeneratedMethods generatedMethods, Map<?, ?> map) {
         GeneratedMethod generatedMethod = generatedMethods.add(
            "getMap",
            method -> {
               method.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{\"rawtypes\", \"unchecked\"}", new Object[0]).build());
               method.addModifiers(new Modifier[]{Modifier.PRIVATE, Modifier.STATIC});
               method.returns(Map.class);
               method.addStatement("$T map = new $T($L)", new Object[]{Map.class, LinkedHashMap.class, map.size()});
               map.forEach(
                  (key, value) -> method.addStatement(
                     "map.put($L, $L)", new Object[]{valueCodeGenerator.generateCode(key), valueCodeGenerator.generateCode(value)}
                  )
               );
               method.addStatement("return map", new Object[0]);
            }
         );
         return CodeBlock.of("$L()", new Object[]{generatedMethod.getName()});
      }
   }

   private static class ManagedListDelegate extends CollectionDelegate<ManagedList<?>> {
      public ManagedListDelegate() {
         super(ManagedList.class, CodeBlock.of("new $T()", new Object[]{ManagedList.class}));
      }
   }

   private static class ManagedMapDelegate implements Delegate {
      private static final CodeBlock EMPTY_RESULT = CodeBlock.of("$T.ofEntries()", new Object[]{ManagedMap.class});

      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         return value instanceof ManagedMap<?, ?> managedMap ? this.generateManagedMapCode(valueCodeGenerator, managedMap) : null;
      }

      private <K, V> CodeBlock generateManagedMapCode(ValueCodeGenerator valueCodeGenerator, ManagedMap<K, V> managedMap) {
         if (managedMap.isEmpty()) {
            return EMPTY_RESULT;
         }

         Builder code = CodeBlock.builder();
         code.add("$T.ofEntries(", new Object[]{ManagedMap.class});
         Iterator<Entry<K, V>> iterator = managedMap.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<?, ?> entry = iterator.next();
            code.add(
               "$T.entry($L,$L)", new Object[]{Map.class, valueCodeGenerator.generateCode(entry.getKey()), valueCodeGenerator.generateCode(entry.getValue())}
            );
            if (iterator.hasNext()) {
               code.add(", ", new Object[0]);
            }
         }

         code.add(")", new Object[0]);
         return code.build();
      }
   }

   private static class ManagedSetDelegate extends CollectionDelegate<ManagedSet<?>> {
      public ManagedSetDelegate() {
         super(ManagedSet.class, CodeBlock.of("new $T()", new Object[]{ManagedSet.class}));
      }
   }

   private static class TypedStringValueDelegate implements Delegate {
      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         return value instanceof TypedStringValue typedStringValue ? this.generateTypeStringValueCode(valueCodeGenerator, typedStringValue) : null;
      }

      private CodeBlock generateTypeStringValueCode(ValueCodeGenerator valueCodeGenerator, TypedStringValue typedStringValue) {
         String value = typedStringValue.getValue();
         return typedStringValue.hasTargetType()
            ? CodeBlock.of("new $T($S, $L)", new Object[]{TypedStringValue.class, value, valueCodeGenerator.generateCode(typedStringValue.getTargetType())})
            : valueCodeGenerator.generateCode(value);
      }
   }
}
