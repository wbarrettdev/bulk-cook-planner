# optimiser data

One PostgreSQL database, `nutrition`. The optimiser owns it and no other service reads it.

The tables are created by Flyway migrations in `src/main/resources/db/migration/`, which run when the service starts.

## Reference foods

| Table | Holds |
|---|---|
| `cofid_food` | The UK's reference foods, CoFID 2021. 2,886 foods, loaded by a migration |

Each row is a food as the dataset publishes it, for 100g of the edible part: `code`, `name`, `food_group`, `kcal`, `protein_g`, `fat_g`, `carbohydrate_g`, `starch_g`, `total_sugars_g`, `aoac_fibre_g`, `nsp_g`, `sodium_mg`.

The figures are stored unchanged. CoFID measures carbohydrate and fibre differently from a food label, and gives sodium where a label gives salt, so they're converted when a food is read, not when it's stored. A value the dataset doesn't give is `NULL`, which is not the same as none.

This table is only read. USDA FoodData Central and Open Food Facts are searched live, and nothing of theirs is kept until a row is added from one of their records.

## Ingredients

What the solver reads. One ingredient is spread over four tables, keyed by its name.

| Table | Key | Holds |
|---|---|---|
| `ingredient_macros` | `ingredient`, `unit` | `grams_per_unit`, and `kcal`, `protein`, `fat` and `carbs` per 100g |
| `ingredient_salt` | `ingredient` | Salt per 100g |
| `ingredient_fibre` | `ingredient` | Fibre per 100g |
| `ingredient_five_a_day` | `ingredient` | The category it counts under: `veg`, `fruit`, `dried`, `pulse` or `juice` |

The first three also have a `source` column naming the record the values came from, such as `cofid:13-234 Curly kale, raw`, so every value can be traced.

A few rules the tables hold to:

- Names are lower case.
- An ingredient has one macros row for each unit it's measured in: `g`, `tsp` and so on. An empty unit means one of the thing, such as one egg.
- A name written `name|text` is a second set of values for the same ingredient. `white rice|dry` is used when a recipe's note for the rice says dry.
- An ingredient with no row in `ingredient_five_a_day` doesn't count towards five a day.

These tables start empty. They fill as rows are added with `add_nutrition_row`, and adding never changes a row that's already there.
