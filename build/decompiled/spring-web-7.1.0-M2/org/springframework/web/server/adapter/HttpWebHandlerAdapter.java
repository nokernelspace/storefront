package org.springframework.web.server.adapter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerHttpObservationDocumentation;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationConvention;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.ExceptionHandlingWebHandler;
import org.springframework.web.server.handler.WebHandlerDecorator;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.util.DisconnectedClientHelper;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class HttpWebHandlerAdapter extends WebHandlerDecorator implements HttpHandler {
   private static final String DISCONNECTED_CLIENT_LOG_CATEGORY = "org.springframework.web.server.DisconnectedClient";
   private static final DisconnectedClientHelper disconnectedClientHelper = new DisconnectedClientHelper("org.springframework.web.server.DisconnectedClient");
   private static final ServerRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultServerRequestObservationConvention();
   private static final Log logger = LogFactory.getLog(HttpWebHandlerAdapter.class);
   private WebSessionManager sessionManager = new DefaultWebSessionManager();
   private @Nullable ServerCodecConfigurer codecConfigurer;
   private LocaleContextResolver localeContextResolver = new AcceptHeaderLocaleContextResolver();
   private @Nullable ForwardedHeaderTransformer forwardedHeaderTransformer;
   private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
   private ServerRequestObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;
   private @Nullable Boolean defaultHtmlEscape;
   private @Nullable ApplicationContext applicationContext;
   private boolean enableLoggingRequestDetails = false;

   public HttpWebHandlerAdapter(WebHandler delegate) {
      super(delegate);
   }

   public void setSessionManager(WebSessionManager sessionManager) {
      Assert.notNull(sessionManager, "WebSessionManager must not be null");
      this.sessionManager = sessionManager;
   }

   public WebSessionManager getSessionManager() {
      return this.sessionManager;
   }

   public void setCodecConfigurer(ServerCodecConfigurer codecConfigurer) {
      Assert.notNull(codecConfigurer, "ServerCodecConfigurer is required");
      this.codecConfigurer = codecConfigurer;
      this.enableLoggingRequestDetails = false;
      this.codecConfigurer.getReaders().stream().filter(LoggingCodecSupport.class::isInstance).forEach(reader -> {
         if (((LoggingCodecSupport)reader).isEnableLoggingRequestDetails()) {
            this.enableLoggingRequestDetails = true;
         }
      });
   }

   public ServerCodecConfigurer getCodecConfigurer() {
      ServerCodecConfigurer codecConfigurer = this.codecConfigurer;
      if (codecConfigurer == null) {
         codecConfigurer = ServerCodecConfigurer.create();
         this.setCodecConfigurer(codecConfigurer);
      }

      return codecConfigurer;
   }

   public void setLocaleContextResolver(LocaleContextResolver resolver) {
      Assert.notNull(resolver, "LocaleContextResolver is required");
      this.localeContextResolver = resolver;
   }

   public LocaleContextResolver getLocaleContextResolver() {
      return this.localeContextResolver;
   }

   public void setForwardedHeaderTransformer(@Nullable ForwardedHeaderTransformer transformer) {
      this.forwardedHeaderTransformer = transformer;
   }

   public @Nullable ForwardedHeaderTransformer getForwardedHeaderTransformer() {
      return this.forwardedHeaderTransformer;
   }

   public void setObservationRegistry(ObservationRegistry observationRegistry) {
      this.observationRegistry = observationRegistry;
   }

   public ObservationRegistry getObservationRegistry() {
      return this.observationRegistry;
   }

   public void setObservationConvention(ServerRequestObservationConvention observationConvention) {
      this.observationConvention = observationConvention;
   }

   public ServerRequestObservationConvention getObservationConvention() {
      return this.observationConvention;
   }

   public void setDefaultHtmlEscape(Boolean defaultHtmlEscape) {
      this.defaultHtmlEscape = defaultHtmlEscape;
   }

   public @Nullable Boolean getDefaultHtmlEscape() {
      return this.defaultHtmlEscape;
   }

   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public @Nullable ApplicationContext getApplicationContext() {
      return this.applicationContext;
   }

   public void afterPropertiesSet() {
      if (logger.isDebugEnabled()) {
         String value = this.enableLoggingRequestDetails
            ? "shown which may lead to unsafe logging of potentially sensitive data"
            : "masked to prevent unsafe logging of potentially sensitive data";
         logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails + "': form data and headers will be " + value);
      }
   }

   @Override
   public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
      if (this.forwardedHeaderTransformer != null) {
         try {
            request = this.forwardedHeaderTransformer.apply(request);
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to apply forwarded headers to " + this.formatRequest(request), ex);
            }

            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return response.setComplete();
         }
      }

      ServerWebExchange exchange = this.createExchange(request, response);
      LogFormatUtils.traceDebug(
         logger,
         traceOn -> exchange.getLogPrefix()
            + this.formatRequest(exchange.getRequest())
            + (traceOn ? ", headers=" + this.formatHeaders(exchange.getRequest().getHeaders()) : "")
      );
      ServerRequestObservationContext observationContext = new ServerRequestObservationContext(
         exchange.getRequest(), exchange.getResponse(), exchange.getAttributes()
      );
      exchange.getAttributes().put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observationContext);
      if (this.defaultHtmlEscape != null) {
         exchange.getAttributes().put(ServerWebExchange.HTML_ESCAPE_ATTRIBUTE, this.defaultHtmlEscape);
      }

      return this.getDelegate()
         .handle(exchange)
         .doOnSuccess(aVoid -> this.logResponse(exchange))
         .onErrorResume(ex -> this.handleUnresolvedError(exchange, observationContext, ex))
         .tap(() -> new HttpWebHandlerAdapter.ObservationSignalListener(observationContext))
         .then(exchange.cleanupMultipart())
         .then(Mono.defer(response::setComplete));
   }

   protected ServerWebExchange createExchange(ServerHttpRequest request, ServerHttpResponse response) {
      return new DefaultServerWebExchange(
         request, response, this.sessionManager, this.getCodecConfigurer(), this.getLocaleContextResolver(), this.applicationContext
      );
   }

   protected String formatRequest(ServerHttpRequest request) {
      String rawQuery = request.getURI().getRawQuery();
      String query = StringUtils.hasText(rawQuery) ? "?" + rawQuery : "";
      return "HTTP " + request.getMethod() + " \"" + request.getPath() + query + "\"";
   }

   private void logResponse(ServerWebExchange exchange) {
      LogFormatUtils.traceDebug(
         logger,
         traceOn -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            return exchange.getLogPrefix()
               + "Completed "
               + (status != null ? status : "200 OK")
               + (traceOn ? ", headers=" + this.formatHeaders(exchange.getResponse().getHeaders()) : "");
         }
      );
   }

   private String formatHeaders(HttpHeaders responseHeaders) {
      return this.enableLoggingRequestDetails ? responseHeaders.toString() : (responseHeaders.isEmpty() ? "{}" : "{masked}");
   }

   private Mono<Void> handleUnresolvedError(ServerWebExchange exchange, ServerRequestObservationContext observationContext, Throwable ex) {
      ServerHttpRequest request = exchange.getRequest();
      ServerHttpResponse response = exchange.getResponse();
      String logPrefix = exchange.getLogPrefix();
      if (disconnectedClientHelper.checkAndLogClientDisconnectedException(ex)) {
         response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
         observationContext.setConnectionAborted(true);
         return Mono.empty();
      } else if (response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR)) {
         logger.error(logPrefix + "500 Server Error for " + this.formatRequest(request), ex);
         return Mono.empty();
      } else {
         logger.error(
            logPrefix
               + "Error ["
               + ex
               + "] for "
               + this.formatRequest(request)
               + ", but ServerHttpResponse already committed ("
               + response.getStatusCode()
               + ")"
         );
         return Mono.error(ex);
      }
   }

   private final class ObservationSignalListener extends DefaultSignalListener<Void> {
      private final ServerRequestObservationContext observationContext;
      private final Observation observation;
      private final AtomicBoolean observationRecorded = new AtomicBoolean();

      ObservationSignalListener(ServerRequestObservationContext observationContext) {
         this.observationContext = observationContext;
         this.observation = ServerHttpObservationDocumentation.HTTP_REACTIVE_SERVER_REQUESTS
            .observation(
               HttpWebHandlerAdapter.this.observationConvention,
               HttpWebHandlerAdapter.DEFAULT_OBSERVATION_CONVENTION,
               () -> observationContext,
               HttpWebHandlerAdapter.this.observationRegistry
            );
      }

      public Context addToContext(Context originalContext) {
         return originalContext.put("micrometer.observation", this.observation);
      }

      public void doFirst() throws Throwable {
         this.observation.start();
      }

      public void doOnCancel() throws Throwable {
         if (this.observationRecorded.compareAndSet(false, true)) {
            this.observationContext.setConnectionAborted(true);
            this.observation.stop();
         }
      }

      public void doOnComplete() throws Throwable {
         if (this.observationRecorded.compareAndSet(false, true)) {
            Throwable throwable = (Throwable)this.observationContext.getAttributes().get(ExceptionHandlingWebHandler.HANDLED_WEB_EXCEPTION);
            if (throwable != null) {
               this.observation.error(throwable);
            }

            this.doOnTerminate(this.observationContext);
         }
      }

      public void doOnError(Throwable error) throws Throwable {
         if (this.observationRecorded.compareAndSet(false, true)) {
            this.observationContext.setError(error);
            this.doOnTerminate(this.observationContext);
         }
      }

      private void doOnTerminate(ServerRequestObservationContext context) {
         ServerHttpResponse response = (ServerHttpResponse)context.getResponse();
         if (response != null) {
            if (response.isCommitted()) {
               this.observation.stop();
            } else {
               response.beforeCommit(() -> {
                  this.observation.stop();
                  return Mono.empty();
               });
            }
         }
      }
   }
}
