package org.springframework.web.context.request;

public class RequestScope extends AbstractRequestAttributesScope {
   @Override
   protected int getScope() {
      return 0;
   }
}
