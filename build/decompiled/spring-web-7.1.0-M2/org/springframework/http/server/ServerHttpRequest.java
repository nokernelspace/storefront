package org.springframework.http.server;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpRequest;

public interface ServerHttpRequest extends HttpRequest, HttpInputMessage {
   @Nullable Principal getPrincipal();

   InetSocketAddress getLocalAddress();

   InetSocketAddress getRemoteAddress();

   ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response);
}
