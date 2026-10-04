package dev.romoslayer.elapsed.handler.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class EggLayingTest {
	@Test
	void theCapLimitsEggsButNotTheTimer() {
		RandomSource random = RandomSource.create(1L);
		for (int i = 0; i < 1000; i++) {
			ChickenEggHandler.Laying laying = ChickenEggHandler.lay(100, 100_000, 2, random);
			assertEquals(2, laying.eggs());
			assertTrue(laying.nextEggTime() >= 1 && laying.nextEggTime() <= 12_000, "timer " + laying.nextEggTime());
		}
	}

	@Test
	void aCapOfZeroLaysNothing() {
		ChickenEggHandler.Laying laying = ChickenEggHandler.lay(0, 50_000, 0, RandomSource.create(2L));
		assertEquals(0, laying.eggs());
		assertTrue(laying.nextEggTime() >= 1 && laying.nextEggTime() <= 12_000);
	}

	/** One egg when the timer runs out, then one every 6000 to 12000 ticks: about window / 9000 in all. */
	@Test
	void withoutACapTheCountFollowsTheIntervals() {
		RandomSource random = RandomSource.create(3L);
		long total = 0;
		int trials = 5000;
		for (int i = 0; i < trials; i++) {
			total += ChickenEggHandler.lay(0, 90_000, 64, random).eggs();
		}
		double mean = total / (double) trials;
		assertTrue(mean > 10.0 && mean < 11.5, "mean " + mean);
	}

	@Test
	void aVeryLongWindowStaysCheap() {
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
			RandomSource random = RandomSource.create(4L);
			for (int i = 0; i < 10_000; i++) {
				ChickenEggHandler.Laying laying = ChickenEggHandler.lay(10, 630_720_000L, 2, random);
				assertEquals(2, laying.eggs());
				assertTrue(laying.nextEggTime() >= 1 && laying.nextEggTime() <= 12_000);
			}
		});
	}
}
