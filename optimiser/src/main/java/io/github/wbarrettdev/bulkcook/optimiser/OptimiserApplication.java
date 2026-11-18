package io.github.wbarrettdev.bulkcook.optimiser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OptimiserApplication {

    public static void main(String[] args) {
        SpringApplication.run(OptimiserApplication.class, args);
    }
}
