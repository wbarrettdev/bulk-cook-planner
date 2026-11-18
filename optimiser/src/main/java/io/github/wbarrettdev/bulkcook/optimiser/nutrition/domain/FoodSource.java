package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.List;
import java.util.Optional;

/** One reference source of nutrition values, searched by a food's name. */
public interface FoodSource {

    Source source();

    /** The records that best match the words, closest first. */
    List<FoodRecord> search(String query);

    Optional<FoodRecord> find(String id);

    /** The source could not be reached. The message is written for whoever asked, model or person. */
    class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }

        public Unavailable(Source source, Throwable cause) {
            this(source.title() + " can't be reached right now. Try again later, or try another source.", cause);
        }
    }
}
