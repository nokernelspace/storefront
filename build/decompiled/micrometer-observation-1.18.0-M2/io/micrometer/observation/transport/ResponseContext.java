package io.micrometer.observation.transport;

import org.jspecify.annotations.Nullable;

public interface ResponseContext<RES> {
   @Nullable RES getResponse();

   void setResponse(RES var1);
}
