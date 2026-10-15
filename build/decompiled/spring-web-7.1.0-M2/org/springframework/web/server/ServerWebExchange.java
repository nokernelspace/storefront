package org.springframework.web.server;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
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

public interface ServerWebExchange {
   String HTML_ESCAPE_ATTRIBUTE = ServerWebExchange.class.getName() + ".HTML_ESCAPE";
   String LOG_ID_ATTRIBUTE = ServerWebExchange.class.getName() + ".LOG_ID";

   ServerHttpRequest getRequest();

   ServerHttpResponse getResponse();

   Map<String, Object> getAttributes();

   default <T> @Nullable T getAttribute(String name) {
      return (T)this.getAttributes().get(name);
   }

   default <T> T getRequiredAttribute(String name) {
      T value = this.getAttribute(name);
      Assert.notNull(value, () -> "Required attribute '" + name + "' is missing");
      return value;
   }

   default <T> T getAttributeOrDefault(String name, T defaultValue) {
      return (T)this.getAttributes().getOrDefault(name, defaultValue);
   }

   Mono<WebSession> getSession();

   <T extends Principal> Mono<T> getPrincipal();

   Mono<MultiValueMap<String, String>> getFormData();

   Mono<MultiValueMap<String, Part>> getMultipartData();

   default Mono<Void> cleanupMultipart() {
      return this.getMultipartData()
         .onErrorComplete()
         .flatMapIterable(Map::values)
         .flatMapIterable(Function.identity())
         .flatMap(part -> part.delete().onErrorComplete())
         .then();
   }

   LocaleContext getLocaleContext();

   @Nullable ApplicationContext getApplicationContext();

   boolean isNotModified();

   boolean checkNotModified(Instant lastModified);

   boolean checkNotModified(String etag);

   boolean checkNotModified(@Nullable String etag, Instant lastModified);

   String transformUrl(String url);

   void addUrlTransformer(Function<String, String> transformer);

   String getLogPrefix();

   default ServerWebExchange.Builder mutate() {
      return new DefaultServerWebExchangeBuilder(this);
   }

   interface Builder {
      ServerWebExchange.Builder request(Consumer<ServerHttpRequest.Builder> requestBuilderConsumer);

      ServerWebExchange.Builder request(ServerHttpRequest request);

      ServerWebExchange.Builder response(ServerHttpResponse response);

      ServerWebExchange.Builder principal(Mono<Principal> principalMono);

      ServerWebExchange build();
   }
}
