package org.springframework.web.service.invoker;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.Assert;
import org.springframework.util.StringValueResolver;
import org.springframework.web.service.annotation.HttpExchange;

public final class HttpServiceProxyFactory {
   private final HttpExchangeAdapter exchangeAdapter;
   private final List<HttpServiceArgumentResolver> argumentResolvers;
   private final HttpRequestValues.Processor requestValuesProcessor;
   private final @Nullable StringValueResolver embeddedValueResolver;
   private final List<HttpServiceProxyFactory.ProxyFactoryCustomizer> proxyFactoryCustomizers;

   private HttpServiceProxyFactory(
      HttpExchangeAdapter exchangeAdapter,
      List<HttpServiceArgumentResolver> argumentResolvers,
      List<HttpRequestValues.Processor> requestValuesProcessor,
      @Nullable StringValueResolver embeddedValueResolver,
      List<HttpServiceProxyFactory.ProxyFactoryCustomizer> proxyFactoryCustomizers
   ) {
      this.exchangeAdapter = exchangeAdapter;
      this.argumentResolvers = argumentResolvers;
      this.requestValuesProcessor = new HttpServiceProxyFactory.CompositeHttpRequestValuesProcessor(requestValuesProcessor);
      this.embeddedValueResolver = embeddedValueResolver;
      this.proxyFactoryCustomizers = proxyFactoryCustomizers;
   }

   public <S> S createClient(Class<S> serviceType) {
      List<HttpServiceMethod> httpServiceMethods = MethodIntrospector.selectMethods(serviceType, this::isExchangeMethod)
         .stream()
         .map(method -> this.createHttpServiceMethod(serviceType, method))
         .toList();
      return this.getProxy(serviceType, httpServiceMethods);
   }

   private <S> S getProxy(Class<S> serviceType, List<HttpServiceMethod> httpServiceMethods) {
      MethodInterceptor interceptor = new HttpServiceProxyFactory.HttpServiceMethodInterceptor(httpServiceMethods);
      ProxyFactory factory = new ProxyFactory(serviceType, interceptor);

      for (HttpServiceProxyFactory.ProxyFactoryCustomizer customizer : this.proxyFactoryCustomizers) {
         customizer.customize(factory, serviceType);
      }

      return (S)factory.getProxy(serviceType.getClassLoader());
   }

   private boolean isExchangeMethod(Method method) {
      return AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
   }

   private <S> HttpServiceMethod createHttpServiceMethod(Class<S> serviceType, Method method) {
      Assert.notNull(this.argumentResolvers, "No argument resolvers: afterPropertiesSet was not called");
      return new HttpServiceMethod(method, serviceType, this.argumentResolvers, this.requestValuesProcessor, this.exchangeAdapter, this.embeddedValueResolver);
   }

   public static HttpServiceProxyFactory.Builder builderFor(HttpExchangeAdapter exchangeAdapter) {
      return new HttpServiceProxyFactory.Builder().exchangeAdapter(exchangeAdapter);
   }

   public static HttpServiceProxyFactory.Builder builder() {
      return new HttpServiceProxyFactory.Builder();
   }

   public static final class Builder {
      private @Nullable HttpExchangeAdapter exchangeAdapter;
      private Function<HttpExchangeAdapter, HttpExchangeAdapter> exchangeAdapterDecorator = Function.identity();
      private final List<HttpServiceArgumentResolver> customArgumentResolvers = new ArrayList<>();
      private @Nullable ConversionService conversionService;
      private final List<HttpRequestValues.Processor> requestValuesProcessors = new ArrayList<>();
      private @Nullable StringValueResolver embeddedValueResolver;
      private final List<HttpServiceProxyFactory.ProxyFactoryCustomizer> proxyFactoryCustomizers = new ArrayList<>();

      private Builder() {
      }

      public HttpServiceProxyFactory.Builder exchangeAdapter(HttpExchangeAdapter adapter) {
         this.exchangeAdapter = adapter;
         return this;
      }

      public HttpServiceProxyFactory.Builder exchangeAdapterDecorator(Function<HttpExchangeAdapter, HttpExchangeAdapter> decorator) {
         this.exchangeAdapterDecorator = this.exchangeAdapterDecorator.andThen(decorator);
         return this;
      }

      public HttpServiceProxyFactory.Builder customArgumentResolver(HttpServiceArgumentResolver resolver) {
         this.customArgumentResolvers.add(resolver);
         return this;
      }

      public HttpServiceProxyFactory.Builder conversionService(ConversionService conversionService) {
         this.conversionService = conversionService;
         return this;
      }

      public HttpServiceProxyFactory.Builder httpRequestValuesProcessor(HttpRequestValues.Processor processor) {
         this.requestValuesProcessors.add(processor);
         return this;
      }

      public HttpServiceProxyFactory.Builder embeddedValueResolver(StringValueResolver embeddedValueResolver) {
         this.embeddedValueResolver = embeddedValueResolver;
         return this;
      }

      public HttpServiceProxyFactory.Builder proxyFactoryCustomizer(HttpServiceProxyFactory.ProxyFactoryCustomizer customizer) {
         this.proxyFactoryCustomizers.add(customizer);
         return this;
      }

      public HttpServiceProxyFactory build() {
         Assert.notNull(this.exchangeAdapter, "HttpClientAdapter is required");
         HttpExchangeAdapter adapterToUse = this.exchangeAdapterDecorator.apply(this.exchangeAdapter);
         return new HttpServiceProxyFactory(
            adapterToUse, this.initArgumentResolvers(), this.requestValuesProcessors, this.embeddedValueResolver, this.proxyFactoryCustomizers
         );
      }

      private List<HttpServiceArgumentResolver> initArgumentResolvers() {
         List<HttpServiceArgumentResolver> resolvers = new ArrayList<>(this.customArgumentResolvers);
         ConversionService service = (ConversionService)(this.conversionService != null ? this.conversionService : new DefaultFormattingConversionService());
         resolvers.add(new RequestHeaderArgumentResolver(service));
         resolvers.add(new RequestBodyArgumentResolver(this.exchangeAdapter));
         resolvers.add(new PathVariableArgumentResolver(service));
         resolvers.add(new RequestParamArgumentResolver(service));
         resolvers.add(new RequestPartArgumentResolver(this.exchangeAdapter));
         resolvers.add(new CookieValueArgumentResolver(service));
         if (this.exchangeAdapter.supportsRequestAttributes()) {
            resolvers.add(new RequestAttributeArgumentResolver());
         }

         resolvers.add(new UrlArgumentResolver());
         resolvers.add(new UriBuilderFactoryArgumentResolver());
         resolvers.add(new HttpMethodArgumentResolver());
         return resolvers;
      }
   }

   private record CompositeHttpRequestValuesProcessor(List<HttpRequestValues.Processor> processors) implements HttpRequestValues.Processor {
      @Override
      public void process(Method method, MethodParameter[] parameters, @Nullable Object[] arguments, HttpRequestValues.Builder builder) {
         for (HttpRequestValues.Processor processor : this.processors) {
            processor.process(method, parameters, arguments, builder);
         }
      }
   }

   private static final class HttpServiceMethodInterceptor implements MethodInterceptor {
      private final Map<Method, HttpServiceMethod> httpServiceMethods;

      private HttpServiceMethodInterceptor(List<HttpServiceMethod> methods) {
         this.httpServiceMethods = methods.stream().collect(Collectors.toMap(HttpServiceMethod::getMethod, Function.identity()));
      }

      public Object invoke(MethodInvocation invocation) throws Throwable {
         Method method = invocation.getMethod();
         HttpServiceMethod httpServiceMethod = this.httpServiceMethods.get(method);
         if (httpServiceMethod != null) {
            Object[] arguments = KotlinDetector.isSuspendingFunction(method)
               ? resolveCoroutinesArguments(invocation.getArguments())
               : invocation.getArguments();
            return httpServiceMethod.invoke(arguments);
         } else if (method.isDefault() && invocation instanceof ReflectiveMethodInvocation reflectiveMethodInvocation) {
            Object proxy = reflectiveMethodInvocation.getProxy();
            return InvocationHandler.invokeDefault(proxy, method, invocation.getArguments());
         } else {
            throw new IllegalStateException("Unexpected method invocation: " + method);
         }
      }

      private static Object[] resolveCoroutinesArguments(@Nullable Object[] args) {
         Object[] functionArgs = new Object[args.length - 1];
         System.arraycopy(args, 0, functionArgs, 0, args.length - 1);
         return functionArgs;
      }
   }

   public interface ProxyFactoryCustomizer {
      void customize(ProxyFactory proxyFactory, Class<?> serviceType);
   }
}
