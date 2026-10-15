package org.springframework.web.bind.support;

import org.springframework.web.bind.WebDataBinder;

public interface WebBindingInitializer {
   void initBinder(WebDataBinder binder);
}
