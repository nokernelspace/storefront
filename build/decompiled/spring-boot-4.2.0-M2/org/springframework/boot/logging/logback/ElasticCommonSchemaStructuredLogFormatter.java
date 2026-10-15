package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.ElasticCommonSchemaProperties;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

class ElasticCommonSchemaStructuredLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent> {
   private static final JsonWriter.PairExtractor<KeyValuePair> keyValuePairExtractor = JsonWriter.PairExtractor.of(pair -> pair.key, pair -> pair.value);

   ElasticCommonSchemaStructuredLogFormatter(
      Environment environment,
      @Nullable StackTracePrinter stackTracePrinter,
      ContextPairs contextPairs,
      ThrowableProxyConverter throwableProxyConverter,
      StructuredLoggingJsonMembersCustomizer.Builder<?> customizerBuilder
   ) {
      super(members -> jsonMembers(environment, stackTracePrinter, contextPairs, throwableProxyConverter, members), customizerBuilder.nested().build());
   }

   private static void jsonMembers(
      Environment environment,
      StackTracePrinter stackTracePrinter,
      ContextPairs contextPairs,
      ThrowableProxyConverter throwableProxyConverter,
      JsonWriter.Members<ILoggingEvent> members
   ) {
      Extractor extractor = new Extractor(stackTracePrinter, throwableProxyConverter);
      members.add("@timestamp", ILoggingEvent::getInstant);
      members.add("log").usingMembers(log -> {
         log.add("level", ILoggingEvent::getLevel);
         log.add("logger", ILoggingEvent::getLoggerName);
      });
      members.add("process").usingMembers(process -> {
         process.add("pid", (Long)environment.getProperty("spring.application.pid", Long.class)).whenNotNull();
         process.add("thread").usingMembers(thread -> thread.add("name", ILoggingEvent::getThreadName));
      });
      ElasticCommonSchemaProperties.get(environment).jsonMembers(members);
      members.add("message", ILoggingEvent::getFormattedMessage);
      members.add().usingPairs(contextPairs.nested(pairs -> {
         pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap);
         pairs.add(ILoggingEvent::getKeyValuePairs, keyValuePairExtractor);
      }));
      Function<ILoggingEvent, Object> getThrowableProxy = event -> event != null ? event.getThrowableProxy() : null;
      members.add().whenNotNull(getThrowableProxy).usingMembers(throwableMembers -> throwableMembers.add("error").usingMembers(error -> {
         error.add("type", ILoggingEvent::getThrowableProxy).as(IThrowableProxy::getClassName);
         error.add("message", ILoggingEvent::getThrowableProxy).as(IThrowableProxy::getMessage);
         error.add("stack_trace", extractor::stackTrace);
      }));
      members.<List<Marker>>add("tags", ILoggingEvent::getMarkerList).whenNotNull().as(ElasticCommonSchemaStructuredLogFormatter::getMarkers).whenNotEmpty();
      members.add("ecs").usingMembers(ecs -> ecs.add("version", "8.11"));
   }

   private static Set<String> getMarkers(List<Marker> markers) {
      Set<String> result = new TreeSet<>();
      addMarkers(result, markers.iterator());
      return result;
   }

   private static void addMarkers(Set<String> result, Iterator<Marker> iterator) {
      while (iterator.hasNext()) {
         Marker marker = iterator.next();
         result.add(marker.getName());
         if (marker.hasReferences()) {
            addMarkers(result, marker.iterator());
         }
      }
   }
}
