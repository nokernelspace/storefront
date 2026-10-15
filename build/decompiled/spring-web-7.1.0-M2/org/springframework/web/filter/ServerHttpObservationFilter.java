package org.springframework.web.filter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation.Scope;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerHttpObservationDocumentation;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

public class ServerHttpObservationFilter extends OncePerRequestFilter {
   public static final String CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE = ServerHttpObservationFilter.class.getName() + ".context";
   private static final String CURRENT_OBSERVATION_ATTRIBUTE = ServerHttpObservationFilter.class.getName() + ".observation";
   private static final ServerRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultServerRequestObservationConvention();
   private final ObservationRegistry observationRegistry;
   private final ServerRequestObservationConvention observationConvention;

   public ServerHttpObservationFilter(ObservationRegistry observationRegistry) {
      this(observationRegistry, DEFAULT_OBSERVATION_CONVENTION);
   }

   public ServerHttpObservationFilter(ObservationRegistry observationRegistry, ServerRequestObservationConvention observationConvention) {
      this.observationRegistry = observationRegistry;
      this.observationConvention = observationConvention;
   }

   @Override
   protected boolean shouldNotFilterAsyncDispatch() {
      return false;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      Observation observation = this.createOrFetchObservation(request, response);

      try {
         Scope scope = observation.openScope();

         try {
            this.onScopeOpened(scope, request, response);
            filterChain.doFilter(request, response);
         } catch (Throwable var15) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var14) {
                  var15.addSuppressed(var14);
               }
            }

            throw var15;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Exception ex) {
         observation.error(unwrapServletException(ex));
         response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
         throw ex;
      } finally {
         if (request.isAsyncStarted() && request.getDispatcherType() == DispatcherType.REQUEST) {
            request.getAsyncContext().addListener(new ServerHttpObservationFilter.ObservationAsyncListener(observation));
         } else if (!this.isAsyncDispatch(request)) {
            Throwable error = fetchException(request);
            if (error != null) {
               observation.error(error);
            }

            observation.stop();
         }
      }
   }

   protected void onScopeOpened(Scope scope, HttpServletRequest request, HttpServletResponse response) {
   }

   private Observation createOrFetchObservation(HttpServletRequest request, HttpServletResponse response) {
      Observation observation = (Observation)request.getAttribute(CURRENT_OBSERVATION_ATTRIBUTE);
      if (observation == null) {
         ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
         observation = ServerHttpObservationDocumentation.HTTP_SERVLET_SERVER_REQUESTS
            .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> context, this.observationRegistry)
            .start();
         request.setAttribute(CURRENT_OBSERVATION_ATTRIBUTE, observation);
         if (!observation.isNoop()) {
            request.setAttribute(CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation.getContext());
         }
      }

      return observation;
   }

   public static Optional<ServerRequestObservationContext> findObservationContext(HttpServletRequest request) {
      return Optional.ofNullable((ServerRequestObservationContext)request.getAttribute(CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE));
   }

   static @Nullable Throwable fetchException(ServletRequest request) {
      return (Throwable)request.getAttribute("jakarta.servlet.error.exception");
   }

   static Throwable unwrapServletException(Throwable ex) {
      if (ex instanceof ServletException) {
         Throwable cause = ex.getCause();
         if (cause != null) {
            return cause;
         }
      }

      return ex;
   }

   private static class ObservationAsyncListener implements AsyncListener {
      private final Observation currentObservation;

      public ObservationAsyncListener(Observation currentObservation) {
         this.currentObservation = currentObservation;
      }

      public void onStartAsync(AsyncEvent event) {
         event.getAsyncContext().addListener(this);
      }

      public void onTimeout(AsyncEvent event) {
         this.currentObservation.stop();
      }

      public void onComplete(AsyncEvent event) {
         this.currentObservation.stop();
      }

      public void onError(AsyncEvent event) {
         this.currentObservation.error(ServerHttpObservationFilter.unwrapServletException(event.getThrowable()));
      }
   }
}
