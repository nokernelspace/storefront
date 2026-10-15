package org.springframework.context;

import org.springframework.beans.factory.Aware;

public interface ApplicationEventPublisherAware extends Aware {
   void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher);
}
