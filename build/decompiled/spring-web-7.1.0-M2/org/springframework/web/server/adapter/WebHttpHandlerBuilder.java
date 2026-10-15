package org.springframework.web.server.adapter;

import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.HttpHandlerDecoratorFactory;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.ExceptionHandlingWebHandler;
import org.springframework.web.server.handler.FilteringWebHandler;
import org.springframework.web.server.i18n.LocaleContextResolver;
import org.springframework.web.server.session.WebSessionManager;
import reactor.blockhound.BlockHound.Builder;
import reactor.blockhound.integration.BlockHoundIntegration;

public final class WebHttpHandlerBuilder {
   public static final String WEB_HANDLER_BEAN_NAME = "webHandler";
   public static final String WEB_SESSION_MANAGER_BEAN_NAME = "webSessionManager";
   public static final String SERVER_CODEC_CONFIGURER_BEAN_NAME = "serverCodecConfigurer";
   public static final String LOCALE_CONTEXT_RESOLVER_BEAN_NAME = "localeContextResolver";
   public static final String FORWARDED_HEADER_TRANSFORMER_BEAN_NAME = "forwardedHeaderTransformer";
   private final WebHandler webHandler;
   private final @Nullable ApplicationContext applicationContext;
   private final List<WebFilter> filters = new ArrayList<>();
   private final List<WebExceptionHandler> exceptionHandlers = new ArrayList<>();
   private @Nullable Function<HttpHandler, HttpHandler> httpHandlerDecorator;
   private @Nullable WebSessionManager sessionManager;
   private @Nullable ServerCodecConfigurer codecConfigurer;
   private @Nullable LocaleContextResolver localeContextResolver;
   private @Nullable ForwardedHeaderTransformer forwardedHeaderTransformer;
   private @Nullable ObservationRegistry observationRegistry;
   private @Nullable ServerRequestObservationConvention observationConvention;
   private @Nullable Boolean defaultHtmlEscape;

   private WebHttpHandlerBuilder(WebHandler webHandler, @Nullable ApplicationContext applicationContext) {
      Assert.notNull(webHandler, "WebHandler must not be null");
      this.webHandler = webHandler;
      this.applicationContext = applicationContext;
   }

   private WebHttpHandlerBuilder(WebHttpHandlerBuilder other) {
      this.webHandler = other.webHandler;
      this.applicationContext = other.applicationContext;
      this.filters.addAll(other.filters);
      this.exceptionHandlers.addAll(other.exceptionHandlers);
      this.httpHandlerDecorator = other.httpHandlerDecorator;
      this.sessionManager = other.sessionManager;
      this.codecConfigurer = other.codecConfigurer;
      this.localeContextResolver = other.localeContextResolver;
      this.forwardedHeaderTransformer = other.forwardedHeaderTransformer;
      this.observationRegistry = other.observationRegistry;
      this.observationConvention = other.observationConvention;
      this.defaultHtmlEscape = other.defaultHtmlEscape;
   }

   public static WebHttpHandlerBuilder webHandler(WebHandler webHandler) {
      return new WebHttpHandlerBuilder(webHandler, null);
   }

   public static WebHttpHandlerBuilder applicationContext(ApplicationContext context) {
      WebHttpHandlerBuilder builder = new WebHttpHandlerBuilder((WebHandler)context.getBean("webHandler", WebHandler.class), context);
      List<WebFilter> webFilters = context.getBeanProvider(WebFilter.class).orderedStream().toList();
      builder.filters(filters -> filters.addAll(webFilters));
      List<WebExceptionHandler> exceptionHandlers = context.getBeanProvider(WebExceptionHandler.class).orderedStream().toList();
      builder.exceptionHandlers(handlers -> handlers.addAll(exceptionHandlers));
      context.getBeanProvider(HttpHandlerDecoratorFactory.class).orderedStream().forEach(builder::httpHandlerDecorator);
      context.getBeanProvider(ObservationRegistry.class).ifUnique(builder::observationRegistry);
      context.getBeanProvider(ServerRequestObservationConvention.class).ifAvailable(builder::observationConvention);

      try {
         builder.sessionManager((WebSessionManager)context.getBean("webSessionManager", WebSessionManager.class));
      } catch (NoSuchBeanDefinitionException var8) {
      }

      try {
         builder.codecConfigurer((ServerCodecConfigurer)context.getBean("serverCodecConfigurer", ServerCodecConfigurer.class));
      } catch (NoSuchBeanDefinitionException var7) {
      }

      try {
         builder.localeContextResolver((LocaleContextResolver)context.getBean("localeContextResolver", LocaleContextResolver.class));
      } catch (NoSuchBeanDefinitionException var6) {
      }

      try {
         builder.forwardedHeaderTransformer((ForwardedHeaderTransformer)context.getBean("forwardedHeaderTransformer", ForwardedHeaderTransformer.class));
      } catch (NoSuchBeanDefinitionException var5) {
      }

      return builder;
   }

   public WebHttpHandlerBuilder filter(WebFilter... filters) {
      if (!ObjectUtils.isEmpty(filters)) {
         this.filters.addAll(Arrays.asList(filters));
      }

      return this;
   }

   public WebHttpHandlerBuilder filters(Consumer<List<WebFilter>> consumer) {
      consumer.accept(this.filters);
      return this;
   }

   public WebHttpHandlerBuilder exceptionHandler(WebExceptionHandler... handlers) {
      if (!ObjectUtils.isEmpty(handlers)) {
         this.exceptionHandlers.addAll(Arrays.asList(handlers));
      }

      return this;
   }

   public WebHttpHandlerBuilder exceptionHandlers(Consumer<List<WebExceptionHandler>> consumer) {
      consumer.accept(this.exceptionHandlers);
      return this;
   }

   public WebHttpHandlerBuilder httpHandlerDecorator(Function<HttpHandler, HttpHandler> handlerDecorator) {
      this.httpHandlerDecorator = this.httpHandlerDecorator != null ? handlerDecorator.andThen(this.httpHandlerDecorator) : handlerDecorator;
      return this;
   }

   public boolean hasHttpHandlerDecorator() {
      return this.httpHandlerDecorator != null;
   }

   public WebHttpHandlerBuilder sessionManager(WebSessionManager manager) {
      this.sessionManager = manager;
      return this;
   }

   public boolean hasSessionManager() {
      return this.sessionManager != null;
   }

   public WebHttpHandlerBuilder codecConfigurer(ServerCodecConfigurer codecConfigurer) {
      this.codecConfigurer = codecConfigurer;
      return this;
   }

   public boolean hasCodecConfigurer() {
      return this.codecConfigurer != null;
   }

   public WebHttpHandlerBuilder localeContextResolver(LocaleContextResolver localeContextResolver) {
      this.localeContextResolver = localeContextResolver;
      return this;
   }

   public boolean hasLocaleContextResolver() {
      return this.localeContextResolver != null;
   }

   public WebHttpHandlerBuilder forwardedHeaderTransformer(ForwardedHeaderTransformer transformer) {
      this.forwardedHeaderTransformer = transformer;
      return this;
   }

   public boolean hasForwardedHeaderTransformer() {
      return this.forwardedHeaderTransformer != null;
   }

   public WebHttpHandlerBuilder observationRegistry(ObservationRegistry observationRegistry) {
      this.observationRegistry = observationRegistry;
      return this;
   }

   public WebHttpHandlerBuilder observationConvention(ServerRequestObservationConvention observationConvention) {
      this.observationConvention = observationConvention;
      return this;
   }

   public WebHttpHandlerBuilder defaultHtmlEscape(@Nullable Boolean defaultHtmlEscape) {
      this.defaultHtmlEscape = defaultHtmlEscape;
      return this;
   }

   public @Nullable Boolean getDefaultHtmlEscape() {
      return this.defaultHtmlEscape;
   }

   public HttpHandler build() {
      WebHandler decorated = new FilteringWebHandler(this.webHandler, this.filters);
      decorated = new ExceptionHandlingWebHandler(decorated, this.exceptionHandlers);
      HttpWebHandlerAdapter adapted = new HttpWebHandlerAdapter(decorated);
      if (this.sessionManager != null) {
         adapted.setSessionManager(this.sessionManager);
      }

      if (this.codecConfigurer != null) {
         adapted.setCodecConfigurer(this.codecConfigurer);
      }

      if (this.localeContextResolver != null) {
         adapted.setLocaleContextResolver(this.localeContextResolver);
      }

      if (this.forwardedHeaderTransformer != null) {
         adapted.setForwardedHeaderTransformer(this.forwardedHeaderTransformer);
      }

      if (this.observationRegistry != null) {
         adapted.setObservationRegistry(this.observationRegistry);
      }

      if (this.observationConvention != null) {
         adapted.setObservationConvention(this.observationConvention);
      }

      if (this.applicationContext != null) {
         adapted.setApplicationContext(this.applicationContext);
      }

      if (this.defaultHtmlEscape != null) {
         adapted.setDefaultHtmlEscape(this.defaultHtmlEscape);
      }

      adapted.afterPropertiesSet();
      return this.httpHandlerDecorator != null ? this.httpHandlerDecorator.apply(adapted) : adapted;
   }

   public WebHttpHandlerBuilder clone() {
      return new WebHttpHandlerBuilder(this);
   }

   public static class SpringWebBlockHoundIntegration implements BlockHoundIntegration {
      public void applyTo(Builder builder) {
         builder.allowBlockingCallsInside("org.springframework.http.MediaTypeFactory", "<clinit>");
         builder.allowBlockingCallsInside("org.springframework.web.util.HtmlUtils", "<clinit>");
      }
   }
}
