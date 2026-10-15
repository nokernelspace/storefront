package org.springframework.http.server.reactive;

import java.util.function.Function;

public interface HttpHandlerDecoratorFactory extends Function<HttpHandler, HttpHandler> {
}
