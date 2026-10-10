package dev.romoslayer.elapsed.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BurstTest {
	@Test
	void theWorstTickKeepsItsOwnWorkNotTheWholeBurst() {
		CatchupManager.BurstTracker tracker = new CatchupManager.BurstTracker();
		tracker.tick(2, 0, 2800L, 4_000_000L);
		// The slowest tick did less work than the others: slow for some other reason (a GC pause, say)
		tracker.tick(1, 0, 900L, 15_000_000L);
		tracker.tick(2, 5, 2700L, 6_000_000L);
		CatchupManager.Burst burst = tracker.finish();

		assertNotNull(burst);
		assertEquals(15_000_000L, burst.worstTickNanos());
		assertEquals(900L, burst.worstTickOperations());
		assertEquals(6400L, burst.operations());
		assertEquals(25_000_000L, burst.nanos());
		assertEquals(3, burst.ticks());
		assertEquals(5, burst.chunks());
		assertEquals(5, burst.entities());
	}

	@Test
	void theStatusLineSaysWhichCountIsWhich() {
		CatchupManager.BurstTracker tracker = new CatchupManager.BurstTracker();
		tracker.tick(2, 0, 2800L, 4_000_000L);
		tracker.tick(1, 0, 900L, 15_000_000L);
		tracker.waited(40L);
		String text = String.valueOf(tracker.finish());

		assertTrue(text.contains("3700 operations in total"), text);
		assertTrue(text.contains("worst tick 15.00 ms (900 operations)"), text);
		assertTrue(text.contains("longest wait 40 tick(s)"), text);
	}

	@Test
	void aTieKeepsTheFirstSlowestTick() {
		CatchupManager.BurstTracker tracker = new CatchupManager.BurstTracker();
		tracker.tick(1, 0, 1000L, 5_000_000L);
		tracker.tick(1, 0, 3000L, 5_000_000L);
		assertEquals(1000L, tracker.finish().worstTickOperations());
	}

	@Test
	void finishingStartsTheNextBurstFromNothing() {
		CatchupManager.BurstTracker tracker = new CatchupManager.BurstTracker();
		assertNull(tracker.finish());

		tracker.tick(3, 1, 2500L, 20_000_000L);
		tracker.waited(300L);
		assertNotNull(tracker.finish());
		assertNull(tracker.finish());

		// A short, fast burst after a long, slow one reports only itself
		tracker.tick(1, 0, 200L, 1_000_000L);
		CatchupManager.Burst next = tracker.finish();
		assertNotNull(next);
		assertEquals(1_000_000L, next.worstTickNanos());
		assertEquals(200L, next.worstTickOperations());
		assertEquals(200L, next.operations());
		assertEquals(0L, next.longestWaitTicks());
		assertEquals(1, next.ticks());
	}
}
