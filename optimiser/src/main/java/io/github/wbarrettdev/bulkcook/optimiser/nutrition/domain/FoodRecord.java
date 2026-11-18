package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

/**
 * One food in a reference source, with what 100g of it holds.
 *
 * @param detail what tells this record from its neighbours in a search but isn't part of its name,
 *     such as a pack size. Empty when there is none.
 * @param suggestedFiveADay the five-a-day category the source's own grouping points to, or null. A
 *     suggestion only: the grouping can't tell a potato from a carrot.
 * @param fibreNote how the fibre value was arrived at, when it wasn't measured the way labels
 *     measure it. Empty when it was.
 */
public record FoodRecord(
        FoodReference reference,
        String name,
        String detail,
        FoodValues per100g,
        FiveADayCategory suggestedFiveADay,
        String fibreNote) {

    /** What a nutrition row gives as its source, so every value can be traced to its record. */
    public String citation() {
        return (reference + " " + name).strip();
    }

    public String fibreCitation() {
        return fibreNote.isEmpty() ? citation() : citation() + " (" + fibreNote + ")";
    }
}
