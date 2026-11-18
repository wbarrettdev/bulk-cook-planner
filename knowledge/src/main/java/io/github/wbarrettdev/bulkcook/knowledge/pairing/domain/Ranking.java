package io.github.wbarrettdev.bulkcook.knowledge.pairing.domain;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.Suggestion;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Candidate;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Combination;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Turns what the graph holds into what to suggest, under the household's rules. Pure functions. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Ranking {

    private static final Comparator<Suggestion> BEST_FIRST = Comparator.comparingInt(Suggestion::score)
            .reversed()
            .thenComparing(suggestion -> -suggestion.reasons().size())
            .thenComparing(Suggestion::name);

    private static final Comparator<Combination> MOST_COMPLETE_FIRST = Comparator.comparingInt(
                    (Combination combination) -> -combination.present().size())
            .thenComparingInt(combination -> combination.missing().size());

    public static List<Suggestion> suggestions(List<Candidate> candidates, FoodRules rules) {
        return candidates.stream()
                .filter(candidate -> !candidate.isCuisine())
                .filter(candidate -> !rules.treatsAsBasic(candidate.name()))
                .filter(candidate -> !rules.forbids(candidate.name(), candidate.kindOf()))
                .map(candidate -> suggestion(candidate, rules))
                .sorted(BEST_FIRST)
                .toList();
    }

    public static List<String> forbidden(List<Candidate> candidates, FoodRules rules) {
        return candidates.stream()
                .filter(candidate -> rules.forbids(candidate.name(), candidate.kindOf()))
                .map(Candidate::name)
                .sorted()
                .toList();
    }

    public static List<Combination> allowedCombinations(List<Combination> combinations, FoodRules rules) {
        return combinations.stream()
                .filter(combination -> !needsSomethingForbidden(combination, rules))
                .sorted(MOST_COMPLETE_FIRST)
                .toList();
    }

    private static Suggestion suggestion(Candidate candidate, FoodRules rules) {
        boolean goEasy = rules.advisesGoingEasyOn(candidate.name(), candidate.kindOf());
        int score = candidate.combinedStrength() - (goEasy ? rules.goEasyPenalty() : 0);
        return new Suggestion(candidate.name(), score, goEasy, candidate.pairings());
    }

    private static boolean needsSomethingForbidden(Combination combination, FoodRules rules) {
        return combination.missing().stream().anyMatch(name -> rules.forbids(name, combination.kindsOfMissing()));
    }
}
