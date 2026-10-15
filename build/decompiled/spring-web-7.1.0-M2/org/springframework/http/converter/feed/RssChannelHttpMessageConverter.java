package org.springframework.http.converter.feed;

import com.rometools.rome.feed.rss.Channel;
import org.springframework.http.MediaType;

public class RssChannelHttpMessageConverter extends AbstractWireFeedHttpMessageConverter<Channel> {
   public RssChannelHttpMessageConverter() {
      super(MediaType.APPLICATION_RSS_XML);
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return Channel.class.isAssignableFrom(clazz);
   }
}
