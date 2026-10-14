package main;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.net.httpserver.HttpServer;

import io.github.mimerme.core.RouteHandler;
import io.github.mimerme.yaros.Utils;

@RestController
@Configuration
public class Storefront {
    private static final System.Logger log = System.getLogger(Storefront.class.getName());
    private static final Storefront INSTANCE = new Storefront();

    @GetMapping(path = "/", consumes = "text/html")
    public static String get_home()  {
        try {
            String response = Files.readString(Path.of(Utils.cwd() + "/templates/index.html"));
            return response;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "<p>Error</p>";
        } 
    }

    @PostMapping(path = "/", consumes = "application/json")
    public static String api_welcome(){
        return "404";
    }

    @GetMapping(path = {"/account", "/login"})
    public static String login() {
        return "login";
    }

    public static void registerDynamicPage(HttpServer server, String methodName, RouteHandler templateHandler) throws NoSuchMethodException, SecurityException {
        Method m = Storefront.class.getDeclaredMethod(methodName);
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
        //String[] endpoints = mapping.path();
        //String path = Arrays.stream(endpoints).reduce((a, b) -> {return a + b;}).get();

        String path = mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0];
        server.createContext(path, exchange -> {
            //TODO: 
        });
    }
    public static void registerAPI(HttpServer server, String methodName, RouteHandler templateHandler) throws NoSuchMethodException, SecurityException {
        Method m = Storefront.class.getDeclaredMethod(methodName);
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
        //String[] endpoints = mapping.path();
        //String path = Arrays.stream(endpoints).reduce((a, b) -> {return a + b;}).get();

        String path = mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0];
        server.createContext(path, exchange -> {
            // TODO: 
        });
    }
    public static void registerStaticPage(HttpServer server, String methodName, RouteHandler templateHandler) throws NoSuchMethodException, SecurityException {
        Method m = Storefront.class.getDeclaredMethod(methodName);
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
        //String[] endpoints = mapping.path();
        //String path = Arrays.stream(endpoints).reduce((a, b) -> {return a + b;}).get();

        String path = mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0];
        server.createContext(path, exchange -> {
            String response;
            try {
                // TODO: imeplement the vargs...
                response = (String) m.invoke(null);

                // Convert down to UTF-8 since Java uses UTF-16 for `String` internally
                // otherwise bytes.length will have a mis-match
                // KiM: Java still prefers 'static + 'sized memory allocations 
                // ... so .getBytes()/.getBytes(UTF-16) will be double the length 
                // ... even though UTF is a variable length encoding format
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                System.out.println(response);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                exchange.sendResponseHeaders(404, 0);
                e.printStackTrace();
            } 
        });
    }

 	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(Storefront.class);
        int port = Integer.parseInt(context.getEnvironment().getProperty("server.port"));
		log.log(System.Logger.Level.INFO, "Starting on port: {0}", port);
		log.log(System.Logger.Level.INFO, "Current Working Directory: {0}", Utils.cwd());
        INSTANCE.start(port);
	}

    public void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            registerDynamicPage(server, "login", Storefront::login);
            registerStaticPage(server, "get_home", Storefront::get_home);

            // TODO: not working
            //registerAPI(server, "api_welcome", Storefront::api_welcome);

            // TODO: use spring-webmvc DispatcherServerlet somewhere here
            server.setExecutor(null); // TODO: this might be single-threaded?

            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }  catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}