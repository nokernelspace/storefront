package org.springframework.web.server;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public class ServerWebExchangeDecorator implements ServerWebExchange {
   private final ServerWebExchange delegate;

   protected ServerWebExchangeDecorator(ServerWebExchange delegate) {
      Assert.notNull(delegate, "ServerWebExchange 'delegate' is required.");
      this.delegate = delegate;
   }

   public ServerWebExchange getDelegate() {
      return this.delegate;
   }

   @Override
   public ServerHttpRequest getRequest() {
      return this.getDelegate().getRequest();
   }

   @Override
   public ServerHttpResponse getResponse() {
      return this.getDelegate().getResponse();
   }

   @Override
   public Map<String, Object> getAttributes() {
      return this.getDelegate().getAttributes();
   }

   @Override
   public Mono<WebSession> getSession() {
      return this.getDelegate().getSession();
   }

   @Override
   public <T extends Principal> Mono<T> getPrincipal() {
      return this.getDelegate().getPrincipal();
   }

   @Override
   public LocaleContext getLocaleContext() {
      return this.getDelegate().getLocaleContext();
   }

   @Override
   public @Nullable ApplicationContext getApplicationContext() {
      return this.getDelegate().getApplicationContext();
   }

   @Override
   public Mono<MultiValueMap<String, String>> getFormData() {
      return this.getDelegate().getFormData();
   }

   @Override
   public Mono<MultiValueMap<String, Part>> getMultipartData() {
      return this.getDelegate().getMultipartData();
   }

   @Override
   public Mono<Void> cleanupMultipart() {
      return this.getDelegate().cleanupMultipart();
   }

   @Override
   public boolean isNotModified() {
      return this.getDelegate().isNotModified();
   }

   @Override
   public boolean checkNotModified(Instant lastModified) {
      return this.getDelegate().checkNotModified(lastModified);
   }

   @Override
   public boolean checkNotModified(String etag) {
      return this.getDelegate().checkNotModified(etag);
   }

   @Override
   public boolean checkNotModified(@Nullable String etag, Instant lastModified) {
      return this.getDelegate().checkNotModified(etag, lastModified);
   }

   @Override
   public String transformUrl(String url) {
      return this.getDelegate().transformUrl(url);
   }

   @Override
   public void addUrlTransformer(Function<String, String> transformer) {
      this.getDelegate().addUrlTransformer(transformer);
   }

   @Override
   public String getLogPrefix() {
      return this.getDelegate().getLogPrefix();
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [delegate=" + this.getDelegate() + "]";
   }
}
