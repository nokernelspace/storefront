package org.springframework.web.bind.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.DataBinder.ValueResolver;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class WebExchangeDataBinder extends WebDataBinder {
   public WebExchangeDataBinder(@Nullable Object target) {
      super(target);
   }

   public WebExchangeDataBinder(@Nullable Object target, String objectName) {
      super(target, objectName);
   }

   public Mono<Void> construct(ServerWebExchange exchange) {
      return this.getValuesToBind(exchange).doOnNext(map -> this.construct(new WebExchangeDataBinder.MapValueResolver(map))).then();
   }

   protected boolean shouldConstructArgument(MethodParameter param) {
      Class<?> type = param.nestedIfOptional().getNestedParameterType();
      return super.shouldConstructArgument(param) && !Part.class.isAssignableFrom(type);
   }

   public Mono<Void> bind(ServerWebExchange exchange) {
      return this.shouldNotBindPropertyValues()
         ? Mono.empty()
         : this.getValuesToBind(exchange).doOnNext(map -> this.doBind(new MutablePropertyValues(map))).then();
   }

   public Mono<Map<String, Object>> getValuesToBind(ServerWebExchange exchange) {
      return extractValuesToBind(exchange);
   }

   public static Mono<Map<String, Object>> extractValuesToBind(ServerWebExchange exchange) {
      MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
      Mono<MultiValueMap<String, String>> formData = exchange.getFormData();
      Mono<MultiValueMap<String, Part>> multipartData = exchange.getMultipartData();
      return Mono.zip(Mono.just(queryParams), formData, multipartData).map(tuple -> {
         Map<String, Object> result = new TreeMap<>();
         ((MultiValueMap)tuple.getT1()).forEach((key, values) -> addBindValue(result, key, values));
         ((MultiValueMap)tuple.getT2()).forEach((key, values) -> addBindValue(result, key, values));
         ((MultiValueMap)tuple.getT3()).forEach((key, values) -> addBindValue(result, key, values));
         return result;
      });
   }

   protected static void addBindValue(Map<String, Object> params, String key, List<?> values) {
      if (!CollectionUtils.isEmpty(values)) {
         if (values.size() == 1) {
            params.put(key, adaptBindValue(values.get(0)));
         } else {
            params.put(key, values.stream().map(WebExchangeDataBinder::adaptBindValue).toList());
         }
      }
   }

   private static Object adaptBindValue(Object value) {
      return value instanceof FormFieldPart part ? part.value() : value;
   }

   private record MapValueResolver(Map<String, Object> map) implements ValueResolver {
      public @Nullable Object resolveValue(String name, Class<?> type) {
         return this.map.get(name);
      }

      public Set<String> getNames() {
         return this.map.keySet();
      }
   }
}
