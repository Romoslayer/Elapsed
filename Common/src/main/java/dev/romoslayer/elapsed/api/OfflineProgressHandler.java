package dev.romoslayer.elapsed.api;

import org.jspecify.annotations.Nullable;

/**
 * Brings one kind of thing up to date after time it spent unloaded. Elapsed never runs the thing while it is away;
 * when it is loaded again the handler is asked, once, what that time would have done to it:
 *
 * <ol>
 * <li>{@link #captureState} reads what matters from the world (null: nothing to catch up);</li>
 * <li>{@link #calculateProgress} works out the state after the elapsed time without touching the world;</li>
 * <li>{@link #applyResult} writes that state back, checking again that it is still valid.</li>
 * </ol>
 *
 * <p>The elapsed time given to {@link #calculateProgress} has already been cut down to the handler's cap, and Elapsed
 * makes sure the same stretch of time is never handed out twice. Handlers must still never create items or blocks
 * that the captured state does not account for.
 *
 * <p>All three steps run on the server thread, one straight after the other.
 *
 * @param <T> what is caught up: a {@link BlockTarget}, a block entity or an entity
 * @param <S> the handler's own snapshot of that thing's state
 */
public interface OfflineProgressHandler<T, S> {
	/**
	 * The settings group this handler belongs to, which decides its catch-up cap. One of the {@link CatchupCategory}
	 * names, or any other name (which uses the global cap).
	 */
	String category();

	boolean canHandle(T target);

	@Nullable S captureState(T target, CatchupContext context);

	/** The state after {@code elapsedTicks} of unattended time, or null if nothing would have changed. */
	@Nullable S calculateProgress(S previous, long elapsedTicks, CatchupContext context);

	void applyResult(T target, S result, CatchupContext context);
}
