package org.springframework.http;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

public class MediaType extends MimeType implements Serializable {
   private static final long serialVersionUID = 2069937152339670231L;
   public static final MediaType ALL = new MediaType("*", "*");
   public static final String ALL_VALUE = "*/*";
   public static final MediaType APPLICATION_ATOM_XML = new MediaType("application", "atom+xml");
   public static final String APPLICATION_ATOM_XML_VALUE = "application/atom+xml";
   public static final MediaType APPLICATION_CBOR = new MediaType("application", "cbor");
   public static final String APPLICATION_CBOR_VALUE = "application/cbor";
   public static final MediaType APPLICATION_FORM_URLENCODED = new MediaType("application", "x-www-form-urlencoded");
   public static final String APPLICATION_FORM_URLENCODED_VALUE = "application/x-www-form-urlencoded";
   public static final MediaType APPLICATION_GRAPHQL_RESPONSE = new MediaType("application", "graphql-response+json");
   public static final String APPLICATION_GRAPHQL_RESPONSE_VALUE = "application/graphql-response+json";
   public static final MediaType APPLICATION_JSON = new MediaType("application", "json");
   public static final String APPLICATION_JSON_VALUE = "application/json";
   public static final MediaType APPLICATION_OCTET_STREAM = new MediaType("application", "octet-stream");
   public static final String APPLICATION_OCTET_STREAM_VALUE = "application/octet-stream";
   public static final MediaType APPLICATION_PDF = new MediaType("application", "pdf");
   public static final String APPLICATION_PDF_VALUE = "application/pdf";
   public static final MediaType APPLICATION_PROBLEM_JSON = new MediaType("application", "problem+json");
   public static final String APPLICATION_PROBLEM_JSON_VALUE = "application/problem+json";
   public static final MediaType APPLICATION_PROBLEM_XML = new MediaType("application", "problem+xml");
   public static final String APPLICATION_PROBLEM_XML_VALUE = "application/problem+xml";
   public static final MediaType APPLICATION_PROTOBUF = new MediaType("application", "x-protobuf");
   public static final String APPLICATION_PROTOBUF_VALUE = "application/x-protobuf";
   public static final MediaType APPLICATION_RSS_XML = new MediaType("application", "rss+xml");
   public static final String APPLICATION_RSS_XML_VALUE = "application/rss+xml";
   public static final MediaType APPLICATION_NDJSON = new MediaType("application", "x-ndjson");
   public static final String APPLICATION_NDJSON_VALUE = "application/x-ndjson";
   public static final MediaType APPLICATION_JSONL = new MediaType("application", "jsonl");
   public static final String APPLICATION_JSONL_VALUE = "application/jsonl";
   public static final MediaType APPLICATION_XHTML_XML = new MediaType("application", "xhtml+xml");
   public static final String APPLICATION_XHTML_XML_VALUE = "application/xhtml+xml";
   public static final MediaType APPLICATION_XML = new MediaType("application", "xml");
   public static final String APPLICATION_XML_VALUE = "application/xml";
   public static final MediaType APPLICATION_YAML = new MediaType("application", "yaml");
   public static final String APPLICATION_YAML_VALUE = "application/yaml";
   public static final MediaType IMAGE_GIF = new MediaType("image", "gif");
   public static final String IMAGE_GIF_VALUE = "image/gif";
   public static final MediaType IMAGE_JPEG = new MediaType("image", "jpeg");
   public static final String IMAGE_JPEG_VALUE = "image/jpeg";
   public static final MediaType IMAGE_PNG = new MediaType("image", "png");
   public static final String IMAGE_PNG_VALUE = "image/png";
   public static final MediaType MULTIPART_FORM_DATA = new MediaType("multipart", "form-data");
   public static final String MULTIPART_FORM_DATA_VALUE = "multipart/form-data";
   public static final MediaType MULTIPART_MIXED = new MediaType("multipart", "mixed");
   public static final String MULTIPART_MIXED_VALUE = "multipart/mixed";
   public static final MediaType MULTIPART_RELATED = new MediaType("multipart", "related");
   public static final String MULTIPART_RELATED_VALUE = "multipart/related";
   public static final MediaType TEXT_EVENT_STREAM = new MediaType("text", "event-stream");
   public static final String TEXT_EVENT_STREAM_VALUE = "text/event-stream";
   public static final MediaType TEXT_HTML = new MediaType("text", "html");
   public static final String TEXT_HTML_VALUE = "text/html";
   public static final MediaType TEXT_MARKDOWN = new MediaType("text", "markdown");
   public static final String TEXT_MARKDOWN_VALUE = "text/markdown";
   public static final MediaType TEXT_PLAIN = new MediaType("text", "plain");
   public static final String TEXT_PLAIN_VALUE = "text/plain";
   public static final MediaType TEXT_XML = new MediaType("text", "xml");
   public static final String TEXT_XML_VALUE = "text/xml";
   private static final String PARAM_QUALITY_FACTOR = "q";

   public MediaType(String type) {
      super(type);
   }

   public MediaType(String type, String subtype) {
      super(type, subtype, Collections.emptyMap());
   }

   public MediaType(String type, String subtype, Charset charset) {
      super(type, subtype, charset);
   }

   public MediaType(String type, String subtype, double qualityValue) {
      this(type, subtype, Collections.singletonMap("q", Double.toString(qualityValue)));
   }

   public MediaType(MediaType other, Charset charset) {
      super(other, charset);
   }

   public MediaType(MediaType other, @Nullable Map<String, String> parameters) {
      super(other, parameters);
   }

   public MediaType(String type, String subtype, @Nullable Map<String, String> parameters) {
      super(type, subtype, parameters);
   }

   public MediaType(MimeType mimeType) {
      super(mimeType);
      this.getParameters().forEach(this::checkParameters);
   }

   protected void checkParameters(String parameter, String value) {
      super.checkParameters(parameter, value);
      if ("q".equalsIgnoreCase(parameter)) {
         String unquotedValue = this.unquote(value);
         double d = Double.parseDouble(unquotedValue);
         Assert.isTrue(d >= 0.0 && d <= 1.0, () -> "Invalid quality value \"" + unquotedValue + "\": should be between 0.0 and 1.0");
      }
   }

   public double getQualityValue() {
      String qualityFactor = this.getParameter("q");
      return qualityFactor != null ? Double.parseDouble(this.unquote(qualityFactor)) : 1.0;
   }

   public boolean isMoreSpecific(MimeType other) {
      Assert.notNull(other, "Other must not be null");
      if (other instanceof MediaType otherMediaType) {
         double quality1 = this.getQualityValue();
         double quality2 = otherMediaType.getQualityValue();
         if (quality1 > quality2) {
            return true;
         }

         if (quality1 < quality2) {
            return false;
         }
      }

      return super.isMoreSpecific(other);
   }

   public boolean isLessSpecific(MimeType other) {
      Assert.notNull(other, "Other must not be null");
      return other.isMoreSpecific(this);
   }

   public boolean includes(@Nullable MediaType other) {
      return super.includes(other);
   }

   public boolean isCompatibleWith(@Nullable MediaType other) {
      return super.isCompatibleWith(other);
   }

   public MediaType copyQualityValue(MediaType mediaType) {
      if (!mediaType.getParameters().containsKey("q")) {
         return this;
      }

      Map<String, String> params = new LinkedHashMap<>(this.getParameters());
      params.put("q", (String)mediaType.getParameters().get("q"));
      return new MediaType(this, params);
   }

   public MediaType removeQualityValue() {
      if (!this.getParameters().containsKey("q")) {
         return this;
      }

      Map<String, String> params = new LinkedHashMap<>(this.getParameters());
      params.keySet().removeIf("q"::equalsIgnoreCase);
      return new MediaType(this, params);
   }

   public static MediaType valueOf(String value) {
      return parseMediaType(value);
   }

   public static MediaType parseMediaType(String mediaType) {
      MimeType type;
      try {
         type = MimeTypeUtils.parseMimeType(mediaType);
      } catch (InvalidMimeTypeException ex) {
         throw new InvalidMediaTypeException(ex);
      }

      try {
         return new MediaType(type);
      } catch (IllegalArgumentException ex) {
         throw new InvalidMediaTypeException(mediaType, ex.getMessage());
      }
   }

   public static List<MediaType> parseMediaTypes(@Nullable String mediaTypes) {
      if (!StringUtils.hasLength(mediaTypes)) {
         return Collections.emptyList();
      }

      List<MimeType> mimeTypes;
      try {
         mimeTypes = MimeTypeUtils.parseMimeTypes(mediaTypes);
      } catch (InvalidMimeTypeException ex) {
         throw new InvalidMediaTypeException(ex);
      }

      List<MediaType> result = new ArrayList<>(mimeTypes.size());

      for (MimeType mimeType : mimeTypes) {
         try {
            result.add(new MediaType(mimeType));
         } catch (IllegalArgumentException ex) {
            throw new InvalidMediaTypeException(mimeType.toString(), ex.getMessage());
         }
      }

      return result;
   }

   public static List<MediaType> parseMediaTypes(@Nullable List<String> mediaTypes) {
      if (CollectionUtils.isEmpty(mediaTypes)) {
         return Collections.emptyList();
      }

      if (mediaTypes.size() == 1) {
         return parseMediaTypes(mediaTypes.get(0));
      }

      List<MediaType> result = new ArrayList<>(8);

      for (String mediaType : mediaTypes) {
         result.addAll(parseMediaTypes(mediaType));
      }

      return result;
   }

   public static List<MediaType> asMediaTypes(List<MimeType> mimeTypes) {
      List<MediaType> mediaTypes = new ArrayList<>(mimeTypes.size());

      for (MimeType mimeType : mimeTypes) {
         mediaTypes.add(asMediaType(mimeType));
      }

      return mediaTypes;
   }

   public static MediaType asMediaType(MimeType mimeType) {
      return mimeType instanceof MediaType mediaType ? mediaType : new MediaType(mimeType.getType(), mimeType.getSubtype(), mimeType.getParameters());
   }

   public static String toString(Collection<MediaType> mediaTypes) {
      return MimeTypeUtils.toString(mediaTypes);
   }
}
