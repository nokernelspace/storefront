package org.springframework.context;

import org.springframework.beans.factory.Aware;

public interface MessageSourceAware extends Aware {
   void setMessageSource(MessageSource messageSource);
}
