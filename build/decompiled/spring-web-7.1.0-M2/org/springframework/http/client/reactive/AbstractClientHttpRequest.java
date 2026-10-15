package org.springframework.http.client.reactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractClientHttpRequest implements ClientHttpRequest {
   private final HttpHeaders headers;
   private final MultiValueMap<String, HttpCookie> cookies;
   private final Map<String, Object> attributes;
   private final AtomicReference<AbstractClientHttpRequest.State> state = new AtomicReference<>(AbstractClientHttpRequest.State.NEW);
   private final List<Supplier<? extends Publisher<Void>>> commitActions = new ArrayList<>(4);
   private @Nullable HttpHeaders readOnlyHeaders;

   public AbstractClientHttpRequest() {
      this(new HttpHeaders());
   }

   public AbstractClientHttpRequest(HttpHeaders headers) {
      Assert.notNull(headers, "HttpHeaders must not be null");
      this.headers = headers;
      this.cookies = new LinkedMultiValueMap();
      this.attributes = new LinkedHashMap<>();
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.readOnlyHeaders != null) {
         return this.readOnlyHeaders;
      } else if (AbstractClientHttpRequest.State.COMMITTED.equals(this.state.get())) {
         this.readOnlyHeaders = this.initReadOnlyHeaders();
         return this.readOnlyHeaders;
      } else {
         return this.headers;
      }
   }

   protected HttpHeaders initReadOnlyHeaders() {
      return HttpHeaders.readOnlyHttpHeaders(this.headers);
   }

   @Override
   public MultiValueMap<String, HttpCookie> getCookies() {
      return AbstractClientHttpRequest.State.COMMITTED.equals(this.state.get()) ? CollectionUtils.unmodifiableMultiValueMap(this.cookies) : this.cookies;
   }

   @Override
   public Map<String, Object> getAttributes() {
      return AbstractClientHttpRequest.State.COMMITTED.equals(this.state.get()) ? Collections.unmodifiableMap(this.attributes) : this.attributes;
   }

   @Override
   public void beforeCommit(Supplier<? extends Mono<Void>> action) {
      Assert.notNull(action, "Action must not be null");
      this.commitActions.add(action);
   }

   @Override
   public boolean isCommitted() {
      return this.state.get() != AbstractClientHttpRequest.State.NEW;
   }

   protected Mono<Void> doCommit() {
      return this.doCommit(null);
   }

   protected Mono<Void> doCommit(@Nullable Supplier<? extends Publisher<Void>> writeAction) {
      if (!this.state.compareAndSet(AbstractClientHttpRequest.State.NEW, AbstractClientHttpRequest.State.COMMITTING)) {
         return Mono.empty();
      }

      this.commitActions.add(() -> Mono.fromRunnable(() -> {
         this.applyHeaders();
         this.applyCookies();
         this.applyAttributes();
         this.state.set(AbstractClientHttpRequest.State.COMMITTED);
      }));
      if (writeAction != null) {
         this.commitActions.add(writeAction);
      }

      List<Publisher<Void>> actions = new ArrayList<>(this.commitActions.size());

      for (Supplier<? extends Publisher<Void>> commitAction : this.commitActions) {
         actions.add((Publisher<Void>)commitAction.get());
      }

      return Flux.concat(actions).then();
   }

   protected abstract void applyHeaders();

   protected abstract void applyCookies();

   protected void applyAttributes() {
   }

   private enum State {
      NEW,
      COMMITTING,
      COMMITTED;
   }
}
