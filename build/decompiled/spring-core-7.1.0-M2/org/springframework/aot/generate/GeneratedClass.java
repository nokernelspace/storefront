package org.springframework.aot.generate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.JavaFile;
import org.springframework.javapoet.TypeSpec;
import org.springframework.util.Assert;

public final class GeneratedClass {
   private final @Nullable GeneratedClass enclosingClass;
   private final ClassName name;
   private final GeneratedMethods methods;
   private final Consumer<TypeSpec.Builder> type;
   private final Map<ClassName, GeneratedClass> declaredClasses;
   private final Map<MethodName, AtomicInteger> methodNameSequenceGenerator;

   GeneratedClass(ClassName name, Consumer<TypeSpec.Builder> type) {
      this(null, name, type);
   }

   private GeneratedClass(@Nullable GeneratedClass enclosingClass, ClassName name, Consumer<TypeSpec.Builder> type) {
      this.enclosingClass = enclosingClass;
      this.name = name;
      this.type = type;
      this.methods = new GeneratedMethods(name, this::generateSequencedMethodName);
      this.declaredClasses = new ConcurrentHashMap<>();
      this.methodNameSequenceGenerator = new ConcurrentHashMap<>();
   }

   public void reserveMethodNames(String... reservedMethodNames) {
      for (String reservedMethodName : reservedMethodNames) {
         String generatedName = this.generateSequencedMethodName(MethodName.of(reservedMethodName));
         Assert.state(generatedName.equals(reservedMethodName), () -> String.format("Unable to reserve method name '%s'", reservedMethodName));
      }
   }

   private String generateSequencedMethodName(MethodName name) {
      int sequence = this.methodNameSequenceGenerator.computeIfAbsent(name, key -> new AtomicInteger()).getAndIncrement();
      return sequence > 0 ? name.toString() + sequence : name.toString();
   }

   public @Nullable GeneratedClass getEnclosingClass() {
      return this.enclosingClass;
   }

   public ClassName getName() {
      return this.name;
   }

   public GeneratedMethods getMethods() {
      return this.methods;
   }

   public GeneratedClass getOrAdd(String name, Consumer<TypeSpec.Builder> type) {
      ClassName className = this.name.nestedClass(name);
      return this.declaredClasses.computeIfAbsent(className, key -> new GeneratedClass(this, className, type));
   }

   JavaFile generateJavaFile() {
      Assert.state(this.getEnclosingClass() == null, "Java file cannot be generated for an inner class");
      TypeSpec.Builder type = this.apply();
      return JavaFile.builder(this.name.packageName(), type.build()).build();
   }

   private TypeSpec.Builder apply() {
      TypeSpec.Builder type = this.getBuilder(this.type);
      type.addAnnotation(Generated.class);
      this.methods.doWithMethodSpecs(type::addMethod);
      this.declaredClasses.values().forEach(declaredClass -> type.addType(declaredClass.apply().build()));
      return type;
   }

   private TypeSpec.Builder getBuilder(Consumer<TypeSpec.Builder> type) {
      TypeSpec.Builder builder = TypeSpec.classBuilder(this.name);
      type.accept(builder);
      return builder;
   }

   void assertSameType(Consumer<TypeSpec.Builder> type) {
      Assert.state(type == this.type || this.getBuilder(this.type).build().equals(this.getBuilder(type).build()), "'type' consumer generated different result");
   }
}
