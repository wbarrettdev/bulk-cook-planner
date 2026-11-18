package io.github.wbarrettdev.bulkcook.knowledge.pairing.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The household's rules about what may be suggested. */
public record FoodRules(Set<String> never, Set<String> goEasyOn, Set<String> basics, int goEasyPenalty) {

    public FoodRules {
        never = lowerCased(never);
        goEasyOn = lowerCased(goEasyOn);
        basics = lowerCased(basics);
    }

    public boolean forbids(String name, Collection<String> kindOf) {
        return mentioned(never, name, kindOf);
    }

    public boolean advisesGoingEasyOn(String name, Collection<String> kindOf) {
        return mentioned(goEasyOn, name, kindOf);
    }

    public boolean treatsAsBasic(String name) {
        return basics.contains(name);
    }

    public FoodRules alsoForbidding(Collection<String> more) {
        Set<String> extended = new HashSet<>(never);
        extended.addAll(more);
        return new FoodRules(extended, goEasyOn, basics, goEasyPenalty);
    }

    private static boolean mentioned(Set<String> words, String name, Collection<String> kindOf) {
        return Stream.concat(Stream.of(name), kindOf.stream())
                .anyMatch(text -> words.stream().anyMatch(word -> containsWord(text, word)));
    }

    /** "bacon" is in "smoked bacon" but "ham" is not in "champagne". */
    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b")
                .matcher(text)
                .find();
    }

    private static Set<String> lowerCased(Set<String> words) {
        return words.stream()
                .map(word -> word.strip().toLowerCase(Locale.ROOT))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
