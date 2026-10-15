package org.springframework.boot.logging.structured;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.util.Instantiator;
import org.springframework.boot.util.LambdaSafe;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.util.Assert;

public class StructuredLogFormatterFactory<E> {
   private static final Instantiator.FailureHandler failureHandler = (type, implementationName, failure) -> {
      if (!(failure instanceof ClassNotFoundException)) {
         throw new IllegalArgumentException("Unable to instantiate " + implementationName + " [" + type.getName() + "]", failure);
      }
   };
   private final SpringFactoriesLoader factoriesLoader;
   private final Class<E> logEventType;
   private final Instantiator<?> instantiator;
   private final StructuredLogFormatterFactory.CommonFormatters<E> commonFormatters;

   public StructuredLogFormatterFactory(
      Class<E> logEventType,
      Environment environment,
      @Nullable Consumer<Instantiator.AvailableParameters> availableParameters,
      Consumer<StructuredLogFormatterFactory.CommonFormatters<E>> commonFormatters
   ) {
      this(SpringFactoriesLoader.forDefaultResourceLocation(), logEventType, environment, availableParameters, commonFormatters);
   }

   StructuredLogFormatterFactory(
      SpringFactoriesLoader factoriesLoader,
      Class<E> logEventType,
      Environment environment,
      @Nullable Consumer<Instantiator.AvailableParameters> availableParameters,
      Consumer<StructuredLogFormatterFactory.CommonFormatters<E>> commonFormatters
   ) {
      StructuredLoggingJsonProperties properties = StructuredLoggingJsonProperties.get(environment);
      this.factoriesLoader = factoriesLoader;
      this.logEventType = logEventType;
      this.instantiator = new Instantiator(
         Object.class,
         allAvailableParameters -> {
            allAvailableParameters.add(Environment.class, environment);
            allAvailableParameters.add(
               StructuredLoggingJsonMembersCustomizer.class, new StructuredLogFormatterFactory.JsonMembersCustomizerBuilder(properties).build()
            );
            allAvailableParameters.add(
               StructuredLoggingJsonMembersCustomizer.Builder.class, new StructuredLogFormatterFactory.JsonMembersCustomizerBuilder(properties)
            );
            allAvailableParameters.add(StackTracePrinter.class, type -> this.getStackTracePrinter(properties, environment));
            allAvailableParameters.add(ContextPairs.class, type -> this.getContextPairs(properties));
            if (availableParameters != null) {
               availableParameters.accept(allAvailableParameters);
            }
         },
         failureHandler
      );
      this.commonFormatters = new StructuredLogFormatterFactory.CommonFormatters<>();
      commonFormatters.accept(this.commonFormatters);
   }

   private @Nullable StackTracePrinter getStackTracePrinter(@Nullable StructuredLoggingJsonProperties properties, Environment environment) {
      return properties != null && properties.stackTrace() != null ? properties.stackTrace().createPrinter(environment) : null;
   }

   private ContextPairs getContextPairs(@Nullable StructuredLoggingJsonProperties properties) {
      StructuredLoggingJsonProperties.Context contextProperties = properties != null ? properties.context() : null;
      contextProperties = contextProperties != null ? contextProperties : new StructuredLoggingJsonProperties.Context(true, null);
      return new ContextPairs(contextProperties.include(), contextProperties.prefix());
   }

   public StructuredLogFormatter<E> get(String format) {
      StructuredLogFormatter<E> formatter = this.commonFormatters.get(this.instantiator, format);
      formatter = formatter != null ? formatter : this.getUsingClassName(format);
      if (formatter != null) {
         return formatter;
      } else {
         throw new IllegalArgumentException(
            "Unknown format '%s'. Values can be a valid fully-qualified class name or one of the common formats: %s"
               .formatted(format, this.commonFormatters.getCommonNames())
         );
      }
   }

   private @Nullable StructuredLogFormatter<E> getUsingClassName(String className) {
      Object formatter = this.instantiator.instantiate(className);
      if (formatter != null) {
         Assert.state(formatter instanceof StructuredLogFormatter, () -> "'%s' is not a StructuredLogFormatter".formatted(className));
         this.checkTypeArgument(formatter);
      }

      return (StructuredLogFormatter<E>)formatter;
   }

   private void checkTypeArgument(Object formatter) {
      Class<?> typeArgument = GenericTypeResolver.resolveTypeArgument(formatter.getClass(), StructuredLogFormatter.class);
      Assert.state(
         this.logEventType.equals(typeArgument),
         () -> "Type argument of %s must be %s but was %s"
            .formatted(formatter.getClass().getName(), this.logEventType.getName(), typeArgument != null ? typeArgument.getName() : "null")
      );
   }

   @FunctionalInterface
   public interface CommonFormatterFactory<E> {
      StructuredLogFormatter<E> createFormatter(Instantiator<?> instantiator);
   }

   public static class CommonFormatters<E> {
      private final Map<CommonStructuredLogFormat, StructuredLogFormatterFactory.CommonFormatterFactory<E>> factories = new TreeMap<>();

      public void add(CommonStructuredLogFormat format, StructuredLogFormatterFactory.CommonFormatterFactory<E> factory) {
         this.factories.put(format, factory);
      }

      Collection<String> getCommonNames() {
         return this.factories.keySet().stream().map(CommonStructuredLogFormat::getId).toList();
      }

      @Nullable StructuredLogFormatter<E> get(Instantiator<?> instantiator, String format) {
         CommonStructuredLogFormat commonFormat = CommonStructuredLogFormat.forId(format);
         StructuredLogFormatterFactory.CommonFormatterFactory<E> factory = commonFormat != null ? this.factories.get(commonFormat) : null;
         return factory != null ? factory.createFormatter(instantiator) : null;
      }
   }

   class JsonMembersCustomizerBuilder implements StructuredLoggingJsonMembersCustomizer.Builder<E> {
      private final @Nullable StructuredLoggingJsonProperties properties;
      private boolean nested;

      JsonMembersCustomizerBuilder(@Nullable StructuredLoggingJsonProperties properties) {
         this.properties = properties;
      }

      public StructuredLogFormatterFactory<E>.JsonMembersCustomizerBuilder nested(boolean nested) {
         this.nested = nested;
         return this;
      }

      @Override
      public StructuredLoggingJsonMembersCustomizer<E> build() {
         return members -> {
            List<StructuredLoggingJsonMembersCustomizer<?>> customizers = new ArrayList<>();
            if (this.properties != null) {
               customizers.add(
                  new StructuredLoggingJsonPropertiesJsonMembersCustomizer(StructuredLogFormatterFactory.this.instantiator, this.properties, this.nested)
               );
            }

            customizers.addAll(this.loadStructuredLoggingJsonMembersCustomizers());
            this.invokeCustomizers(members, customizers);
         };
      }

      private List<StructuredLoggingJsonMembersCustomizer<?>> loadStructuredLoggingJsonMembersCustomizers() {
         return StructuredLogFormatterFactory.this.factoriesLoader
            .load(StructuredLoggingJsonMembersCustomizer.class, ArgumentResolver.from(StructuredLogFormatterFactory.this.instantiator::getArg));
      }

      private void invokeCustomizers(JsonWriter.Members<E> members, List<StructuredLoggingJsonMembersCustomizer<?>> customizers) {
         ((LambdaSafe.Callbacks)LambdaSafe.callbacks(StructuredLoggingJsonMembersCustomizer.class, customizers, members)
               .withFilter(LambdaSafe.Filter.allowAll()))
            .invoke(customizer -> customizer.customize(members));
      }
   }
}
