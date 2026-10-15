package org.springframework.boot.logging.structured;

import org.jspecify.annotations.Nullable;

public enum CommonStructuredLogFormat {
   ELASTIC_COMMON_SCHEMA("ecs"),
   GRAYLOG_EXTENDED_LOG_FORMAT("gelf"),
   LOGSTASH("logstash");

   private final String id;

   CommonStructuredLogFormat(String id) {
      this.id = id;
   }

   String getId() {
      return this.id;
   }

   static @Nullable CommonStructuredLogFormat forId(String id) {
      for (CommonStructuredLogFormat candidate : values()) {
         if (candidate.getId().equalsIgnoreCase(id)) {
            return candidate;
         }
      }

      return null;
   }
}
