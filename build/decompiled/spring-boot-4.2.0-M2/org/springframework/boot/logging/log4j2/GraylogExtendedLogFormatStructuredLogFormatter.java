package org.springframework.boot.logging.log4j2;

import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.net.Severity;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.json.WritableJson;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.GraylogExtendedLogFormatProperties;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

class GraylogExtendedLogFormatStructuredLogFormatter extends JsonWriterStructuredLogFormatter<LogEvent> {
   private static final Log logger = LogFactory.getLog(GraylogExtendedLogFormatStructuredLogFormatter.class);
   private static final Pattern FIELD_NAME_VALID_PATTERN = Pattern.compile("^[\\w.\\-]*$");
   private static final Set<String> ADDITIONAL_FIELD_ILLEGAL_KEYS = Set.of("id", "_id");

   GraylogExtendedLogFormatStructuredLogFormatter(
      Environment environment,
      @Nullable StackTracePrinter stackTracePrinter,
      ContextPairs contextPairs,
      @Nullable StructuredLoggingJsonMembersCustomizer<?> customizer
   ) {
      super(members -> jsonMembers(environment, stackTracePrinter, contextPairs, members), customizer);
   }

   private static void jsonMembers(
      Environment environment, StackTracePrinter stackTracePrinter, ContextPairs contextPairs, JsonWriter.Members<LogEvent> members
   ) {
      Extractor extractor = new Extractor(stackTracePrinter);
      members.add("version", "1.1");
      members.<Message>add("short_message", LogEvent::getMessage).as(GraylogExtendedLogFormatStructuredLogFormatter::getMessageText);
      members.<Instant>add("timestamp", LogEvent::getInstant).as(GraylogExtendedLogFormatStructuredLogFormatter::formatTimeStamp);
      members.add("level", GraylogExtendedLogFormatStructuredLogFormatter::convertLevel);
      members.add("_level_name", LogEvent::getLevel).as(Level::name);
      members.add("_process_pid", (Long)environment.getProperty("spring.application.pid", Long.class)).whenNotNull();
      members.add("_process_thread_name", LogEvent::getThreadName);
      GraylogExtendedLogFormatProperties.get(environment).jsonMembers(members);
      members.add("_log_logger", LogEvent::getLoggerName);
      Predicate<ReadOnlyStringMap> mapIsEmpty = map -> map == null || map.isEmpty();
      members.<ReadOnlyStringMap>from(LogEvent::getContextData)
         .whenNot(mapIsEmpty)
         .usingPairs(contextPairs.flat(additionalFieldJoiner(), GraylogExtendedLogFormatStructuredLogFormatter::addContextDataPairs));
      Function<LogEvent, Object> getThrown = event -> event != null ? event.getThrown() : null;
      members.add().whenNotNull(getThrown).usingMembers(thrownMembers -> throwableMembers(thrownMembers, extractor));
   }

   private static String getMessageText(Message message) {
      String formattedMessage = message.getFormattedMessage();
      return !StringUtils.hasText(formattedMessage) ? "(blank)" : formattedMessage;
   }

   private static WritableJson formatTimeStamp(Instant timeStamp) {
      return out -> out.append(new BigDecimal(timeStamp.getEpochMillisecond()).movePointLeft(3).toPlainString());
   }

   private static int convertLevel(LogEvent event) {
      return Severity.getSeverity(event.getLevel()).getCode();
   }

   private static void throwableMembers(JsonWriter.Members<LogEvent> members, Extractor extractor) {
      members.add("full_message", extractor::messageAndStackTrace);
      members.add("_error_type", LogEvent::getThrown).whenNotNull().as(ObjectUtils::nullSafeClassName);
      members.add("_error_stack_trace", extractor::stackTrace);
      members.add("_error_message", event -> event.getThrown().getMessage());
   }

   private static void addContextDataPairs(ContextPairs.Pairs<ReadOnlyStringMap> contextPairs) {
      contextPairs.add((contextData, pairs) -> contextData.forEach(pairs::accept));
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
