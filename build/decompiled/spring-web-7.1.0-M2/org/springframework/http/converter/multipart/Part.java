package org.springframework.http.converter.multipart;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;

public interface Part {
   String name();

   HttpHeaders headers();

   InputStream content() throws IOException;

   default void delete() throws IOException {
   }
}
