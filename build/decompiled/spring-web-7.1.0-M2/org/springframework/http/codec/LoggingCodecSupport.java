package org.springframework.http.codec;

import org.apache.commons.logging.Log;
import org.springframework.http.HttpLogging;

public class LoggingCodecSupport {
   protected final Log logger = HttpLogging.forLogName(this.getClass());
   private boolean enableLoggingRequestDetails = false;

   public void setEnableLoggingRequestDetails(boolean enable) {
      this.enableLoggingRequestDetails = enable;
   }

   public boolean isEnableLoggingRequestDetails() {
      return this.enableLoggingRequestDetails;
   }
}
