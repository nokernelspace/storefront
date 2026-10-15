package org.springframework.web.filter.reactive;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class HiddenHttpMethodFilter implements WebFilter {
   private static final List<HttpMethod> ALLOWED_METHODS = List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.QUERY);
   public static final String DEFAULT_METHOD_PARAMETER_NAME = "_method";
   private String methodParamName = "_method";

   public void setMethodParamName(String methodParamName) {
      Assert.hasText(methodParamName, "'methodParamName' must not be empty");
      this.methodParamName = methodParamName;
   }

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
      return exchange.getRequest().getMethod() != HttpMethod.POST ? chain.filter(exchange) : exchange.getFormData().map(formData -> {
         String method = (String)formData.getFirst(this.methodParamName);
         return StringUtils.hasLength(method) ? this.mapExchange(exchange, method) : exchange;
      }).flatMap(chain::filter);
   }

   private ServerWebExchange mapExchange(ServerWebExchange exchange, String methodParamValue) {
      HttpMethod httpMethod = HttpMethod.valueOf(methodParamValue.toUpperCase(Locale.ROOT));
      return ALLOWED_METHODS.contains(httpMethod) ? exchange.mutate().request(builder -> builder.method(httpMethod)).build() : exchange;
   }
}
