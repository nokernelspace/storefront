package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.CoreConstants;

public class ExtendedWhitespaceThrowableProxyConverter extends ExtendedThrowableProxyConverter {
   protected String throwableProxyToString(IThrowableProxy tp) {
      return CoreConstants.LINE_SEPARATOR + super.throwableProxyToString(tp) + CoreConstants.LINE_SEPARATOR;
   }
}
