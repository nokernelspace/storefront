package org.springframework.web.bind.support;

public interface SessionStatus {
   void setComplete();

   boolean isComplete();
}
