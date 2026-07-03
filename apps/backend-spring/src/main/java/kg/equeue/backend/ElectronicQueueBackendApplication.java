package kg.equeue.backend;

import kg.equeue.backend.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties.class)
@EnableScheduling
public class ElectronicQueueBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElectronicQueueBackendApplication.class, args);
    }
}
