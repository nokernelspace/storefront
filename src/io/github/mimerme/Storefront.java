package io.github.mimerme;

import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;




// @Configuration
// public class VanillaJavaServer {
//     public static void main(String[] args) {
//         SpringApplication.run(Server.class, args);
//     }
//     @Bean
//     public CommandLineRunner hello() {
//         return args -> {
//             System.out.println("hello world");
//         };
//     }

//     @Bean
//     public CommandLineRunner runServer() {
//         return args -> {
//             // Create an HTTP server listening on port 8080 using JDK's built-in HttpServer
//             HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

//             server.createContext("/greeting", new GreetingHandler());
//             server.setExecutor(null); // default executor
//             server.start();

//             System.out.println("Server started on port 8080");
//         };
//     }



//     static class GreetingHandler implements HttpHandler {
//         private static final String TEMPLATE = "Hello, %s!";
//         private final AtomicLong counter = new AtomicLong();

//         @Override
//         public void handle(HttpExchange exchange) throws IOException {
//             if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
//                 URI requestUri = exchange.getRequestURI();
//                 String query = requestUri.getQuery();

//                 String name = "World";
//                 if (query != null && query.startsWith("name=")) {
//                     name = query.substring(5);
//                 }

//                 // Greeting greeting = new Greeting(counter.incrementAndGet(),
//                 // String.format(TEMPLATE, name));
//                 //String responseJson = greeting.toJson();

//                 exchange.getResponseHeaders().set("Content-Type", "application/json");
//                 exchange.sendResponseHeaders(200, "Hello World".getBytes().length);

//                 // try (OutputStream os = exchange.getResponseBody()) {
//                 //     os.write(responseJson.getBytes());
//                 // }
//             } else {
//                 exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
//             }
//         }
//     }
// }

@Controller
public class Storefront {
    @GetMapping("/")
    public String hello() {
        return "index"; // Maps to templates/index.html
    }

    @PostMapping("/")
    public String world(){
        return "index";
    }

    @Service 
    public Object test(){

    }
}