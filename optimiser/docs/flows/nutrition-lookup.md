# Looking up nutrition

An ingredient gets into the nutrition table in two calls: `search_nutrition` to find a record, then `add_nutrition_row` to store it.

```mermaid
sequenceDiagram
    participant C as MCP client
    participant T as nutrition tools
    participant L as NutritionLookup
    participant R as reference source
    participant D as nutrition database

    C->>T: search_nutrition(source, query)
    T->>L: search
    L->>R: search for the food
    R-->>L: a few records, values per 100g
    L-->>T: records
    T-->>C: records, each with its id

    Note over C: picks the record that matches what goes in the pan

    C->>T: add_nutrition_row(record, ingredient, unit, grams, category)
    T->>L: add
    L->>R: fetch that record again
    R-->>L: record
    L->>L: refuse a record with a value missing
    L->>D: insert macros, salt, fibre, five-a-day in one transaction
    D-->>L: which rows were written
    L-->>T: what was written, what was already there
    T-->>C: the same, as text and as structured content
```

## The three sources

| Source | Where it's read from | Used for |
|---|---|---|
| `cofid` | A table in the nutrition database | First choice. The UK's reference foods |
| `usda` | USDA FoodData Central, over HTTP | Whole foods that are the same anywhere, when CoFID has no good match |
| `off` | Open Food Facts, over HTTP | Packaged foods the other two don't have |

Each source measures things its own way, so each is converted to the figures a European food label gives: carbohydrate by its own weight with fibre apart, and salt in place of sodium.

The two HTTP sources are asked again, up to three times, when they answer that they're busy. Open Food Facts has a second search service that's used when the main one is down.

## Why the record is fetched twice

`add_nutrition_row` takes a record's id, not its values. The values are read from the source again at the moment they're stored, so what goes into the table is the source's figures and not a copy that passed through the caller.

## Adding never changes a row

Each insert leaves a row alone if it's already there. Adding the same ingredient twice changes nothing, and adding an ingredient that only lacks fibre fills in the fibre and nothing else. The answer says which rows were written and which were left.
