package io.github.wbarrettdev.bulkcook.optimiser.week.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void roundsAnExactHalfToTheEvenNeighbour() {
        assertThat(Numbers.round(2.5)).isEqualTo(2);
        assertThat(Numbers.round(3.5)).isEqualTo(4);
        assertThat(Numbers.round(0.125, 2)).isEqualTo(0.12);
    }

    @Test
    void roundsOnTheNumbersExactValue() {
        assertThat(Numbers.fixed(4.85, 1)).isEqualTo("4.8");
        assertThat(Numbers.fixed(24.5, 0)).isEqualTo("24");
    }

    @Test
    void writesANumberWithoutANeedlessDecimalPoint() {
        assertThat(Numbers.plain(440.0)).isEqualTo("440");
        assertThat(Numbers.plain(2.5)).isEqualTo("2.5");
        assertThat(Numbers.plain(6.25)).isEqualTo("6.25");
    }
}
