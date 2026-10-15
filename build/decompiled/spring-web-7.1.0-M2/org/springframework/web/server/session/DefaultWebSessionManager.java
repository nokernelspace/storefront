package org.springframework.web.server.session;

import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultWebSessionManager implements WebSessionManager {
   private static final Log logger = LogFactory.getLog(DefaultWebSessionManager.class);
   private WebSessionIdResolver sessionIdResolver = new CookieWebSessionIdResolver();
   private WebSessionStore sessionStore = new InMemoryWebSessionStore();

   public void setSessionIdResolver(WebSessionIdResolver sessionIdResolver) {
      Assert.notNull(sessionIdResolver, "WebSessionIdResolver is required");
      this.sessionIdResolver = sessionIdResolver;
   }

   public WebSessionIdResolver getSessionIdResolver() {
      return this.sessionIdResolver;
   }

   public void setSessionStore(WebSessionStore sessionStore) {
      Assert.notNull(sessionStore, "WebSessionStore is required");
      this.sessionStore = sessionStore;
   }

   public WebSessionStore getSessionStore() {
      return this.sessionStore;
   }

   @Override
   public Mono<WebSession> getSession(ServerWebExchange exchange) {
      return Mono.defer(
         () -> this.retrieveSession(exchange)
            .switchIfEmpty(this.createWebSession())
            .doOnNext(session -> exchange.getResponse().beforeCommit(() -> this.save(exchange, session)))
      );
   }

   private Mono<WebSession> createWebSession() {
      Mono<WebSession> session = this.sessionStore.createWebSession();
      if (logger.isDebugEnabled()) {
         session = session.doOnNext(s -> logger.debug("Created new WebSession."));
      }

      return session;
   }

   private Mono<WebSession> retrieveSession(ServerWebExchange exchange) {
      return Flux.fromIterable(this.getSessionIdResolver().resolveSessionIds(exchange)).concatMap(this.sessionStore::retrieveSession).next();
   }

   private Mono<Void> save(ServerWebExchange exchange, WebSession session) {
      List<String> ids = this.getSessionIdResolver().resolveSessionIds(exchange);
      if (session.isStarted() && !session.isExpired()) {
         if (ids.isEmpty() || !session.getId().equals(ids.get(0))) {
            this.sessionIdResolver.setSessionId(exchange, session.getId());
         }

         return session.save();
      } else {
         if (!ids.isEmpty()) {
            if (logger.isDebugEnabled()) {
               logger.debug("WebSession expired or has been invalidated");
            }

            this.sessionIdResolver.expireSession(exchange);
         }

         return Mono.empty();
      }
   }
}
