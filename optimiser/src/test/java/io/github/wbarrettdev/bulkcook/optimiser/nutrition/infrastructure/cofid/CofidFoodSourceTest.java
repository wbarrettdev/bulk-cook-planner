package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.cofid;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodValues;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Searches the real CoFID foods, loaded by the migration into a real PostgreSQL. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CofidFoodSourceTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CofidFoodSource cofid;

    @Test
    void findsFoodsNamedWithEveryWordPlainestFirst() {
        List<FoodRecord> found = cofid.search("lentils red");

        assertThat(found)
                .extracting(FoodRecord::name)
                .containsExactly(
                        "Lentils, red, split, dried, raw", "Lentils, red, split, dried, boiled in unsalted water");
    }

    @Test
    void givesTheFiguresALabelWould() {
        FoodRecord kale = cofid.search("Kale, raw").getFirst();

        assertThat(kale)
                .isEqualTo(new FoodRecord(
                        new FoodReference(Source.COFID, "13-234"),
                        "Curly kale, raw",
                        "",
                        new FoodValues(33.0, 3.4, 1.6, 1.3, 4.1, 0.11),
                        FiveADayCategory.VEG,
                        "fibre from NSP x1.33"));
    }

    @Test
    void listsNoMoreThanAFewRecords() {
        assertThat(cofid.search("chicken")).hasSize(8);
    }

    @Test
    void takesAPercentSignAsAPercentSign() {
        assertThat(cofid.search("%")).allMatch(food -> food.name().contains("%"));
        assertThat(cofid.search("zzz_zzz")).isEmpty();
    }

    @Test
    void findsNothingForWordsNoFoodHas() {
        assertThat(cofid.search("dragon fruit sorbet")).isEmpty();
        assertThat(cofid.search(" , ")).isEmpty();
    }

    @Test
    void findsOneFoodByItsCode() {
        assertThat(cofid.find("13-657"))
                .get()
                .extracting(FoodRecord::name)
                .isEqualTo("Lentils, red, split, dried, raw");
        assertThat(cofid.find("00-000")).isEmpty();
    }
}
