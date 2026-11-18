package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.Arrays;
import java.util.Optional;

/** The reference sources a food's nutrition values can come from. */
public enum Source {
    COFID("cofid", "CoFID"),
    USDA("usda", "USDA FoodData Central"),
    OPEN_FOOD_FACTS("off", "Open Food Facts");

    private final String key;
    private final String title;

    Source(String key, String title) {
        this.key = key;
        this.title = title;
    }

    /** The short name a record is cited by, as in "cofid:13-234". */
    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public static Optional<Source> withKey(String key) {
        return Arrays.stream(values()).filter(source -> source.key.equals(key)).findFirst();
    }
}
