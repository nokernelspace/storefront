import org.springframework.boot.SpringApplication;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;


@Configuration
@EnableWebMvc 
public class Tomcat {
    private static final System.Logger log = System.getLogger(Tomcat.class.getName());
    private static final Tomcat INSTANCE = new Tomcat(); 

    @RestController
    public static class RootController {
        @GetMapping("/")
        public String getIndex() {
            return "Hello World";
        }
    }


    @GetMapping(path = "/")
    public static String get_index() {
        return "Hello World";
    }

    public static void main(String[] args) {
        // 1. Create standard Spring Web Application Context
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(Tomcat.class);

        // 2. Wrap Spring MVC DispatcherServlet
        DispatcherServlet dispatcherServlet = new DispatcherServlet(context);

        // 3. Programmatically launch Tomcat WebServer
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(8080);
        WebServer server = factory.getWebServer(servletContext -> {
            var registration = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
            registration.setLoadOnStartup(1);
            registration.addMapping("/");
        });

        server.start();
        context.refresh();
    }
}