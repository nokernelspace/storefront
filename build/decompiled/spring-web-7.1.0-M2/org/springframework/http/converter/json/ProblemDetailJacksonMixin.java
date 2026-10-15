package org.springframework.http.converter.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_EMPTY)
public interface ProblemDetailJacksonMixin {
   @JsonAnySetter
   void setProperty(String name, @Nullable Object value);

   @JsonAnyGetter
   Map<String, Object> getProperties();
}
