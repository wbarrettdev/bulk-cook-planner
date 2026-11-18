package io.github.wbarrettdev.bulkcook.knowledge;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.FoodRules;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.config.PairingRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeApplication {

    @Bean
    FoodRules houseRules(PairingRulesProperties rules) {
        return rules.toFoodRules();
    }

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
