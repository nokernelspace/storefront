package org.springframework.http.converter.feed;

import com.rometools.rome.feed.atom.Feed;
import org.springframework.http.MediaType;

public class AtomFeedHttpMessageConverter extends AbstractWireFeedHttpMessageConverter<Feed> {
   public AtomFeedHttpMessageConverter() {
      super(new MediaType("application", "atom+xml"));
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return Feed.class.isAssignableFrom(clazz);
   }
}
