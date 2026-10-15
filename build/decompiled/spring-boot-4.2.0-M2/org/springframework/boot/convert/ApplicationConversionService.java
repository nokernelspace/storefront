package org.springframework.boot.convert;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class ApplicationConversionService extends FormattingConversionService {
   private static final ResolvableType STRING = ResolvableType.forClass(String.class);
   private static volatile @Nullable ApplicationConversionService sharedInstance;
   private final boolean unmodifiable;

   public ApplicationConversionService() {
      this(null);
   }

   public ApplicationConversionService(@Nullable StringValueResolver embeddedValueResolver) {
      this(embeddedValueResolver, false);
   }

   private ApplicationConversionService(@Nullable StringValueResolver embeddedValueResolver, boolean unmodifiable) {
      if (embeddedValueResolver != null) {
         this.setEmbeddedValueResolver(embeddedValueResolver);
      }

      configure(this);
      this.unmodifiable = unmodifiable;
   }

   public void addPrinter(Printer<?> printer) {
      this.assertModifiable();
      super.addPrinter(printer);
   }

   public void addParser(Parser<?> parser) {
      this.assertModifiable();
      super.addParser(parser);
   }

   public void addFormatter(Formatter<?> formatter) {
      this.assertModifiable();
      super.addFormatter(formatter);
   }

   public void addFormatterForFieldType(Class<?> fieldType, Formatter<?> formatter) {
      this.assertModifiable();
      super.addFormatterForFieldType(fieldType, formatter);
   }

   public void addConverter(Converter<?, ?> converter) {
      this.assertModifiable();
      super.addConverter(converter);
   }

   public void addFormatterForFieldType(Class<?> fieldType, Printer<?> printer, Parser<?> parser) {
      this.assertModifiable();
      super.addFormatterForFieldType(fieldType, printer, parser);
   }

   public void addFormatterForFieldAnnotation(AnnotationFormatterFactory<? extends Annotation> annotationFormatterFactory) {
      this.assertModifiable();
      super.addFormatterForFieldAnnotation(annotationFormatterFactory);
   }

   public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
      this.assertModifiable();
      super.addConverter(sourceType, targetType, converter);
   }

   public void addConverter(GenericConverter converter) {
      this.assertModifiable();
      super.addConverter(converter);
   }

   public void addConverterFactory(ConverterFactory<?, ?> factory) {
      this.assertModifiable();
      super.addConverterFactory(factory);
   }

   public void removeConvertible(Class<?> sourceType, Class<?> targetType) {
      this.assertModifiable();
      super.removeConvertible(sourceType, targetType);
   }

   private void assertModifiable() {
      if (this.unmodifiable) {
         throw new UnsupportedOperationException("This ApplicationConversionService cannot be modified");
      }
   }

   public boolean isConvertViaObjectSourceType(TypeDescriptor sourceType, TypeDescriptor targetType) {
      GenericConverter converter = this.getConverter(sourceType, targetType);
      Set<ConvertiblePair> pairs = converter != null ? converter.getConvertibleTypes() : null;
      if (pairs != null) {
         for (ConvertiblePair pair : pairs) {
            if (Object.class.equals(pair.getSourceType())) {
               return true;
            }
         }
      }

      return false;
   }

   public static ConversionService getSharedInstance() {
      ApplicationConversionService sharedInstance = ApplicationConversionService.sharedInstance;
      if (sharedInstance == null) {
         synchronized (ApplicationConversionService.class) {
            sharedInstance = ApplicationConversionService.sharedInstance;
            if (sharedInstance == null) {
               sharedInstance = new ApplicationConversionService(null, true);
               ApplicationConversionService.sharedInstance = sharedInstance;
            }
         }
      }

      return sharedInstance;
   }

   public static void configure(FormatterRegistry registry) {
      DefaultConversionService.addDefaultConverters(registry);
      DefaultFormattingConversionService.addDefaultFormatters(registry);
      addApplicationFormatters(registry);
      addApplicationConverters(registry);
   }

   public static void addApplicationConverters(ConverterRegistry registry) {
      addDelimitedStringConverters(registry);
      registry.addConverter(new StringToDurationConverter());
      registry.addConverter(new DurationToStringConverter());
      registry.addConverter(new NumberToDurationConverter());
      registry.addConverter(new DurationToNumberConverter());
      registry.addConverter(new StringToPeriodConverter());
      registry.addConverter(new PeriodToStringConverter());
      registry.addConverter(new NumberToPeriodConverter());
      registry.addConverter(new StringToDataSizeConverter());
      registry.addConverter(new NumberToDataSizeConverter());
      registry.addConverter(new StringToFileConverter());
      registry.addConverter(new InputStreamSourceToByteArrayConverter());
      registry.addConverterFactory(new LenientStringToEnumConverterFactory());
      registry.addConverterFactory(new LenientBooleanToEnumConverterFactory());
      if (registry instanceof ConversionService conversionService) {
         addApplicationConverters(registry, conversionService);
      }
   }

   private static void addApplicationConverters(ConverterRegistry registry, ConversionService conversionService) {
      registry.addConverter(new CharSequenceToObjectConverter(conversionService));
   }

   public static void addDelimitedStringConverters(ConverterRegistry registry) {
      ConversionService service = (ConversionService)registry;
      registry.addConverter(new ArrayToDelimitedStringConverter(service));
      registry.addConverter(new CollectionToDelimitedStringConverter(service));
      registry.addConverter(new DelimitedStringToArrayConverter(service));
      registry.addConverter(new DelimitedStringToCollectionConverter(service));
   }

   public static void addApplicationFormatters(FormatterRegistry registry) {
      registry.addFormatter(new CharArrayFormatter());
      registry.addFormatter(new InetAddressFormatter());
      registry.addFormatter(new IsoOffsetFormatter());
   }

   public static void addBeans(FormatterRegistry registry, ListableBeanFactory beanFactory) {
      addBeans(registry, beanFactory, null);
   }

   public static Map<String, Object> addBeans(FormatterRegistry registry, ListableBeanFactory beanFactory, @Nullable String qualifier) {
      ConfigurableListableBeanFactory configurableBeanFactory = getConfigurableListableBeanFactory(beanFactory);
      Map<String, Object> beans = getBeans(beanFactory, qualifier);
      beans.forEach((beanName, bean) -> {
         BeanDefinition beanDefinition = configurableBeanFactory != null ? configurableBeanFactory.getMergedBeanDefinition(beanName) : null;
         ResolvableType type = beanDefinition != null ? beanDefinition.getResolvableType() : null;
         addBean(registry, bean, type);
      });
      return beans;
   }

   private static @Nullable ConfigurableListableBeanFactory getConfigurableListableBeanFactory(ListableBeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableApplicationContext applicationContext) {
         return applicationContext.getBeanFactory();
      } else {
         return beanFactory instanceof ConfigurableListableBeanFactory configurableListableBeanFactory ? configurableListableBeanFactory : null;
      }
   }

   private static Map<String, Object> getBeans(ListableBeanFactory beanFactory, @Nullable String qualifier) {
      Map<String, Object> beans = new LinkedHashMap<>();
      beans.putAll(getBeans(beanFactory, Printer.class, qualifier));
      beans.putAll(getBeans(beanFactory, Parser.class, qualifier));
      beans.putAll(getBeans(beanFactory, Formatter.class, qualifier));
      beans.putAll(getBeans(beanFactory, Converter.class, qualifier));
      beans.putAll(getBeans(beanFactory, ConverterFactory.class, qualifier));
      beans.putAll(getBeans(beanFactory, GenericConverter.class, qualifier));
      return beans;
   }

   private static <T> Map<String, T> getBeans(ListableBeanFactory beanFactory, Class<T> type, @Nullable String qualifier) {
      return !StringUtils.hasLength(qualifier)
         ? beanFactory.getBeansOfType(type)
         : BeanFactoryAnnotationUtils.qualifiedBeansOfType(beanFactory, type, qualifier);
   }

   static void addBean(FormatterRegistry registry, Object bean, @Nullable ResolvableType beanType) {
      if (bean instanceof GenericConverter converterBean) {
         addBean(registry, converterBean, beanType, GenericConverter.class, registry::addConverter, (Runnable)null);
      } else if (bean instanceof Converter<?, ?> converterBean) {
         Assert.state(beanType != null, "beanType is missing");
         addBeanWithType(registry, converterBean, beanType, Converter.class, registry::addConverter, ApplicationConversionService.ConverterBeanAdapter::new);
      } else if (bean instanceof ConverterFactory<?, ?> converterBean) {
         Assert.state(beanType != null, "beanType is missing");
         addBeanWithType(
            registry,
            converterBean,
            beanType,
            ConverterFactory.class,
            registry::addConverterFactory,
            ApplicationConversionService.ConverterFactoryBeanAdapter::new
         );
      } else if (bean instanceof Formatter<?> formatterBean) {
         addBean(registry, formatterBean, beanType, Formatter.class, registry::addFormatter, () -> {
            Assert.state(beanType != null, "beanType is missing");
            registry.addConverter(new ApplicationConversionService.PrinterBeanAdapter(formatterBean, beanType));
            registry.addConverter(new ApplicationConversionService.ParserBeanAdapter(formatterBean, beanType));
         });
      } else if (bean instanceof Printer<?> printerBean) {
         Assert.state(beanType != null, "beanType is missing");
         addBeanWithType(registry, printerBean, beanType, Printer.class, registry::addPrinter, ApplicationConversionService.PrinterBeanAdapter::new);
      } else if (bean instanceof Parser<?> parserBean) {
         Assert.state(beanType != null, "beanType is missing");
         addBeanWithType(registry, parserBean, beanType, Parser.class, registry::addParser, ApplicationConversionService.ParserBeanAdapter::new);
      }
   }

   private static <B, T> void addBeanWithType(
      FormatterRegistry registry,
      B bean,
      ResolvableType beanType,
      Class<T> type,
      Consumer<B> standardRegistrar,
      BiFunction<B, ResolvableType, ApplicationConversionService.BeanAdapter<?>> beanAdapterFactory
   ) {
      addBean(registry, bean, beanType, type, standardRegistrar, () -> registry.addConverter((GenericConverter)beanAdapterFactory.apply(bean, beanType)));
   }

   private static <B, T> void addBean(
      FormatterRegistry registry,
      B bean,
      @Nullable ResolvableType beanType,
      Class<T> type,
      Consumer<B> standardRegistrar,
      @Nullable Runnable beanAdapterRegistrar
   ) {
      if (beanType != null && beanAdapterRegistrar != null && ResolvableType.forInstance(bean).as(type).hasUnresolvableGenerics()) {
         beanAdapterRegistrar.run();
      } else {
         standardRegistrar.accept(bean);
      }
   }

   abstract static class BeanAdapter<B> implements ConditionalGenericConverter {
      private final B bean;
      private final ApplicationConversionService.ResolvableTypePair types;

      BeanAdapter(B bean, ResolvableType beanType) {
         Assert.isInstanceOf(beanType.toClass(), bean);
         ResolvableType type = ResolvableType.forClass(this.getClass()).as(ApplicationConversionService.BeanAdapter.class).getGeneric(new int[0]);
         ResolvableType[] generics = beanType.as(type.toClass()).getGenerics();
         this.bean = bean;
         this.types = this.getResolvableTypePair(generics);
      }

      protected ApplicationConversionService.ResolvableTypePair getResolvableTypePair(ResolvableType[] generics) {
         return new ApplicationConversionService.ResolvableTypePair(generics[0], generics[1]);
      }

      protected B bean() {
         return this.bean;
      }

      public Set<ConvertiblePair> getConvertibleTypes() {
         return Set.of(new ConvertiblePair(this.types.source().toClass(), this.types.target().toClass()));
      }

      public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
         return this.types.target().toClass() == targetType.getObjectType() && this.matchesTargetType(targetType.getResolvableType());
      }

      private boolean matchesTargetType(ResolvableType targetType) {
         ResolvableType ours = this.types.target();
         return targetType.getType() instanceof Class || targetType.isAssignableFrom(ours) || this.types.target().hasUnresolvableGenerics();
      }

      protected final boolean conditionalConverterCandidateMatches(Object conditionalConverterCandidate, TypeDescriptor sourceType, TypeDescriptor targetType) {
         return conditionalConverterCandidate instanceof ConditionalConverter conditionalConverter
            ? conditionalConverter.matches(sourceType, targetType)
            : true;
      }

      protected final @Nullable Object convert(@Nullable Object source, TypeDescriptor targetType, Converter<?, ?> converter) {
         return source != null ? converter.convert(source) : this.convertNull(targetType);
      }

      private @Nullable Object convertNull(TypeDescriptor targetType) {
         return targetType.getObjectType() != Optional.class ? null : Optional.empty();
      }

      @Override
      public String toString() {
         return this.types + " : " + this.bean;
      }
   }

   static final class ConverterBeanAdapter extends ApplicationConversionService.BeanAdapter<Converter<?, ?>> {
      ConverterBeanAdapter(Converter<?, ?> bean, ResolvableType beanType) {
         super(bean, beanType);
      }

      @Override
      public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
         return super.matches(sourceType, targetType) && this.conditionalConverterCandidateMatches(this.bean(), sourceType, targetType);
      }

      public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
         return this.convert(source, targetType, this.bean());
      }
   }

   private static final class ConverterFactoryBeanAdapter extends ApplicationConversionService.BeanAdapter<ConverterFactory<?, ?>> {
      ConverterFactoryBeanAdapter(ConverterFactory<?, ?> bean, ResolvableType beanType) {
         super(bean, beanType);
      }

      @Override
      public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
         return super.matches(sourceType, targetType)
            && this.conditionalConverterCandidateMatches(this.bean(), sourceType, targetType)
            && this.conditionalConverterCandidateMatches(this.getConverter(targetType::getType), sourceType, targetType);
      }

      public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
         return this.convert(source, targetType, this.getConverter(targetType::getObjectType));
      }

      private Converter<Object, ?> getConverter(Supplier<Class<?>> typeSupplier) {
         return this.bean().getConverter(typeSupplier.get());
      }
   }

   static class ParserBeanAdapter extends ApplicationConversionService.BeanAdapter<Parser<?>> {
      ParserBeanAdapter(Parser<?> bean, ResolvableType beanType) {
         super(bean, beanType);
      }

      @Override
      protected ApplicationConversionService.ResolvableTypePair getResolvableTypePair(ResolvableType[] generics) {
         return new ApplicationConversionService.ResolvableTypePair(ApplicationConversionService.STRING, generics[0]);
      }

      public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
         String text = (String)source;
         return !StringUtils.hasText(text) ? null : this.parse(text);
      }

      private Object parse(String text) {
         try {
            return this.bean().parse(text, LocaleContextHolder.getLocale());
         } catch (IllegalArgumentException ex) {
            throw ex;
         } catch (Throwable ex) {
            throw new IllegalArgumentException("Parse attempt failed for value [" + text + "]", ex);
         }
      }
   }

   static class PrinterBeanAdapter extends ApplicationConversionService.BeanAdapter<Printer<?>> {
      PrinterBeanAdapter(Printer<?> bean, ResolvableType beanType) {
         super(bean, beanType);
      }

      @Override
      protected ApplicationConversionService.ResolvableTypePair getResolvableTypePair(ResolvableType[] generics) {
         return new ApplicationConversionService.ResolvableTypePair(generics[0], ApplicationConversionService.STRING);
      }

      public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
         return source != null ? this.print(source) : "";
      }

      private String print(Object object) {
         return this.bean().print(object, LocaleContextHolder.getLocale());
      }
   }

   record ResolvableTypePair(ResolvableType source, ResolvableType target) {
      ResolvableTypePair {
         Assert.notNull(source.resolve(), "'source' cannot be resolved");
         Assert.notNull(target.resolve(), "'target' cannot be resolved");
      }

      @Override
      public final String toString() {
         return this.source() + " -> " + this.target();
      }
   }
}
