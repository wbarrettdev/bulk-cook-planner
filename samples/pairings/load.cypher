// Rebuilds the pairing graph from the CSV files in volumes/graphdb/import/.
// Run from the repo folder:
//   docker compose exec -T graphdb cypher-shell < samples/pairings/load.cypher
MATCH (n) WHERE n:Ingredient OR n:Cuisine OR n:Set DETACH DELETE n;
CREATE CONSTRAINT ingredient_name IF NOT EXISTS FOR (i:Ingredient) REQUIRE i.name IS UNIQUE;
CREATE CONSTRAINT set_id IF NOT EXISTS FOR (s:Set) REQUIRE s.id IS UNIQUE;

LOAD CSV WITH HEADERS FROM 'file:///ingredients.csv' AS r
CREATE (:Ingredient {name: r.name, is_entry: r.is_entry = 'True', heading: r.heading, season: r.season,
                     taste: r.taste, weight: r.weight, volume: r.volume, techniques: r.techniques,
                     function: r.function, tips: r.tips, see: r.see});

LOAD CSV WITH HEADERS FROM 'file:///pairings.csv' AS r
MATCH (a:Ingredient {name: r.from}), (b:Ingredient {name: r.to})
CREATE (a)-[:PAIRS_WITH {strength: toInteger(r.strength), note: r.note, source: r.source}]->(b);

LOAD CSV WITH HEADERS FROM 'file:///avoids.csv' AS r
MATCH (a:Ingredient {name: r.from}), (b:Ingredient {name: r.to})
CREATE (a)-[:AVOID {source: r.source}]->(b);

LOAD CSV WITH HEADERS FROM 'file:///kinds.csv' AS r
MATCH (a:Ingredient {name: r.child}), (b:Ingredient {name: r.parent})
CREATE (a)-[:KIND_OF]->(b);

LOAD CSV WITH HEADERS FROM 'file:///sets.csv' AS r
CREATE (:Set {id: toInteger(r.set_id), listed_under: r.listed_under, size: toInteger(r.size), source: r.source});

LOAD CSV WITH HEADERS FROM 'file:///set_members.csv' AS r
MATCH (i:Ingredient {name: r.name}), (s:Set {id: toInteger(r.set_id)})
MERGE (i)-[:IN_SET]->(s);

// Entries that are cuisines, not ingredients.
MATCH (n:Ingredient) WHERE n.name ENDS WITH ' cuisine' SET n:Cuisine REMOVE n:Ingredient;
