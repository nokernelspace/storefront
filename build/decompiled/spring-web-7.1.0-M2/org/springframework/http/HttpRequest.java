package org.springframework.http;

import java.net.URI;
import java.util.Map;

public interface HttpRequest extends HttpMessage {
   HttpMethod getMethod();

   URI getURI();

   Map<String, Object> getAttributes();
}
