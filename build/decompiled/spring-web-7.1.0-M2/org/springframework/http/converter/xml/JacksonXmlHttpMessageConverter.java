package org.springframework.http.converter.xml;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonXmlMixin;
import org.springframework.util.xml.StaxUtils;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlMapper.Builder;

public class JacksonXmlHttpMessageConverter extends AbstractJacksonHttpMessageConverter<XmlMapper> {
   private static final List<MediaType> problemDetailMediaTypes = Collections.singletonList(MediaType.APPLICATION_PROBLEM_XML);
   private static final MediaType[] DEFAULT_XML_MIME_TYPES = new MediaType[]{
      new MediaType("application", "xml", StandardCharsets.UTF_8),
      new MediaType("text", "xml", StandardCharsets.UTF_8),
      new MediaType("application", "*+xml", StandardCharsets.UTF_8)
   };

   public JacksonXmlHttpMessageConverter() {
      this(XmlMapper.builder(defensiveXmlFactory()));
   }

   public JacksonXmlHttpMessageConverter(Builder builder) {
      super(builder.addMixIn(ProblemDetail.class, ProblemDetailJacksonXmlMixin.class), DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlHttpMessageConverter(XmlMapper xmlMapper) {
      super(xmlMapper, DEFAULT_XML_MIME_TYPES);
   }

   public static XmlFactory defensiveXmlFactory() {
      return new XmlFactory(StaxUtils.createDefensiveInputFactory());
   }

   @Override
   protected List<MediaType> getMediaTypesForProblemDetail() {
      return problemDetailMediaTypes;
   }
}
