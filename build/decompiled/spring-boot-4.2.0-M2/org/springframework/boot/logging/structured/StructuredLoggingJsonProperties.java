package org.springframework.boot.logging.structured;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.StandardStackTracePrinter;
import org.springframework.boot.util.Instantiator;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

record StructuredLoggingJsonProperties(
   Set<String> include,
   Set<String> exclude,
   Map<String, String> rename,
   Map<String, String> add,
   StructuredLoggingJsonProperties.@Nullable StackTrace stackTrace,
   StructuredLoggingJsonProperties.@Nullable Context context,
   Set<Class<? extends StructuredLoggingJsonMembersCustomizer<?>>> customizer
) {
   StructuredLoggingJsonProperties(
      Set<String> include,
      Set<String> exclude,
      Map<String, String> rename,
      Map<String, String> add,
      StructuredLoggingJsonProperties.@Nullable StackTrace stackTrace,
      StructuredLoggingJsonProperties.@Nullable Context context,
      @Nullable Set<Class<? extends StructuredLoggingJsonMembersCustomizer<?>>> customizer
   ) {
      this.include = include;
      this.exclude = exclude;
      this.rename = rename;
      this.add = add;
      this.stackTrace = stackTrace;
      this.context = context;
      this.customizer = customizer != null ? customizer : Collections.emptySet();
   }

   Collection<StructuredLoggingJsonMembersCustomizer<Object>> customizers(Instantiator<?> instantiator) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.NullPointerException: Cannot read field "value" because "returnType" is null
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent.getInferredExprType(NewExprent.java:226)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent.getInferredExprType(InvocationExprent.java:506)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent.getInferredExprType(InvocationExprent.java:342)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1002)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.toJava(ExitExprent.java:85)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
      //   at org.jetbrains.java.decompiler.main.ClassWriter.writeMethod(ClassWriter.java:1351)
      //
      // Bytecode:
      // 00: aload 0
      // 01: invokevirtual org/springframework/boot/logging/structured/StructuredLoggingJsonProperties.customizer ()Ljava/util/Set;
      // 04: invokeinterface java/util/Set.stream ()Ljava/util/stream/Stream; 1
      // 09: aload 1
      // 0a: dup
      // 0b: invokestatic java/util/Objects.requireNonNull (Ljava/lang/Object;)Ljava/lang/Object;
      // 0e: pop
      // 0f: invokedynamic apply (Lorg/springframework/boot/util/Instantiator;)Ljava/util/function/Function; bsm=java/lang/invoke/LambdaMetafactory.metafactory (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; args=[ (Ljava/lang/Object;)Ljava/lang/Object;, org/springframework/boot/util/Instantiator.instantiateType (Ljava/lang/Class;)Ljava/lang/Object;, (Ljava/lang/Class;)Ljava/lang/Object; ]
      // 14: invokeinterface java/util/stream/Stream.map (Ljava/util/function/Function;)Ljava/util/stream/Stream; 2
      // 19: invokeinterface java/util/stream/Stream.toList ()Ljava/util/List; 1
      // 1e: areturn
   }

   static @Nullable StructuredLoggingJsonProperties get(Environment environment) {
      return Binder.get(environment).bind("logging.structured.json", StructuredLoggingJsonProperties.class).orElse(null);
   }

   record Context(@DefaultValue("true") boolean include, @Nullable String prefix) {
   }

   record StackTrace(
      @Nullable String printer,
      StructuredLoggingJsonProperties.StackTrace.@Nullable Root root,
      @Nullable Integer maxLength,
      @Nullable Integer maxThrowableDepth,
      @Nullable Boolean includeCommonFrames,
      @Nullable Boolean includeHashes
   ) {
      @Nullable StackTracePrinter createPrinter(Environment environment) {
         String name = this.sanitizePrinter();
         if (!"loggingsystem".equals(name) && (!name.isEmpty() || this.hasAnyOtherProperty())) {
            StandardStackTracePrinter standardPrinter = this.createStandardPrinter();
            if (!"standard".equals(name) && !name.isEmpty()) {
               Assert.state(this.printer() != null, "'printer' must not be null");
               return new Instantiator<StackTracePrinter>(StackTracePrinter.class, parameters -> {
                  parameters.add(StandardStackTracePrinter.class, standardPrinter);
                  parameters.add(Environment.class, environment);
               }).instantiate(this.printer());
            } else {
               return standardPrinter;
            }
         } else {
            return null;
         }
      }

      boolean hasCustomPrinter() {
         String name = this.sanitizePrinter();
         return name.isEmpty() ? false : !"loggingsystem".equals(name) && !"standard".equals(name);
      }

      private String sanitizePrinter() {
         return Objects.toString(this.printer(), "").toLowerCase(Locale.ROOT).replace("-", "");
      }

      private boolean hasAnyOtherProperty() {
         return Stream.of(this.root(), this.maxLength(), this.maxThrowableDepth(), this.includeCommonFrames(), this.includeHashes())
            .anyMatch(x$0 -> Objects.nonNull(x$0));
      }

      private StandardStackTracePrinter createStandardPrinter() {
         StandardStackTracePrinter printer = this.root() == StructuredLoggingJsonProperties.StackTrace.Root.FIRST
            ? StandardStackTracePrinter.rootFirst()
            : StandardStackTracePrinter.rootLast();
         PropertyMapper map = PropertyMapper.get();
         printer = map.from(this::maxLength).to(printer, StandardStackTracePrinter::withMaximumLength);
         printer = map.from(this::maxThrowableDepth).to(printer, StandardStackTracePrinter::withMaximumThrowableDepth);
         printer = map.from(this::includeCommonFrames).to(printer, this.apply(StandardStackTracePrinter::withCommonFrames));
         return map.from(this::includeHashes).to(printer, this.apply(StandardStackTracePrinter::withHashes));
      }

      private BiFunction<StandardStackTracePrinter, Boolean, StandardStackTracePrinter> apply(UnaryOperator<StandardStackTracePrinter> action) {
         return (printer, value) -> !value ? printer : action.apply(printer);
      }

      enum Root {
         LAST,
         FIRST;
      }
   }

   static class StructuredLoggingJsonPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         BindableRuntimeHintsRegistrar.forTypes(StructuredLoggingJsonProperties.class).registerHints(hints, classLoader);
      }
   }
}
