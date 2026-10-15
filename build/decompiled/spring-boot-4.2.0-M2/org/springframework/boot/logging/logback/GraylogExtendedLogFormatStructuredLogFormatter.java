package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.json.WritableJson;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.GraylogExtendedLogFormatProperties;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.util.StringUtils;

class GraylogExtendedLogFormatStructuredLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent> {
   private static final JsonWriter.PairExtractor<KeyValuePair> keyValuePairExtractor = JsonWriter.PairExtractor.of(pair -> pair.key, pair -> pair.value);
   private static final Log logger = LogFactory.getLog(GraylogExtendedLogFormatStructuredLogFormatter.class);
   private static final Pattern FIELD_NAME_VALID_PATTERN = Pattern.compile("^[\\w.\\-]*$");
   private static final Set<String> ADDITIONAL_FIELD_ILLEGAL_KEYS = Set.of("id", "_id");

   GraylogExtendedLogFormatStructuredLogFormatter(
      Environment environment,
      @Nullable StackTracePrinter stackTracePrinter,
      ContextPairs contextPairs,
      ThrowableProxyConverter throwableProxyConverter,
      @Nullable StructuredLoggingJsonMembersCustomizer<?> customizer
   ) {
      super(members -> jsonMembers(environment, stackTracePrinter, contextPairs, throwableProxyConverter, members), customizer);
   }

   private static void jsonMembers(
      Environment environment,
      StackTracePrinter stackTracePrinter,
      ContextPairs contextPairs,
      ThrowableProxyConverter throwableProxyConverter,
      JsonWriter.Members<ILoggingEvent> members
   ) {
      Extractor extractor = new Extractor(stackTracePrinter, throwableProxyConverter);
      members.add("version", "1.1");
      members.<String>add("short_message", ILoggingEvent::getFormattedMessage).as(GraylogExtendedLogFormatStructuredLogFormatter::getMessageText);
      members.<Long>add("timestamp", ILoggingEvent::getTimeStamp).as(GraylogExtendedLogFormatStructuredLogFormatter::formatTimeStamp);
      members.add("level", LevelToSyslogSeverity::convert);
      members.add("_level_name", ILoggingEvent::getLevel);
      members.add("_process_pid", (Long)environment.getProperty("spring.application.pid", Long.class)).whenNotNull();
      members.add("_process_thread_name", ILoggingEvent::getThreadName);
      GraylogExtendedLogFormatProperties.get(environment).jsonMembers(members);
      members.add("_log_logger", ILoggingEvent::getLoggerName);
      members.add().usingPairs(contextPairs.flat(additionalFieldJoiner(), pairs -> {
         pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap);
         pairs.add(ILoggingEvent::getKeyValuePairs, keyValuePairExtractor);
      }));
      Function<ILoggingEvent, Object> getThrowableProxy = event -> event != null ? event.getThrowableProxy() : null;
      members.add().whenNotNull(getThrowableProxy).usingMembers(throwableMembers -> throwableMembers(throwableMembers, extractor));
   }

   private static String getMessageText(String formattedMessage) {
      return !StringUtils.hasText(formattedMessage) ? "(blank)" : formattedMessage;
   }

   private static WritableJson formatTimeStamp(long timeStamp) {
      return out -> out.append(new BigDecimal(timeStamp).movePointLeft(3).toPlainString());
   }

   private static void throwableMembers(JsonWriter.Members<ILoggingEvent> members, Extractor extractor) {
      members.add("full_message", extractor::messageAndStackTrace);
      members.add("_error_type", ILoggingEvent::getThrowableProxy).as(IThrowableProxy::getClassName);
      members.add("_error_stack_trace", extractor::stackTrace);
      members.add("_error_message", ILoggingEvent::getThrowableProxy).as(IThrowableProxy::getMessage);
   }

   private static ContextPairs.Joiner additionalFieldJoiner() {
      return (prefix, name) -> {
         name = prefix + name;
         if (!FIELD_NAME_VALID_PATTERN.matcher(name).matches()) {
            logger.warn(LogMessage.format("'%s' is not a valid field name according to GELF standard", name));
            return null;
         } else if (ADDITIONAL_FIELD_ILLEGAL_KEYS.contains(name)) {
            logger.warn(LogMessage.format("'%s' is an illegal field name according to GELF standard", name));
            return null;
         } else {
            return !name.startsWith("_") ? "_" + name : name;
         }
      };
   }
}
