package io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.config;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.FoodRules;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The household's food rules*/
@ConfigurationProperties(prefix = "pairing.rules")
public record PairingRulesProperties(
        @DefaultValue Set<String> never,
        @DefaultValue Set<String> goEasyOn,
        @DefaultValue Set<String> basics,
        @DefaultValue("2") int goEasyPenalty) {

    public FoodRules toFoodRules() {
        return new FoodRules(never, goEasyOn, basics, goEasyPenalty);
    }
}
