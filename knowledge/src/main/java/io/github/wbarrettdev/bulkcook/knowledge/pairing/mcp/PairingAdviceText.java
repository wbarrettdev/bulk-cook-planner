package io.github.wbarrettdev.bulkcook.knowledge.pairing.mcp;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.Suggestion;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.UnknownIngredient;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvisor;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Avoidance;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Combination;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.IngredientNotes;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Pairing;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Writes pairing advice as plain text for a model to read. Pure functions. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PairingAdviceText {

    private static final int COMBINATIONS_SHOWN = 8;
    private static final int LEFT_OUT_SHOWN = 12;

    static String of(PairingAdvice advice) {
        List<String> lines = new ArrayList<>();
        advice.dish().unknown().stream().map(PairingAdviceText::unknown).forEach(lines::add);
        if (advice.dish().hasNoIngredients()) {
            lines.add("Nothing to look up. Use an ingredient's plain name: salmon, not salmon fillets.");
            return String.join("\n", lines);
        }
        lines.add("Pairings for: " + String.join(", ", advice.dish().ingredients()));
        lines.add("");
        lines.addAll(combinations(advice));
        lines.add("");
        lines.addAll(suggestions(advice));
        lines.addAll(avoidances(advice.avoidances()));
        lines.add("");
        lines.addAll(notes(advice.notes()));
        lines.add("");
        lines.add("No amounts are given. Household rules are already applied.");
        return String.join("\n", lines);
    }

    static String of(PairingAdvisor.Check check) {
        if (!check.known()) {
            return "Not in the pairing graph: " + check.ingredient() + ".";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Does " + check.ingredient() + " pair with:");
        check.verdicts().stream().map(PairingAdviceText::verdict).forEach(lines::add);
        return String.join("\n", lines);
    }

    private static String verdict(PairingAdvisor.Verdict verdict) {
        if (!verdict.known()) {
            return "  " + verdict.partner() + ": not in the pairing graph";
        }
        if (!verdict.paired()) {
            return "  " + verdict.partner() + ": no";
        }
        String listings = verdict.pairings().stream()
                .map(pairing -> pairing.ingredient() + " lists " + pairing.partner() + " at " + pairing.strength())
                .sorted()
                .collect(Collectors.joining("; "));
        return "  " + verdict.partner() + ": yes (" + listings + ")";
    }

    private static String unknown(UnknownIngredient ingredient) {
        String start = "Not in the pairing graph: " + ingredient.asked() + ".";
        if (!ingredient.usedInstead().isEmpty()) {
            return start + " Using: " + String.join(", ", ingredient.usedInstead()) + ".";
        }
        if (!ingredient.closeNames().isEmpty()) {
            return start + " Close names: " + String.join(", ", ingredient.closeNames()) + ".";
        }
        return start;
    }

    private static List<String> combinations(PairingAdvice advice) {
        List<String> lines = new ArrayList<>();
        lines.add("Combinations the dish already half-matches (complete one of these first):");
        if (advice.combinations().isEmpty()) {
            lines.add("  none");
        }
        advice.combinations().stream()
                .limit(COMBINATIONS_SHOWN)
                .map(PairingAdviceText::combination)
                .forEach(lines::add);
        if (advice.combinationsLeftOut() > 0) {
            lines.add("  (" + advice.combinationsLeftOut() + " more left out by the household rules)");
        }
        return lines;
    }

    private static String combination(Combination combination) {
        return "  has " + joined(combination.present(), " + ") + "  ->  add " + joined(combination.missing(), " + ");
    }

    private static List<String> suggestions(PairingAdvice advice) {
        List<String> lines = new ArrayList<>();
        lines.add("Top additions (score = pairing strength, 1 to 4, added up across the dish):");
        advice.suggestions().stream().map(PairingAdviceText::suggestion).forEach(lines::add);
        if (!advice.suggestionsLeftOut().isEmpty()) {
            lines.add("Left out by the household rules: " + shortened(advice.suggestionsLeftOut()));
        }
        return lines;
    }

    private static String suggestion(Suggestion suggestion) {
        String reasons = suggestion.reasons().stream()
                .map(PairingAdviceText::reason)
                .sorted()
                .collect(Collectors.joining(", "));
        return String.format(
                "  %3d  %s%s   <- %s",
                suggestion.score(), suggestion.name(), suggestion.goEasy() ? "  (go easy)" : "", reasons);
    }

    private static String reason(Pairing pairing) {
        String note = pairing.note().isEmpty() ? "" : " [" + pairing.note() + "]";
        return pairing.ingredient() + " " + pairing.strength() + note;
    }

    private static List<String> avoidances(List<Avoidance> avoidances) {
        if (avoidances.isEmpty()) {
            return List.of();
        }
        String pairs = avoidances.stream()
                .map(avoidance -> avoidance.partner() + " with " + avoidance.ingredient())
                .collect(Collectors.joining("; "));
        return List.of("", "Avoid: " + pairs);
    }

    private static List<String> notes(List<IngredientNotes> notes) {
        List<String> lines = new ArrayList<>();
        lines.add("Each ingredient:");
        notes.stream().map(PairingAdviceText::note).forEach(lines::add);
        return lines;
    }

    private static String note(IngredientNotes notes) {
        if (notes.areEmpty()) {
            return "  " + notes.ingredient() + ": no notes";
        }
        String details = Stream.of(
                        labelled("techniques", notes.techniques()),
                        labelled("season", notes.season()),
                        labelled("tips", notes.tips()),
                        notes.seeAlso())
                .filter(detail -> !detail.isEmpty())
                .collect(Collectors.joining("; "));
        return "  " + notes.ingredient() + ": " + details;
    }

    private static String labelled(String label, String value) {
        return value.isEmpty() ? "" : label + ": " + value;
    }

    private static String joined(List<String> names, String separator) {
        return names.stream().sorted().collect(Collectors.joining(separator));
    }

    private static String shortened(List<String> names) {
        String shown = names.stream().limit(LEFT_OUT_SHOWN).collect(Collectors.joining(", "));
        return names.size() > LEFT_OUT_SHOWN ? shown + " ..." : shown;
    }
}
