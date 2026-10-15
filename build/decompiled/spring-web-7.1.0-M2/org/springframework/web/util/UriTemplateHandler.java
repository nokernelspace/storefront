package org.springframework.web.util;

import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface UriTemplateHandler {
   URI expand(String uriTemplate, Map<String, ? extends @Nullable Object> uriVariables);

   URI expand(String uriTemplate, @Nullable Object... uriVariables);
}
