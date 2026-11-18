package io.github.wbarrettdev.bulkcook.knowledge.pairing.application;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.DishResolver;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.FoodRules;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingAdvice.Dish;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Candidate;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Combination;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Pairing;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.Ranking;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Answers "what goes with this dish?" from the pairing graph, under the household's rules. */
@Service
@RequiredArgsConstructor
public class PairingAdvisor {

    private final PairingGraph graph;
    private final FoodRules houseRules;

    public PairingAdvice adviseOn(Request request) {
        Dish dish = new DishResolver(graph).resolve(request.ingredients());
        if (dish.hasNoIngredients()) {
            return PairingAdvice.nothingFor(dish);
        }
        FoodRules rules = houseRules.alsoForbidding(request.alsoExclude());
        List<Candidate> candidates = graph.partnersOf(dish.aliases());
        List<Combination> combinations =
                graph.combinationsSharing(dish.aliases(), dish.hasSeveralIngredients() ? 2 : 1);
        List<Combination> allowedCombinations = Ranking.allowedCombinations(combinations, rules);
        return new PairingAdvice(
                dish,
                allowedCombinations,
                combinations.size() - allowedCombinations.size(),
                Ranking.suggestions(candidates, rules).stream()
                        .limit(request.limit())
                        .toList(),
                Ranking.forbidden(candidates, rules),
                graph.avoidancesFor(dish.aliases()),
                graph.notesFor(dish.ingredients()));
    }

    /** Says whether an ingredient pairs with each of several others, looking in both directions. */
    public Check check(String ingredient, List<String> partners) {
        DishResolver dishes = new DishResolver(graph);
        Dish subject = dishes.resolve(List.of(ingredient));
        List<Verdict> verdicts = partners.stream()
                .map(partner -> verdict(subject, partner, dishes.resolve(List.of(partner))))
                .toList();
        return new Check(ingredient, !subject.hasNoIngredients(), verdicts);
    }

    private Verdict verdict(Dish subject, String partner, Dish other) {
        if (subject.hasNoIngredients() || other.hasNoIngredients()) {
            return new Verdict(partner, !other.hasNoIngredients(), false, List.of());
        }
        List<Pairing> pairings = graph.pairingsBetween(subject.aliases(), other.aliases());
        return new Verdict(partner, true, !pairings.isEmpty(), pairings);
    }

    public record Check(String ingredient, boolean known, List<Verdict> verdicts) {
        public Check {
            verdicts = List.copyOf(verdicts);
        }
    }

    /** Whether one partner is known to the graph, whether it pairs, and the pairings that say so. */
    public record Verdict(String partner, boolean known, boolean paired, List<Pairing> pairings) {
        public Verdict {
            pairings = List.copyOf(pairings);
        }
    }

    public record Request(List<String> ingredients, int limit, List<String> alsoExclude) {

        public static final int DEFAULT_LIMIT = 10;

        public Request {
            ingredients = List.copyOf(ingredients);
            alsoExclude = List.copyOf(alsoExclude);
            if (limit < 1) {
                throw new IllegalArgumentException("The limit must be at least 1, not " + limit);
            }
        }
    }
}
