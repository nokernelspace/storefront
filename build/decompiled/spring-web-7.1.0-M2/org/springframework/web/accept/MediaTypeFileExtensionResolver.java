package org.springframework.web.accept;

import java.util.List;
import org.springframework.http.MediaType;

public interface MediaTypeFileExtensionResolver {
   List<String> resolveFileExtensions(MediaType mediaType);

   List<String> getAllFileExtensions();
}
