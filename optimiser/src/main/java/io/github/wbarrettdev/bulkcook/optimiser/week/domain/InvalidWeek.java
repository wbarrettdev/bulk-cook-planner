package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

/** The week or a table is wrong. The message says what to fix. */
public class InvalidWeek extends RuntimeException {

    public InvalidWeek(String message) {
        super(message);
    }
}
