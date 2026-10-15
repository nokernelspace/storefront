package org.springframework.web.server.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.util.JdkIdGenerator;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class InMemoryWebSessionStore implements WebSessionStore {
   private static final IdGenerator idGenerator = new JdkIdGenerator();
   private int maxSessions = 10000;
   private Duration defaultMaxIdleTime = Duration.ofMinutes(30L);
   private Clock clock = Clock.systemUTC();
   private final Map<String, InMemoryWebSessionStore.InMemoryWebSession> sessions = new ConcurrentHashMap<>();
   private final InMemoryWebSessionStore.ExpiredSessionChecker expiredSessionChecker = new InMemoryWebSessionStore.ExpiredSessionChecker();

   public void setMaxSessions(int maxSessions) {
      this.maxSessions = maxSessions;
   }

   public int getMaxSessions() {
      return this.maxSessions;
   }

   public void setDefaultMaxIdleTime(Duration defaultMaxIdleTime) {
      Assert.notNull(defaultMaxIdleTime, "maxIdleTime is required");
      this.defaultMaxIdleTime = defaultMaxIdleTime;
   }

   public Duration getDefaultMaxIdleTime() {
      return this.defaultMaxIdleTime;
   }

   public void setClock(Clock clock) {
      Assert.notNull(clock, "Clock is required");
      this.clock = clock;
      this.removeExpiredSessions();
   }

   public Clock getClock() {
      return this.clock;
   }

   public Map<String, WebSession> getSessions() {
      return Collections.unmodifiableMap(this.sessions);
   }

   @Override
   public Mono<WebSession> createWebSession() {
      Instant now = this.clock.instant();
      this.expiredSessionChecker.checkIfNecessary(now);
      return Mono.fromSupplier(() -> new InMemoryWebSessionStore.InMemoryWebSession(now, this.defaultMaxIdleTime))
         .subscribeOn(Schedulers.boundedElastic())
         .publishOn(Schedulers.parallel());
   }

   @Override
   public Mono<WebSession> retrieveSession(String id) {
      Instant now = this.clock.instant();
      this.expiredSessionChecker.checkIfNecessary(now);
      InMemoryWebSessionStore.InMemoryWebSession session = this.sessions.get(id);
      if (session == null) {
         return Mono.empty();
      } else if (session.isExpired(now)) {
         this.sessions.remove(id);
         return Mono.empty();
      } else {
         session.updateLastAccessTime(now);
         return Mono.just(session);
      }
   }

   @Override
   public Mono<Void> removeSession(String id) {
      this.sessions.remove(id);
      return Mono.empty();
   }

   @Override
   public Mono<WebSession> updateLastAccessTime(WebSession session) {
      return Mono.fromSupplier(() -> {
         Assert.isInstanceOf(InMemoryWebSessionStore.InMemoryWebSession.class, session);
         ((InMemoryWebSessionStore.InMemoryWebSession)session).updateLastAccessTime(this.clock.instant());
         return session;
      });
   }

   public void removeExpiredSessions() {
      this.expiredSessionChecker.removeExpiredSessions(this.clock.instant());
   }

   private class ExpiredSessionChecker {
      private static final int CHECK_PERIOD = 60000;
      private final Lock lock = new ReentrantLock();
      private Instant checkTime = InMemoryWebSessionStore.this.clock.instant().plus(60000L, ChronoUnit.MILLIS);

      public void checkIfNecessary(Instant now) {
         if (this.checkTime.isBefore(now)) {
            this.removeExpiredSessions(now);
         }
      }

      public void removeExpiredSessions(Instant now) {
         if (!InMemoryWebSessionStore.this.sessions.isEmpty()) {
            if (this.lock.tryLock()) {
               try {
                  Iterator<InMemoryWebSessionStore.InMemoryWebSession> iterator = InMemoryWebSessionStore.this.sessions.values().iterator();

                  while (iterator.hasNext()) {
                     InMemoryWebSessionStore.InMemoryWebSession session = iterator.next();
                     if (session.isExpired(now)) {
                        iterator.remove();
                        session.invalidate();
                     }
                  }
               } finally {
                  this.checkTime = now.plus(60000L, ChronoUnit.MILLIS);
                  this.lock.unlock();
               }
            }
         }
      }
   }

   private class InMemoryWebSession implements WebSession {
      private final AtomicReference<String> id = new AtomicReference<>(String.valueOf(InMemoryWebSessionStore.idGenerator.generateId()));
      private final Map<String, Object> attributes = new ConcurrentHashMap<>();
      private final Instant creationTime;
      private volatile Instant lastAccessTime;
      private volatile Duration maxIdleTime;
      private final AtomicReference<InMemoryWebSessionStore.State> state = new AtomicReference<>(InMemoryWebSessionStore.State.NEW);
      private final Lock lock = new ReentrantLock();

      public InMemoryWebSession(Instant creationTime, Duration maxIdleTime) {
         this.creationTime = creationTime;
         this.lastAccessTime = this.creationTime;
         this.maxIdleTime = maxIdleTime;
      }

      @Override
      public String getId() {
         return this.id.get();
      }

      @Override
      public Map<String, Object> getAttributes() {
         return this.attributes;
      }

      @Override
      public Instant getCreationTime() {
         return this.creationTime;
      }

      @Override
      public Instant getLastAccessTime() {
         return this.lastAccessTime;
      }

      @Override
      public void setMaxIdleTime(Duration maxIdleTime) {
         this.maxIdleTime = maxIdleTime;
      }

      @Override
      public Duration getMaxIdleTime() {
         return this.maxIdleTime;
      }

      @Override
      public void start() {
         this.state.compareAndSet(InMemoryWebSessionStore.State.NEW, InMemoryWebSessionStore.State.STARTED);
      }

      @Override
      public boolean isStarted() {
         return this.state.get().equals(InMemoryWebSessionStore.State.STARTED) || !this.getAttributes().isEmpty();
      }

      @Override
      public Mono<Void> save() {
         this.checkMaxSessionsLimit();
         if (!this.getAttributes().isEmpty()) {
            this.state.compareAndSet(InMemoryWebSessionStore.State.NEW, InMemoryWebSessionStore.State.STARTED);
         }

         if (this.isStarted()) {
            if (InMemoryWebSessionStore.this.sessions.get(this.getId()) == null) {
               this.lock.lock();

               try {
                  InMemoryWebSessionStore.this.sessions.putIfAbsent(this.getId(), this);
               } finally {
                  this.lock.unlock();
               }
            }

            if (this.state.get().equals(InMemoryWebSessionStore.State.EXPIRED)) {
               InMemoryWebSessionStore.this.sessions.remove(this.getId());
               return Mono.error(new IllegalStateException("Session was invalidated"));
            }
         }

         return Mono.empty();
      }

      private void checkMaxSessionsLimit() {
         if (InMemoryWebSessionStore.this.sessions.size() >= InMemoryWebSessionStore.this.maxSessions) {
            InMemoryWebSessionStore.this.expiredSessionChecker.removeExpiredSessions(InMemoryWebSessionStore.this.clock.instant());
            if (InMemoryWebSessionStore.this.sessions.size() >= InMemoryWebSessionStore.this.maxSessions
               && !InMemoryWebSessionStore.this.sessions.containsKey(this.getId())) {
               throw new IllegalStateException("Max sessions limit reached: " + InMemoryWebSessionStore.this.sessions.size());
            }
         }
      }

      @Override
      public Mono<Void> changeSessionId() {
         return Mono.defer(() -> {
            this.lock.lock();

            try {
               String oldId = this.getId();
               String newId = String.valueOf(InMemoryWebSessionStore.idGenerator.generateId());
               InMemoryWebSessionStore.this.sessions.remove(oldId);
               InMemoryWebSessionStore.this.sessions.put(newId, this);
               this.id.set(newId);
            } finally {
               this.lock.unlock();
            }

            return Mono.empty();
         }).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()).then();
      }

      @Override
      public Mono<Void> invalidate() {
         this.state.set(InMemoryWebSessionStore.State.EXPIRED);
         this.getAttributes().clear();
         InMemoryWebSessionStore.this.sessions.remove(this.getId());
         return Mono.empty();
      }

      @Override
      public boolean isExpired() {
         return this.isExpired(InMemoryWebSessionStore.this.clock.instant());
      }

      private boolean isExpired(Instant now) {
         if (this.state.get().equals(InMemoryWebSessionStore.State.EXPIRED)) {
            return true;
         } else if (this.checkExpired(now)) {
            this.state.set(InMemoryWebSessionStore.State.EXPIRED);
            return true;
         } else {
            return false;
         }
      }

      private boolean checkExpired(Instant currentTime) {
         return this.isStarted() && !this.maxIdleTime.isNegative() && currentTime.minus(this.maxIdleTime).isAfter(this.lastAccessTime);
      }

      private void updateLastAccessTime(Instant currentTime) {
         this.lastAccessTime = currentTime;
      }
   }

   private enum State {
      NEW,
      STARTED,
      EXPIRED;
   }
}
