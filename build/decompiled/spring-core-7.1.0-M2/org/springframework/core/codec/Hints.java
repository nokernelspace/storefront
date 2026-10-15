package org.springframework.core.codec;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public abstract class Hints {
   public static final String LOG_PREFIX_HINT = Log.class.getName() + ".PREFIX";
   public static final String SUPPRESS_LOGGING_HINT = Log.class.getName() + ".SUPPRESS_LOGGING";

   public static Map<String, Object> from(String hintName, Object value) {
      return Collections.singletonMap(hintName, value);
   }

   public static Map<String, Object> none() {
      return Collections.emptyMap();
   }

   public static <T> T getRequiredHint(@Nullable Map<String, Object> hints, String hintName) {
      if (hints == null) {
         throw new IllegalArgumentException("No hints map for required hint '" + hintName + "'");
      } else {
         T hint = (T)hints.get(hintName);
         if (hint == null) {
            throw new IllegalArgumentException("Hints map must contain the hint '" + hintName + "'");
         } else {
            return hint;
         }
      }
   }

   public static String getLogPrefix(@Nullable Map<String, Object> hints) {
      return hints != null ? (String)hints.getOrDefault(LOG_PREFIX_HINT, "") : "";
   }

   public static boolean isLoggingSuppressed(@Nullable Map<String, Object> hints) {
      return hints != null && (Boolean)hints.getOrDefault(SUPPRESS_LOGGING_HINT, false);
   }

   public static Map<String, Object> merge(@Nullable Map<String, Object> hints1, @Nullable Map<String, Object> hints2) {
      if (ObjectUtils.isEmpty(hints1) && ObjectUtils.isEmpty(hints2)) {
         return Collections.emptyMap();
      }

      if (ObjectUtils.isEmpty(hints2)) {
         return hints1 != null ? hints1 : Collections.emptyMap();
      }

      if (ObjectUtils.isEmpty(hints1)) {
         return hints2;
      }

      Map<String, Object> result = CollectionUtils.newHashMap(hints1.size() + hints2.size());
      result.putAll(hints1);
      result.putAll(hints2);
      return result;
   }

   public static Map<String, Object> merge(@Nullable Map<String, Object> hints, String hintName, Object hintValue) {
      if (ObjectUtils.isEmpty(hints)) {
         return Collections.singletonMap(hintName, hintValue);
      }

      Map<String, Object> result = CollectionUtils.newHashMap(hints.size() + 1);
      result.putAll(hints);
      result.put(hintName, hintValue);
      return result;
   }

   public static void touchDataBuffer(DataBuffer buffer, @Nullable Map<String, Object> hints, Log logger) {
      if (logger.isDebugEnabled() && hints != null) {
         Object logPrefix = hints.get(LOG_PREFIX_HINT);
         if (logPrefix != null) {
            DataBufferUtils.touch(buffer, logPrefix);
         }
      }
   }
}
