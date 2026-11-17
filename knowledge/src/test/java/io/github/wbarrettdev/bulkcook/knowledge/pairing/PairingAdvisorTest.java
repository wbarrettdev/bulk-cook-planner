package io.github.wbarrettdev.bulkcook.knowledge.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.Suggestion;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.UnknownIngredient;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Avoidance;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Combination;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PairingAdvisorTest {

    private static final FoodRules NO_RULES = new FoodRules(Set.of(), Set.of(), Set.of(), 2);

    private final FakeGraph graph = new FakeGraph()
            .entry("salmon")
            .entry("potatoes")
            .entry("leeks")
            .entry("chicken")
            .entry("lemons")
            .pairs("salmon", "dill", 4)
            .pairs("potatoes", "dill", 2)
            .pairs("salmon", "lemon", 3)
            .pairs("salmon", "bacon", 3)
            .pairs("potatoes", "smoked bacon", 2)
            .pairs("salmon", "salt", 3)
            .pairs("salmon", "tomatoes", 3)
            .pairs("salmon", "morels", 2)
            .pairs("chicken", "tarragon", 3)
            .kindOf("morels", "mushrooms")
            .kindOf("chicken thighs", "chicken")
            .entry("fish")
            .kindOf("salmon", "fish")
            .pairs("fish", "parsley", 3)
            .cuisine("japanese cuisine")
            .pairs("salmon", "japanese cuisine", 3)
            .combination("salmon", "potatoes", "chives")
            .combination("salmon", "potatoes", "morels")
            .combination("chicken", "lemon", "garlic")
            .avoid("salmon", "cinnamon");

    @Test
    void ranksAdditionsByTheirCombinedStrengthAcrossTheDish() {
        PairingAdvice advice = advise(NO_RULES, "salmon", "potatoes");

        assertThat(advice.suggestions()).first().extracting(Suggestion::name).isEqualTo("dill");
        assertThat(advice.suggestions()).first().extracting(Suggestion::score).isEqualTo(6);
    }

    @Test
    void leavesOutWhatTheHouseholdNeverEats() {
        FoodRules noBacon = new FoodRules(Set.of("bacon"), Set.of(), Set.of(), 2);

        PairingAdvice advice = advise(noBacon, "salmon", "potatoes");

        assertThat(names(advice)).doesNotContain("bacon", "smoked bacon");
        assertThat(advice.suggestionsLeftOut()).containsExactly("bacon", "smoked bacon");
    }

    @Test
    void leavesOutAKindOfSomethingTheHouseholdNeverEats() {
        FoodRules noMushrooms = new FoodRules(Set.of("mushrooms"), Set.of(), Set.of(), 2);

        PairingAdvice advice = advise(noMushrooms, "salmon");

        assertThat(names(advice)).doesNotContain("morels");
    }

    @Test
    void leavesOutBasicsAndCuisines() {
        FoodRules saltIsBasic = new FoodRules(Set.of(), Set.of(), Set.of("salt"), 2);

        PairingAdvice advice = advise(saltIsBasic, "salmon");

        assertThat(names(advice)).doesNotContain("salt", "japanese cuisine");
        assertThat(advice.suggestionsLeftOut()).isEmpty();
    }

    @Test
    void marksDownWhatTheHouseholdGoesEasyOn() {
        FoodRules easyOnTomatoes = new FoodRules(Set.of(), Set.of("tomatoes"), Set.of(), 2);

        Suggestion tomatoes = advise(easyOnTomatoes, "salmon").suggestions().stream()
                .filter(suggestion -> suggestion.name().equals("tomatoes"))
                .findFirst()
                .orElseThrow();

        assertThat(tomatoes.goEasy()).isTrue();
        assertThat(tomatoes.score()).isEqualTo(1);
    }

    @Test
    void offersTheCombinationsTheDishHalfMatches() {
        PairingAdvice advice = advise(NO_RULES, "salmon", "potatoes");

        assertThat(advice.combinations())
                .extracting(Combination::missing)
                .containsExactlyInAnyOrder(List.of("chives"), List.of("morels"));
    }

    @Test
    void dropsACombinationThatNeedsSomethingTheHouseholdNeverEats() {
        FoodRules noMushrooms = new FoodRules(Set.of("mushrooms"), Set.of(), Set.of(), 2);

        PairingAdvice advice = advise(noMushrooms, "salmon", "potatoes");

        assertThat(advice.combinations()).extracting(Combination::missing).containsExactly(List.of("chives"));
        assertThat(advice.combinationsLeftOut()).isEqualTo(1);
    }

    @Test
    void findsTheEntryBehindACutOfMeat() {
        PairingAdvice advice = advise(NO_RULES, "chicken thighs");

        assertThat(advice.dish().ingredients()).containsExactly("chicken");
        assertThat(names(advice)).contains("tarragon");
    }

    @Test
    void doesNotBorrowFromACategoryWhenTheIngredientHasItsOwnEntry() {
        PairingAdvice advice = advise(NO_RULES, "salmon");

        assertThat(names(advice)).doesNotContain("parsley");
    }

    @Test
    void searchesOnlyWithNamesTheGraphHas() {
        PairingAdvice advice = advise(NO_RULES, "chicken thighs", "lemons");

        assertThat(advice.dish().aliases()).containsExactlyInAnyOrder("chicken thighs", "chicken", "lemons", "lemon");
    }

    @Test
    void treatsSingularAndPluralAsOneIngredient() {
        PairingAdvice advice = advise(NO_RULES, "chicken", "lemons");

        assertThat(advice.combinations()).extracting(Combination::missing).containsExactly(List.of("garlic"));
    }

    @Test
    void fallsBackToAWordThatIsAnEntry() {
        PairingAdvice advice = advise(NO_RULES, "salmon fillets");

        assertThat(advice.dish().ingredients()).containsExactly("salmon");
        assertThat(advice.dish().unknown())
                .containsExactly(new UnknownIngredient("salmon fillets", List.of("salmon"), List.of()));
    }

    @Test
    void suggestsCloseNamesForAnIngredientItCannotFind() {
        PairingAdvice advice = advise(NO_RULES, "tarragons vinegar");

        assertThat(advice.dish().hasNoIngredients()).isTrue();
        assertThat(advice.suggestions()).isEmpty();
        assertThat(advice.dish().unknown()).hasSize(1);
    }

    @Test
    void listsNoMoreThanTheLimit() {
        PairingAdvisor advisor = new PairingAdvisor(graph, NO_RULES);

        PairingAdvice advice = advisor.adviseOn(new PairingAdvisor.Request(List.of("salmon"), 2, List.of()));

        assertThat(advice.suggestions()).hasSize(2);
    }

    @Test
    void leavesOutExtrasAskedForThisDish() {
        PairingAdvisor advisor = new PairingAdvisor(graph, NO_RULES);

        PairingAdvice advice = advisor.adviseOn(new PairingAdvisor.Request(List.of("salmon"), 10, List.of("Dill")));

        assertThat(names(advice)).doesNotContain("dill");
    }

    @Test
    void passesOnWhatToAvoid() {
        PairingAdvice advice = advise(NO_RULES, "salmon");

        assertThat(advice.avoidances()).containsExactly(new Avoidance("salmon", "cinnamon"));
    }

    @Test
    void confirmsAPairingListedInEitherDirection() {
        PairingAdvisor advisor = new PairingAdvisor(graph, NO_RULES);

        assertThat(advisor.check("dill", List.of("salmon")).verdicts())
                .extracting(PairingAdvisor.Verdict::paired)
                .containsExactly(true);
        assertThat(advisor.check("salmon", List.of("dill")).verdicts())
                .extracting(PairingAdvisor.Verdict::paired)
                .containsExactly(true);
    }

    @Test
    void saysNoWhenTwoIngredientsAreNotPaired() {
        PairingAdvisor.Check check = new PairingAdvisor(graph, NO_RULES).check("dill", List.of("tarragon"));

        assertThat(check.verdicts()).containsExactly(new PairingAdvisor.Verdict("tarragon", true, false, List.of()));
    }

    @Test
    void saysWhenAPartnerIsNotInTheGraph() {
        PairingAdvisor.Check check = new PairingAdvisor(graph, NO_RULES).check("salmon", List.of("zzzz"));

        assertThat(check.verdicts()).containsExactly(new PairingAdvisor.Verdict("zzzz", false, false, List.of()));
    }

    @Test
    void checksUnderAnyOfAnIngredientsNames() {
        PairingAdvisor.Check check = new PairingAdvisor(graph, NO_RULES).check("salmon", List.of("lemons"));

        assertThat(check.verdicts()).extracting(PairingAdvisor.Verdict::paired).containsExactly(true);
    }

    private PairingAdvice advise(FoodRules rules, String... ingredients) {
        return new PairingAdvisor(graph, rules)
                .adviseOn(new PairingAdvisor.Request(List.of(ingredients), 10, List.of()));
    }

    private static List<String> names(PairingAdvice advice) {
        return advice.suggestions().stream().map(Suggestion::name).toList();
    }
}
