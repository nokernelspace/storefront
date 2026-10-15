package org.springframework.http.codec.json;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.util.MimeType;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper.Builder;

public class JacksonJsonEncoder extends AbstractJacksonEncoder<JsonMapper> {
   private static final List<MimeType> problemDetailMimeTypes = Collections.singletonList(MediaType.APPLICATION_PROBLEM_JSON);
   private static final MimeType[] DEFAULT_JSON_MIME_TYPES = new MimeType[]{
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   };
   private final @Nullable PrettyPrinter ssePrettyPrinter;

   public JacksonJsonEncoder() {
      this(JsonMapper.builder(), DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonEncoder(Builder builder) {
      this(builder, DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonEncoder(JsonMapper mapper) {
      this(mapper, DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonEncoder(Builder builder, MimeType... mimeTypes) {
      super(builder.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class), mimeTypes);
      this.setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL));
      this.ssePrettyPrinter = initSsePrettyPrinter();
   }

   public JacksonJsonEncoder(JsonMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
      this.setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL));
      this.ssePrettyPrinter = initSsePrettyPrinter();
   }

   private static PrettyPrinter initSsePrettyPrinter() {
      DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
      printer.indentObjectsWith(new DefaultIndenter("  ", "\ndata: "));
      return printer;
   }

   @Override
   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return super.canEncode(elementType, mimeType) && String.class != elementType.toClass();
   }

   @Override
   protected List<MimeType> getMediaTypesForProblemDetail() {
      return problemDetailMimeTypes;
   }

   @Override
   protected ObjectWriter customizeWriter(ObjectWriter writer, @Nullable MimeType mimeType, ResolvableType elementType, @Nullable Map<String, Object> hints) {
      return this.ssePrettyPrinter != null
            && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(mimeType)
            && writer.getConfig().isEnabled(SerializationFeature.INDENT_OUTPUT)
         ? writer.with(this.ssePrettyPrinter)
         : writer;
   }
}
