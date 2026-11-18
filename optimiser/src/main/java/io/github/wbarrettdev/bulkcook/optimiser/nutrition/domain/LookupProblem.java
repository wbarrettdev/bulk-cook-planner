package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

/** A lookup that can't go ahead as asked. The message says what to change, for a model or a person. */
public class LookupProblem extends RuntimeException {

    public LookupProblem(String message) {
        super(message);
    }
}
