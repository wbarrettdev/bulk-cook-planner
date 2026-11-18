package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Rounding and number text, done the same way everywhere so the report matches what was checked. */
public final class Numbers {

    private Numbers() {}

    /** Rounds half to even on the number's exact value. */
    public static double round(double value, int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static long round(double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).longValue();
    }

    /** The number to a fixed count of decimal places, rounded the same way as everything else. */
    public static String fixed(double value, int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** A number without a needless ".0": 440 for 440.0, 2.5 for 2.5. Up to six significant figures. */
    public static String plain(double value) {
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(6)).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
    }
}
