package org.springframework.web.util;

import java.util.Locale;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class DisconnectedClientHelper {
   private static final Set<String> EXCEPTION_PHRASES = Set.of("broken pipe", "connection reset by peer");
   private static final Set<String> EXCEPTION_TYPE_NAMES = Set.of(
      "AbortedException", "ClientAbortException", "EOFException", "EofException", "AsyncRequestNotUsableException"
   );
   private static final Set<Class<?>> EXCLUDED_EXCEPTION_TYPES = CollectionUtils.newLinkedHashSet(4);
   private final Log logger;

   public DisconnectedClientHelper(String logCategory) {
      Assert.notNull(logCategory, "'logCategory' is required");
      this.logger = LogFactory.getLog(logCategory);
   }

   public boolean checkAndLogClientDisconnectedException(Throwable ex) {
      if (isClientDisconnectedException(ex)) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Looks like the client has gone away", ex);
         } else if (this.logger.isDebugEnabled()) {
            this.logger
               .debug("Looks like the client has gone away: " + ex + " (For a full stack trace, set the log category '" + this.logger + "' to TRACE level.)");
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean isClientDisconnectedException(@Nullable Throwable ex) {
      if (ex == null) {
         return false;
      }

      Throwable currentEx = ex;

      for (Throwable lastEx = null; currentEx != null && currentEx != lastEx; currentEx = currentEx.getCause()) {
         for (Class<?> exceptionType : EXCLUDED_EXCEPTION_TYPES) {
            if (exceptionType.isInstance(currentEx)) {
               return false;
            }
         }

         if (EXCEPTION_TYPE_NAMES.contains(currentEx.getClass().getSimpleName())) {
            return true;
         }

         lastEx = currentEx;
      }

      String message = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
      if (message != null) {
         String text = message.toLowerCase(Locale.ROOT);

         for (String phrase : EXCEPTION_PHRASES) {
            if (text.contains(phrase)) {
               return true;
            }
         }
      }

      return false;
   }

   private static void addExcludedExceptionType(String type) {
      try {
         ClassLoader classLoader = DisconnectedClientHelper.class.getClassLoader();
         EXCLUDED_EXCEPTION_TYPES.add(ClassUtils.forName(type, classLoader));
      } catch (ClassNotFoundException var2) {
      }
   }

   static {
      addExcludedExceptionType("org.springframework.web.client.RestClientException");
      addExcludedExceptionType("org.springframework.web.reactive.function.client.WebClientException");
      addExcludedExceptionType("org.springframework.dao.DataAccessException");
      addExcludedExceptionType("org.springframework.messaging.MessagingException");
   }
}
