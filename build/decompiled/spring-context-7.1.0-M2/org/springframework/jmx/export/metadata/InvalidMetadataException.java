package org.springframework.jmx.export.metadata;

import org.springframework.jmx.JmxException;

public class InvalidMetadataException extends JmxException {
   public InvalidMetadataException(String msg) {
      super(msg);
   }
}
