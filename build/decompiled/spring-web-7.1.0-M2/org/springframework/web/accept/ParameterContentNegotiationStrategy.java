package org.springframework.web.accept;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

public class ParameterContentNegotiationStrategy extends AbstractMappingContentNegotiationStrategy {
   private String parameterName = "format";

   public ParameterContentNegotiationStrategy(Map<String, MediaType> mediaTypes) {
      super(mediaTypes);
   }

   public void setParameterName(String parameterName) {
      Assert.notNull(parameterName, "'parameterName' is required");
      this.parameterName = parameterName;
   }

   public String getParameterName() {
      return this.parameterName;
   }

   @Override
   protected @Nullable String getMediaTypeKey(NativeWebRequest request) {
      return request.getParameter(this.getParameterName());
   }
}
