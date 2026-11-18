package io.github.wbarrettdev.bulkcook.knowledge.pairing.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Flavour pairings as a graph: ingredients linked by what goes with what. It reports what the graph
 * holds. It applies no household rules and ranks nothing.
 */
public interface PairingGraph {

    List<Ingredient> ingredientsNamed(Collection<String> spellings);

    List<String> namesContaining(String fragment);

    List<Candidate> partnersOf(Collection<String> dish);

    List<Combination> combinationsSharing(Collection<String> dish, int atLeast);

    List<Avoidance> avoidancesFor(Collection<String> dish);

    List<IngredientNotes> notesFor(Collection<String> ingredients);

    List<Pairing> pairingsBetween(Collection<String> these, Collection<String> those);

    /** A name as the graph has it, whether it is an entry, and the entries it is a kind of. */
    record Ingredient(String name, boolean hasOwnEntry, List<String> kindOf) {
        public Ingredient {
            kindOf = List.copyOf(kindOf);
        }
    }

    /** One pairing: an ingredient's entry lists a partner, at a strength from 1 to 4. */
    record Pairing(String ingredient, String partner, int strength, String note) {
        public Pairing {
            if (strength < 1 || strength > 4) {
                throw new IllegalArgumentException("A pairing's strength is 1 to 4, not " + strength);
            }
            note = note == null ? "" : note;
        }
    }

    /** Something the dish's ingredients pair with, together with every pairing that names it. */
    record Candidate(String name, boolean isCuisine, Set<String> kindOf, List<Pairing> pairings) {
        public Candidate {
            kindOf = Set.copyOf(kindOf);
            pairings = List.copyOf(pairings);
        }

        public int combinedStrength() {
            return pairings.stream().mapToInt(Pairing::strength).sum();
        }
    }

    /** A combination known to work: the members the dish has, and the ones to add. */
    record Combination(List<String> present, List<String> missing, Set<String> kindsOfMissing) {
        public Combination {
            present = List.copyOf(present);
            missing = List.copyOf(missing);
            kindsOfMissing = Set.copyOf(kindsOfMissing);
        }
    }

    record Avoidance(String ingredient, String partner) {}

    /** The graph could not be reached. The message is written for whoever asked, model or person. */
    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("The pairing graph can't be reached, so there are no pairings to offer right now.", cause);
        }
    }

    record IngredientNotes(String ingredient, String techniques, String season, String tips, String seeAlso) {
        public boolean areEmpty() {
            return techniques.isEmpty() && season.isEmpty() && tips.isEmpty() && seeAlso.isEmpty();
        }
    }
}
