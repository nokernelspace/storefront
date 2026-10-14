package main;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


// This driver hosts a web server using Java's original Sun HttpServer implementation 
@Configuration
public class Vanilla {
    public static void main(String[] args) {
        SpringApplication.run(Vanilla.class, args);
    }
    @Bean
    public CommandLineRunner hello() {
        return args -> {
            System.out.println("hello world");
        };
    }

    @Bean
    public CommandLineRunner runServer() {
        return args -> {
            // Create an HTTP server listening on port 8080 using JDK's built-in HttpServer
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            server.createContext("/hello", new HelloHandler());
            server.setExecutor(null); // default executor
            server.start();

            System.out.println("Server started on port 8080");
        };
    }

    class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
             if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                 URI requestUri = exchange.getRequestURI();
                 String query = requestUri.getQuery();

                 String name = "World";
                 if (query != null && query.startsWith("name=")) {
                     name = query.substring(5);
                 }

                 // Greeting greeting = new Greeting(counter.incrementAndGet(),
                 // String.format(TEMPLATE, name));
                 //String responseJson = greeting.toJson();

                 exchange.getResponseHeaders().set("Content-Type", "application/json");
                 exchange.sendResponseHeaders(200, "Hello World".getBytes().length);

                 // try (OutputStream os = exchange.getResponseBody()) {
                 //     os.write(responseJson.getBytes());
                 // }
             } else {
                 exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
             }
        }
    }
}

