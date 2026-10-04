package dev.romoslayer.elapsed.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DowntimeTest {
	private static final long CAP = 1_728_000L;

	@Test
	void cleanShutdownCountsTheWholeGap() {
		// Saved on the way down, so the world kept no game time beyond the save
		assertEquals(1200L, ElapsedClock.downtimeTicks(60_000L, 0L, CAP));
	}

	/**
	 * The audit's example: clock saved at 0 s, world saved at +100 s (2000 more ticks), crash at +110 s, restart at
	 * +120 s. The 2000 ticks the world kept were the server running, not downtime.
	 */
	@Test
	void runningTimeTheWorldKeptIsNotDowntime() {
		assertEquals(400L, ElapsedClock.downtimeTicks(120_000L, 2000L, CAP));
	}

	@Test
	void neverNegative() {
		assertEquals(0L, ElapsedClock.downtimeTicks(10_000L, 5000L, CAP));
		assertEquals(0L, ElapsedClock.downtimeTicks(-60_000L, 0L, CAP));
	}

	@Test
	void aWorldRolledBackBeforeTheClockSaveCountsTheWholeGap() {
		assertEquals(1200L, ElapsedClock.downtimeTicks(60_000L, -5000L, CAP));
	}

	@Test
	void neverMoreThanTheCap() {
		assertEquals(CAP, ElapsedClock.downtimeTicks(365L * 24 * 3600 * 1000, 0L, CAP));
	}
}
