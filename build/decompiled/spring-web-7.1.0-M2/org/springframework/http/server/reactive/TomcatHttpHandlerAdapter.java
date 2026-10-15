package org.springframework.http.server.reactive;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.ResponseFacade;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class TomcatHttpHandlerAdapter extends ServletHttpHandlerAdapter {
   public TomcatHttpHandlerAdapter(HttpHandler httpHandler) {
      super(httpHandler);
   }

   @Override
   protected ServletServerHttpRequest createRequest(HttpServletRequest request, AsyncContext asyncContext) throws IOException, URISyntaxException {
      Assert.state(this.getServletPath() != null, "Servlet path is not initialized");
      return new TomcatHttpHandlerAdapter.TomcatServerHttpRequest(
         request, asyncContext, this.getServletPath(), this.getDataBufferFactory(), this.getBufferSize()
      );
   }

   @Override
   protected ServletServerHttpResponse createResponse(HttpServletResponse response, AsyncContext asyncContext, ServletServerHttpRequest request) throws IOException {
      return new TomcatHttpHandlerAdapter.TomcatServerHttpResponse(response, asyncContext, this.getDataBufferFactory(), request);
   }

   private static final class TomcatServerHttpRequest extends ServletServerHttpRequest {
      private static final Field COYOTE_REQUEST_FIELD;

      TomcatServerHttpRequest(HttpServletRequest request, AsyncContext context, String servletPath, DataBufferFactory factory, int bufferSize) throws IOException, URISyntaxException {
         super(createTomcatHttpHeaders(request), request, context, servletPath, factory, bufferSize);
      }

      private static HttpHeaders createTomcatHttpHeaders(HttpServletRequest request) {
         RequestFacade requestFacade = getRequestFacade(request);
         Request connectorRequest = (Request)ReflectionUtils.getField(COYOTE_REQUEST_FIELD, requestFacade);
         Assert.state(connectorRequest != null, "No Tomcat connector request");
         org.apache.coyote.Request tomcatRequest = connectorRequest.getCoyoteRequest();
         return new HttpHeaders(new TomcatHeadersAdapter(tomcatRequest.getMimeHeaders()));
      }

      private static RequestFacade getRequestFacade(HttpServletRequest request) {
         if (request instanceof RequestFacade facade) {
            return facade;
         } else if (request instanceof HttpServletRequestWrapper wrapper) {
            HttpServletRequest wrappedRequest = (HttpServletRequest)wrapper.getRequest();
            return getRequestFacade(wrappedRequest);
         } else {
            throw new IllegalArgumentException("Cannot convert [" + request.getClass() + "] to org.apache.catalina.connector.RequestFacade");
         }
      }

      static {
         Field field = ReflectionUtils.findField(RequestFacade.class, "request");
         Assert.state(field != null, "Incompatible Tomcat implementation");
         ReflectionUtils.makeAccessible(field);
         COYOTE_REQUEST_FIELD = field;
      }
   }

   private static final class TomcatServerHttpResponse extends ServletServerHttpResponse {
      private static final Field COYOTE_RESPONSE_FIELD;

      TomcatServerHttpResponse(HttpServletResponse response, AsyncContext context, DataBufferFactory factory, ServletServerHttpRequest request) throws IOException {
         super(createTomcatHttpHeaders(response), response, context, factory, request);
      }

      private static HttpHeaders createTomcatHttpHeaders(HttpServletResponse response) {
         ResponseFacade responseFacade = getResponseFacade(response);
         Response connectorResponse = (Response)ReflectionUtils.getField(COYOTE_RESPONSE_FIELD, responseFacade);
         Assert.state(connectorResponse != null, "No Tomcat connector response");
         org.apache.coyote.Response tomcatResponse = connectorResponse.getCoyoteResponse();
         TomcatHeadersAdapter headers = new TomcatHeadersAdapter(tomcatResponse.getMimeHeaders());
         return new HttpHeaders(headers);
      }

      private static ResponseFacade getResponseFacade(HttpServletResponse response) {
         if (response instanceof ResponseFacade facade) {
            return facade;
         } else if (response instanceof HttpServletResponseWrapper wrapper) {
            HttpServletResponse wrappedResponse = (HttpServletResponse)wrapper.getResponse();
            return getResponseFacade(wrappedResponse);
         } else {
            throw new IllegalArgumentException("Cannot convert [" + response.getClass() + "] to org.apache.catalina.connector.ResponseFacade");
         }
      }

      @Override
      protected void applyHeaders() {
         this.adaptHeaders(true);
      }

      static {
         Field field = ReflectionUtils.findField(ResponseFacade.class, "response");
         Assert.state(field != null, "Incompatible Tomcat implementation");
         ReflectionUtils.makeAccessible(field);
         COYOTE_RESPONSE_FIELD = field;
      }
   }
}
