package org.springframework.http.codec.multipart;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

public interface PartEvent {
   default String name() {
      String name = this.headers().getContentDisposition().getName();
      Assert.state(name != null, "No name available");
      return name;
   }

   HttpHeaders headers();

   DataBuffer content();

   boolean isLast();
}
