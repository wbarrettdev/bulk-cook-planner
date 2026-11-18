package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the nutrition table writes a unit, so adding a row and reading it back agree. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Units {

    private static final String ONE_OF_THEM = "each";

    /** Lower case, and empty for a counted item such as one egg, whether or not it was written "each". */
    static String asStored(String unit) {
        String written = unit == null ? "" : unit.strip().toLowerCase(Locale.ROOT);
        return written.equals(ONE_OF_THEM) ? "" : written;
    }
}
