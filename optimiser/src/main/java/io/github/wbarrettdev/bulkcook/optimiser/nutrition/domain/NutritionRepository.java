package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

/** Where the nutrition tables are kept. */
public interface NutritionRepository {

    /** Every stored row. The table counts no veg and fruit until it is told what a portion weighs. */
    NutritionTable table();

    /**
     * Stores the rows this ingredient doesn't have yet: macros for its unit, then salt, fibre and
     * its five-a-day category. A row that is already there is left exactly as it was, so adding the
     * same ingredient twice changes nothing.
     */
    Stored add(IngredientNutrition ingredient);

    /** What became of one row. */
    enum Outcome {
        WRITTEN,
        ALREADY_THERE,
        NOT_GIVEN
    }

    /** What became of each of an ingredient's rows. */
    record Stored(Outcome macros, Outcome salt, Outcome fibre, Outcome fiveADay) {
        public boolean wroteNothing() {
            return macros != Outcome.WRITTEN
                    && salt != Outcome.WRITTEN
                    && fibre != Outcome.WRITTEN
                    && fiveADay != Outcome.WRITTEN;
        }
    }

    /** The tables could not be read or written. The message is written for whoever asked, model or person. */
    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("The nutrition tables can't be reached right now.", cause);
        }
    }
}
