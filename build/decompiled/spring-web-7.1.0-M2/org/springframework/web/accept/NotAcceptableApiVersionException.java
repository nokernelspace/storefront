package org.springframework.web.accept;

public class NotAcceptableApiVersionException extends InvalidApiVersionException {
   public NotAcceptableApiVersionException(String version) {
      super(version);
   }
}
