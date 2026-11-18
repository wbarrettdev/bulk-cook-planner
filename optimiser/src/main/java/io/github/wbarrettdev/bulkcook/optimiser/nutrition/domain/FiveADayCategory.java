package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.Arrays;
import java.util.Optional;

/** The kinds of food that count towards five a day. Each kind has its own portion size. */
public enum FiveADayCategory {
    VEG,
    FRUIT,
    DRIED,
    PULSE,
    JUICE;

    /** The category's name in the nutrition table and in the targets: veg, fruit and so on. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Optional<FiveADayCategory> withKey(String key) {
        return Arrays.stream(values())
                .filter(category -> category.key().equals(key))
                .findFirst();
    }
}
