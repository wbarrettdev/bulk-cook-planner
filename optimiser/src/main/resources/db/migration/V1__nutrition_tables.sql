-- The nutrition tables: what 100g of each ingredient holds, and what one unit of it weighs.
-- They live in the optimiser's own database, nutrition.

CREATE TABLE ingredient_macros (
    ingredient       text    NOT NULL CHECK (ingredient <> '' AND ingredient = lower(btrim(ingredient))),
    unit             text    NOT NULL DEFAULT '' CHECK (unit = lower(btrim(unit))),
    grams_per_unit   numeric NOT NULL CHECK (grams_per_unit >= 0),
    kcal_per_100g    numeric NOT NULL CHECK (kcal_per_100g >= 0),
    protein_per_100g numeric NOT NULL CHECK (protein_per_100g >= 0),
    fat_per_100g     numeric NOT NULL CHECK (fat_per_100g >= 0),
    carbs_per_100g   numeric NOT NULL CHECK (carbs_per_100g >= 0),
    source           text    NOT NULL DEFAULT '',
    PRIMARY KEY (ingredient, unit)
);

COMMENT ON TABLE ingredient_macros IS
    'Calories and macros for an ingredient, one row for each unit it is measured in.';
COMMENT ON COLUMN ingredient_macros.ingredient IS
    'Lower case. A "name|text" row is used for that ingredient when its note contains the text, such as "white rice|dry".';
COMMENT ON COLUMN ingredient_macros.unit IS
    'g, ml, tsp and so on. Empty means one of the thing, such as one egg.';
COMMENT ON COLUMN ingredient_macros.source IS
    'Where the values came from, so each one can be traced.';

CREATE TABLE ingredient_salt (
    ingredient      text    PRIMARY KEY CHECK (ingredient <> '' AND ingredient = lower(btrim(ingredient))),
    salt_g_per_100g numeric NOT NULL CHECK (salt_g_per_100g >= 0),
    source          text    NOT NULL DEFAULT ''
);

CREATE TABLE ingredient_fibre (
    ingredient       text    PRIMARY KEY CHECK (ingredient <> '' AND ingredient = lower(btrim(ingredient))),
    fibre_g_per_100g numeric NOT NULL CHECK (fibre_g_per_100g >= 0),
    source           text    NOT NULL DEFAULT ''
);

CREATE TABLE ingredient_five_a_day (
    ingredient text PRIMARY KEY CHECK (ingredient <> '' AND ingredient = lower(btrim(ingredient))),
    category   text NOT NULL CHECK (category IN ('veg', 'fruit', 'dried', 'pulse', 'juice'))
);

COMMENT ON TABLE ingredient_five_a_day IS
    'The ingredients that count towards five a day. Anything not listed does not count.';
