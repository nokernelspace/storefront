package org.springframework.http.converter.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_EMPTY)
@JacksonXmlRootElement(localName = "problem", namespace = "urn:ietf:rfc:7807")
@JsonRootName(value = "problem", namespace = "urn:ietf:rfc:7807")
public interface ProblemDetailJacksonXmlMixin {
   String NAMESPACE = "urn:ietf:rfc:7807";

   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   URI getType();

   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   String getTitle();

   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   int getStatus();

   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   String getDetail();

   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   URI getInstance();

   @JsonAnySetter
   void setProperty(String name, @Nullable Object value);

   @JsonAnyGetter
   @JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   @tools.jackson.dataformat.xml.annotation.JacksonXmlProperty(namespace = "urn:ietf:rfc:7807")
   Map<String, Object> getProperties();
}
