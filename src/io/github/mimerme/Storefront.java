package io.github.mimerme;

import org.springframework.stereotype.Controller;
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
}