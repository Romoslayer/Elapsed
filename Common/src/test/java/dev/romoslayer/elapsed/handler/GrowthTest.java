package dev.romoslayer.elapsed.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class GrowthTest {
	@Test
	void noRateOrNoTimeMeansNoEvents() {
		RandomSource random = RandomSource.create(1L);
		assertEquals(0, Growth.sampleEvents(random, 0.0, 1000, 10));
		assertEquals(0, Growth.sampleEvents(random, Double.NaN, 1000, 10));
		assertEquals(0, Growth.sampleEvents(random, 0.5, 0, 10));
	}

	@Test
	void neverMoreThanTheLimit() {
		RandomSource random = RandomSource.create(2L);
		for (int i = 0; i < 1000; i++) {
			assertTrue(Growth.sampleEvents(random, 1.0, 1_000_000, 7) <= 7);
		}
	}

	/** The count of events is Poisson: its average over many draws is rate x time. */
	@Test
	void averageMatchesThePoissonMean() {
		RandomSource random = RandomSource.create(3L);
		double rate = 0.001;
		long ticks = 2500;
		int trials = 40_000;
		long total = 0;
		for (int i = 0; i < trials; i++) {
			total += Growth.sampleEvents(random, rate, ticks, 1000);
		}
		double mean = total / (double) trials;
		assertEquals(rate * ticks, mean, 0.05, "mean " + mean);
	}

	/** Vanilla CropBlock: one chance in (int) (25 / speed) + 1. */
	@Test
	void cropChanceMatchesVanilla() {
		assertEquals(1.0 / 26.0, Growth.cropChance(1.0F), 1e-12);
		assertEquals(1.0 / 3.0, Growth.cropChance(10.0F), 1e-12);
		assertEquals(1.0 / 6.0, Growth.cropChance(5.0F), 1e-12);
	}
}
