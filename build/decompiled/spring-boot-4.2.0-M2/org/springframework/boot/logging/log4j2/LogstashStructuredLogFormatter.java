package org.springframework.boot.logging.log4j2;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.util.CollectionUtils;

class LogstashStructuredLogFormatter extends JsonWriterStructuredLogFormatter<LogEvent> {
   LogstashStructuredLogFormatter(
      @Nullable StackTracePrinter stackTracePrinter, ContextPairs contextPairs, @Nullable StructuredLoggingJsonMembersCustomizer<?> customizer
   ) {
      super(members -> jsonMembers(stackTracePrinter, contextPairs, members), customizer);
   }

   private static void jsonMembers(StackTracePrinter stackTracePrinter, ContextPairs contextPairs, JsonWriter.Members<LogEvent> members) {
      Extractor extractor = new Extractor(stackTracePrinter);
      members.<Instant>add("@timestamp", LogEvent::getInstant).as(LogstashStructuredLogFormatter::asTimestamp);
      members.add("@version", "1");
      members.<Message>add("message", LogEvent::getMessage).as(StructuredMessage::get);
      members.add("logger_name", LogEvent::getLoggerName);
      members.add("thread_name", LogEvent::getThreadName);
      members.add("level", LogEvent::getLevel).as(Level::name);
      members.add("level_value", LogEvent::getLevel).as(Level::intLevel);
      Predicate<ReadOnlyStringMap> mapIsEmpty = map -> map == null || map.isEmpty();
      members.<ReadOnlyStringMap>from(LogEvent::getContextData)
         .whenNot(mapIsEmpty)
         .usingPairs(contextPairs.flat("_", LogstashStructuredLogFormatter::addContextDataPairs));
      Predicate<Set<String>> collectionIsEmpty = CollectionUtils::isEmpty;
      members.<Marker>add("tags", LogEvent::getMarker).whenNotNull().as(LogstashStructuredLogFormatter::getMarkers).whenNot(collectionIsEmpty);
      members.<Throwable>add("stack_trace", LogEvent::getThrown).whenNotNull().as(extractor::stackTrace);
   }

   private static String asTimestamp(Instant instant) {
      java.time.Instant javaInstant = java.time.Instant.ofEpochMilli(instant.getEpochMillisecond()).plusNanos(instant.getNanoOfMillisecond());
      OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(javaInstant, ZoneId.systemDefault());
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime);
   }

   private static void addContextDataPairs(ContextPairs.Pairs<ReadOnlyStringMap> contextPairs) {
      contextPairs.add((contextData, pairs) -> contextData.forEach(pairs::accept));
   }

   private static Set<String> getMarkers(Marker marker) {
      Set<String> result = new TreeSet<>();
      addMarkers(result, marker);
      return result;
   }

   private static void addMarkers(Set<String> result, Marker marker) {
      result.add(marker.getName());
      if (marker.hasParents()) {
         for (Marker parent : marker.getParents()) {
            addMarkers(result, parent);
         }
      }
   }
}
