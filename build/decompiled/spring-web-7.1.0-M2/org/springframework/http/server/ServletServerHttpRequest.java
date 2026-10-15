package org.springframework.http.server;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;

public class ServletServerHttpRequest implements ServerHttpRequest {
   protected static final Charset FORM_CHARSET = StandardCharsets.UTF_8;
   private final HttpServletRequest servletRequest;
   private @Nullable URI uri;
   private @Nullable HttpHeaders headers;
   private @Nullable Map<String, Object> attributes;
   private @Nullable ServerHttpAsyncRequestControl asyncRequestControl;

   public ServletServerHttpRequest(HttpServletRequest servletRequest) {
      Assert.notNull(servletRequest, "HttpServletRequest must not be null");
      this.servletRequest = servletRequest;
   }

   public HttpServletRequest getServletRequest() {
      return this.servletRequest;
   }

   @Override
   public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.servletRequest.getMethod());
   }

   @Override
   public URI getURI() {
      if (this.uri == null) {
         this.uri = initURI(this.servletRequest);
      }

      return this.uri;
   }

   public static URI initURI(HttpServletRequest servletRequest) {
      String urlString = null;
      String query = null;
      boolean hasQuery = false;

      try {
         StringBuffer requestURL = servletRequest.getRequestURL();
         query = servletRequest.getQueryString();
         hasQuery = StringUtils.hasText(query);
         if (hasQuery) {
            requestURL.append('?').append(query);
         }

         urlString = requestURL.toString();
         return new URI(urlString);
      } catch (URISyntaxException ex) {
         if (hasQuery) {
            String requestURL = servletRequest.getRequestURL().toString();

            try {
               return new URI(requestURL + "?" + encodeQuery(query));
            } catch (URISyntaxException ex2) {
               try {
                  return new URI(requestURL);
               } catch (URISyntaxException var8) {
               }
            }
         }

         throw new IllegalStateException("Could not resolve HttpServletRequest as URI: " + urlString, ex);
      }
   }

   private static String encodeQuery(String query) throws URISyntaxException {
      return new URI(null, null, "", query, null).getRawQuery();
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.headers == null) {
         this.headers = new HttpHeaders(ServletRequestHeadersAdapter.create(this.servletRequest));

         try {
            MediaType contentType = this.headers.getContentType();
            if (contentType == null) {
               String requestContentType = this.servletRequest.getContentType();
               if (StringUtils.hasLength(requestContentType)) {
                  contentType = MediaType.parseMediaType(requestContentType);
                  if (contentType.isConcrete()) {
                     this.headers.setContentType(contentType);
                  }
               }
            }

            if (contentType != null && contentType.getCharset() == null) {
               String requestEncoding = this.servletRequest.getCharacterEncoding();
               if (StringUtils.hasLength(requestEncoding)) {
                  Charset charset = Charset.forName(requestEncoding);
                  Map<String, String> params = new LinkedCaseInsensitiveMap();
                  params.putAll(contentType.getParameters());
                  params.put("charset", charset.toString());
                  MediaType mediaType = new MediaType(contentType.getType(), contentType.getSubtype(), params);
                  this.headers.setContentType(mediaType);
               }
            }
         } catch (InvalidMediaTypeException var6) {
         }

         if (this.headers.getContentLength() < 0L) {
            int requestContentLength = this.servletRequest.getContentLength();
            if (requestContentLength != -1) {
               this.headers.setContentLength(requestContentLength);
            }
         }
      }

      return this.headers;
   }

   @Override
   public @Nullable Principal getPrincipal() {
      return this.servletRequest.getUserPrincipal();
   }

   @Override
   public InetSocketAddress getLocalAddress() {
      return new InetSocketAddress(this.servletRequest.getLocalAddr(), this.servletRequest.getLocalPort());
   }

   @Override
   public InetSocketAddress getRemoteAddress() {
      String addressOrHost = this.servletRequest.getRemoteAddr();
      addressOrHost = addressOrHost != null ? addressOrHost : this.servletRequest.getRemoteHost();
      return new InetSocketAddress(addressOrHost, this.servletRequest.getRemotePort());
   }

   @Override
   public Map<String, Object> getAttributes() {
      Map<String, Object> attributes = this.attributes;
      if (attributes == null) {
         attributes = new ServletServerHttpRequest.AttributesMap();
         this.attributes = attributes;
      }

      return attributes;
   }

   @Override
   public InputStream getBody() throws IOException {
      return (InputStream)(isFormPost(this.servletRequest) && this.servletRequest.getQueryString() == null
         ? this.getBodyFromServletRequestParameters(this.servletRequest)
         : this.servletRequest.getInputStream());
   }

   @Override
   public ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response) {
      if (this.asyncRequestControl == null) {
         if (!(response instanceof ServletServerHttpResponse servletServerResponse)) {
            throw new IllegalArgumentException("Response must be a ServletServerHttpResponse: " + response.getClass());
         }

         this.asyncRequestControl = new ServletServerHttpAsyncRequestControl(this, servletServerResponse);
      }

      return this.asyncRequestControl;
   }

   private static boolean isFormPost(HttpServletRequest request) {
      String contentType = request.getContentType();
      return contentType != null && contentType.contains("application/x-www-form-urlencoded") && HttpMethod.POST.matches(request.getMethod());
   }

   private InputStream getBodyFromServletRequestParameters(HttpServletRequest request) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
      Charset charset = this.getFormCharset();
      Writer writer = new OutputStreamWriter(bos, charset);
      Map<String, String[]> form = request.getParameterMap();
      Iterator<Entry<String, String[]>> entryItr = form.entrySet().iterator();

      while (entryItr.hasNext()) {
         Entry<String, String[]> entry = entryItr.next();
         List<String> values = Arrays.asList(entry.getValue());
         Iterator<String> valueItr = values.iterator();

         while (valueItr.hasNext()) {
            String value = valueItr.next();
            writer.write(URLEncoder.encode(entry.getKey(), charset));
            if (value != null) {
               writer.write(61);
               writer.write(URLEncoder.encode(value, charset));
               if (valueItr.hasNext()) {
                  writer.write(38);
               }
            }
         }

         if (entryItr.hasNext()) {
            writer.append('&');
         }
      }

      writer.flush();
      byte[] bytes = bos.toByteArray();
      if (bytes.length > 0 && this.getHeaders().containsHeader("Content-Length")) {
         this.getHeaders().setContentLength(bytes.length);
      }

      return new ByteArrayInputStream(bytes);
   }

   private Charset getFormCharset() {
      try {
         MediaType contentType = this.getHeaders().getContentType();
         if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
         }
      } catch (Exception var2) {
      }

      return FORM_CHARSET;
   }

   private final class AttributesMap extends AbstractMap<String, Object> {
      private transient @Nullable Set<String> keySet;
      private transient @Nullable Collection<Object> values;
      private transient @Nullable Set<Entry<String, Object>> entrySet;

      @Override
      public int size() {
         int size = 0;
         Enumeration<?> names = ServletServerHttpRequest.this.servletRequest.getAttributeNames();

         while (names.hasMoreElements()) {
            size++;
            names.nextElement();
         }

         return size;
      }

      @Override
      public @Nullable Object get(Object key) {
         return key instanceof String name ? ServletServerHttpRequest.this.servletRequest.getAttribute(name) : null;
      }

      public @Nullable Object put(String key, Object value) {
         Object old = this.get(key);
         ServletServerHttpRequest.this.servletRequest.setAttribute(key, value);
         return old;
      }

      @Override
      public @Nullable Object remove(Object key) {
         if (key instanceof String name) {
            Object old = this.get(key);
            ServletServerHttpRequest.this.servletRequest.removeAttribute(name);
            return old;
         } else {
            return null;
         }
      }

      @Override
      public void clear() {
         Enumeration<String> names = ServletServerHttpRequest.this.servletRequest.getAttributeNames();

         while (names.hasMoreElements()) {
            String name = names.nextElement();
            ServletServerHttpRequest.this.servletRequest.removeAttribute(name);
         }
      }

      @Override
      public Set<String> keySet() {
         Set<String> keySet = this.keySet;
         if (keySet == null) {
            keySet = new AbstractSet<String>() {
               @Override
               public Iterator<String> iterator() {
                  return ServletServerHttpRequest.this.servletRequest.getAttributeNames().asIterator();
               }

               @Override
               public int size() {
                  return AttributesMap.this.size();
               }
            };
            this.keySet = keySet;
         }

         return keySet;
      }

      @Override
      public Collection<Object> values() {
         Collection<Object> values = this.values;
         if (values == null) {
            values = new AbstractCollection<Object>() {
               @Override
               public Iterator<Object> iterator() {
                  final Enumeration<String> e = ServletServerHttpRequest.this.servletRequest.getAttributeNames();
                  return new Iterator<Object>() {
                     @Override
                     public boolean hasNext() {
                        return e.hasMoreElements();
                     }

                     @Override
                     public Object next() {
                        String name = e.nextElement();
                        return ServletServerHttpRequest.this.servletRequest.getAttribute(name);
                     }
                  };
               }

               @Override
               public int size() {
                  return AttributesMap.this.size();
               }
            };
            this.values = values;
         }

         return values;
      }

      @Override
      public Set<Entry<String, Object>> entrySet() {
         Set<Entry<String, Object>> entrySet = this.entrySet;
         if (entrySet == null) {
            entrySet = new AbstractSet<Entry<String, Object>>() {
               @Override
               public Iterator<Entry<String, Object>> iterator() {
                  final Enumeration<String> e = ServletServerHttpRequest.this.servletRequest.getAttributeNames();
                  return new Iterator<Entry<String, Object>>() {
                     @Override
                     public boolean hasNext() {
                        return e.hasMoreElements();
                     }

                     public Entry<String, Object> next() {
                        String name = e.nextElement();
                        Object value = ServletServerHttpRequest.this.servletRequest.getAttribute(name);
                        return new SimpleImmutableEntry<>(name, value);
                     }
                  };
               }

               @Override
               public int size() {
                  return AttributesMap.this.size();
               }
            };
            this.entrySet = entrySet;
         }

         return entrySet;
      }
   }
}
