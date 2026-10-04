package dev.romoslayer.elapsed.core;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.api.EntityHandler;
import dev.romoslayer.elapsed.api.OfflineProgressHandler;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.time.ElapsedClock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * The running server's catch-up work. Chunks and entities that load with time to catch up on are queued; each tick a
 * limited number of them is brought up to date, once they are close enough to a player to be ticking (a chunk that is
 * merely loaded at the edge of view would not be doing anything in the game either). Until then their time is kept on
 * them, and goes back to disk with them if they unload first.
 *
 * <p>A chunk is always caught up in one go: all its supported block entities and blocks in the same tick, after which
 * its debt is cleared. The timestamp it is next saved with then starts from now. Because of that, the per-tick work
 * limit decides whether another chunk is started, not where one stops: a single very dense chunk can go over it.
 */
public final class CatchupManager {
	private static final int CLOCK_SAVE_INTERVAL = 6000;
	/** How many queued chunks or entities are looked at per tick for every one that may be caught up. */
	private static final int CHECKS_PER_SLOT = 8;
	/** Queue entries looked at per tick while catching up is switched off, to drop what has unloaded. */
	private static final int PRUNE_CHECKS = 256;
	/** Operations a scan of one 16x16x16 section counts as (it reads 4096 block states). */
	private static final int SECTION_SCAN_COST = 16;

	private final MinecraftServer server;
	private final ElapsedClock clock;
	private final RandomSource random = RandomSource.create();
	private final Map<ServerLevel, ArrayDeque<Pending<LevelChunk>>> chunkQueues = new IdentityHashMap<>();
	private final Map<ServerLevel, ArrayDeque<Pending<Entity>>> entityQueues = new IdentityHashMap<>();
	private HandlerRegistry handlers;
	private boolean debug;
	private int ticksSinceClockSave;
	private int rotation;

	private long chunksCaughtUp;
	private long blockEntitiesCaughtUp;
	private long blocksCaughtUp;
	private long entitiesCaughtUp;
	private long worstTickNanos;
	private long longestWaitTicks;
	private int burstTicks;
	private int burstChunks;
	private int burstEntities;
	private long burstOperations;
	private long burstNanos;
	private long burstWorstNanos;
	private long burstLongestWait;
	private @Nullable Burst lastBurst;

	/** A queued chunk or entity, and the tick it was first ready to be caught up (for measuring the delay). */
	private static final class Pending<T> {
		final T target;
		long readySince = -1L;

		Pending(T target) {
			this.target = target;
		}
	}

	public CatchupManager(MinecraftServer server) {
		this.server = server;
		this.clock = new ElapsedClock(server);
		this.handlers = HandlerRegistry.build();
	}

	public MinecraftServer server() {
		return this.server;
	}

	public ElapsedClock clock() {
		return this.clock;
	}

	public HandlerRegistry handlers() {
		if (this.handlers.isStale()) {
			this.handlers = HandlerRegistry.build();
		}
		return this.handlers;
	}

	/** After a config reload: also gives handlers that failed another chance. */
	public void rebuildHandlers() {
		this.handlers = HandlerRegistry.build();
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public boolean isDebug() {
		return this.debug || ElapsedConfig.get().general.debugLogging;
	}

	public static long pendingTicks(LevelChunk chunk) {
		return ((ElapsedChunk) chunk).elapsed$debt();
	}

	public static long pendingTicks(Entity entity) {
		return ((ElapsedEntity) entity).elapsed$debt();
	}

	// ---- Events

	public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		if (pendingTicks(chunk) > 0) {
			this.chunkQueues.computeIfAbsent(level, key -> new ArrayDeque<>()).add(new Pending<>(chunk));
		}
	}

	public void onEntityLoad(ServerLevel level, Entity entity) {
		if (pendingTicks(entity) > 0) {
			this.entityQueues.computeIfAbsent(level, key -> new ArrayDeque<>()).add(new Pending<>(entity));
		}
	}

	public void tick() {
		if (++this.ticksSinceClockSave >= CLOCK_SAVE_INTERVAL) {
			this.ticksSinceClockSave = 0;
			this.clock.save();
		}
		ElapsedConfig config = ElapsedConfig.get();
		boolean enabled = config.general.enabled;
		// While switched off the clock stands still, so the time is never caught up later
		this.clock.tick(enabled);
		if (!enabled) {
			this.prune();
			return;
		}
		long start = System.nanoTime();
		long now = this.server.getTickCount();
		this.rotation++;

		// Chunks: dimensions take turns at going first, so a busy one cannot starve the others
		long operations = 0;
		int chunks = 0;
		for (ServerLevel level : rotated(this.chunkQueues.keySet(), this.rotation)) {
			ArrayDeque<Pending<LevelChunk>> queue = this.chunkQueues.get(level);
			int checks = Math.min(queue.size(), config.performance.chunksPerTick * CHECKS_PER_SLOT);
			for (int i = 0; i < checks && chunks < config.performance.chunksPerTick && operations < config.performance.maxOperationsPerTick; i++) {
				Pending<LevelChunk> pending = queue.poll();
				LevelChunk chunk = pending.target;
				ChunkPos pos = chunk.getPos();
				if (pendingTicks(chunk) <= 0 || level.getChunkSource().getChunkNow(pos.x(), pos.z()) != chunk) {
					// Done already, or unloaded (its time went to disk with it)
					continue;
				}
				if (!Timestamps.isDimensionActive(level)) {
					((ElapsedChunk) chunk).elapsed$setDebt(0L);
					continue;
				}
				if (!level.shouldTickBlocksAt(pos.pack()) || !neighboursLoaded(level, pos)) {
					// Not doing anything in the game yet; or catching up could read into a chunk that is not loaded
					pending.readySince = -1L;
					queue.add(pending);
					continue;
				}
				operations += this.catchUpChunk(level, chunk);
				chunks++;
				this.recordWait(pending, now);
			}
		}

		// Entities have a budget of their own, so a stream of chunks cannot hold them up
		int entities = 0;
		for (ServerLevel level : rotated(this.entityQueues.keySet(), this.rotation)) {
			ArrayDeque<Pending<Entity>> queue = this.entityQueues.get(level);
			int checks = Math.min(queue.size(), config.performance.entitiesPerTick * CHECKS_PER_SLOT);
			for (int i = 0; i < checks && entities < config.performance.entitiesPerTick; i++) {
				Pending<Entity> pending = queue.poll();
				Entity entity = pending.target;
				if (pendingTicks(entity) <= 0 || entity.isRemoved() || entity.level() != level) {
					continue;
				}
				if (!Timestamps.isDimensionActive(level)) {
					((ElapsedEntity) entity).elapsed$setDebt(0L);
					continue;
				}
				if (!level.isPositionEntityTicking(entity.blockPosition())) {
					pending.readySince = -1L;
					queue.add(pending);
					continue;
				}
				operations += this.catchUpEntity(level, entity);
				entities++;
				this.recordWait(pending, now);
			}
		}
		this.recordTick(chunks, entities, operations, System.nanoTime() - start);
	}

	/** While switched off: nothing is caught up (the time stays on the chunks), but unloaded entries are dropped. */
	private void prune() {
		for (Map.Entry<ServerLevel, ArrayDeque<Pending<LevelChunk>>> entry : this.chunkQueues.entrySet()) {
			ArrayDeque<Pending<LevelChunk>> queue = entry.getValue();
			for (int i = Math.min(queue.size(), PRUNE_CHECKS); i > 0; i--) {
				Pending<LevelChunk> pending = queue.poll();
				ChunkPos pos = pending.target.getPos();
				if (pendingTicks(pending.target) > 0 && entry.getKey().getChunkSource().getChunkNow(pos.x(), pos.z()) == pending.target) {
					pending.readySince = -1L;
					queue.add(pending);
				}
			}
		}
		for (Map.Entry<ServerLevel, ArrayDeque<Pending<Entity>>> entry : this.entityQueues.entrySet()) {
			ArrayDeque<Pending<Entity>> queue = entry.getValue();
			for (int i = Math.min(queue.size(), PRUNE_CHECKS); i > 0; i--) {
				Pending<Entity> pending = queue.poll();
				if (pendingTicks(pending.target) > 0 && !pending.target.isRemoved() && pending.target.level() == entry.getKey()) {
					pending.readySince = -1L;
					queue.add(pending);
				}
			}
		}
	}

	/** The levels in a fixed order, starting at a different one each tick. */
	private static List<ServerLevel> rotated(Set<ServerLevel> levels, int rotation) {
		List<ServerLevel> list = new ArrayList<>(levels);
		if (list.size() > 1) {
			java.util.Collections.rotate(list, Math.floorMod(rotation, list.size()));
		}
		return list;
	}

	/** Whether the eight chunks around this one are loaded, so nothing caught up here can make the game load a chunk. */
	private static boolean neighboursLoaded(ServerLevel level, ChunkPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	// ---- Measuring

	/** Delay between a chunk or entity being ready (ticking) and actually being caught up. */
	private void recordWait(Pending<?> pending, long now) {
		// readySince is only set by the previous visit; a first-visit catch-up waited no time at all
		long wait = pending.readySince < 0 ? 0L : now - pending.readySince;
		this.burstLongestWait = Math.max(this.burstLongestWait, wait);
		this.longestWaitTicks = Math.max(this.longestWaitTicks, wait);
	}

	/** Keeps track of how long catching up takes, per tick and per burst (a run of ticks with work in them). */
	private void recordTick(int chunks, int entities, long operations, long nanos) {
		this.markReady();
		if (chunks == 0 && entities == 0) {
			if (this.burstTicks > 0) {
				this.lastBurst = new Burst(this.burstTicks, this.burstChunks, this.burstEntities, this.burstOperations, this.burstNanos,
						this.burstWorstNanos, this.burstLongestWait);
				if (this.isDebug()) {
					Elapsed.LOGGER.info("[Elapsed] Catch-up burst done: {}", this.lastBurst);
				}
				this.burstTicks = 0;
				this.burstChunks = 0;
				this.burstEntities = 0;
				this.burstOperations = 0;
				this.burstNanos = 0L;
				this.burstWorstNanos = 0L;
				this.burstLongestWait = 0L;
			}
			return;
		}
		this.burstTicks++;
		this.burstChunks += chunks;
		this.burstEntities += entities;
		this.burstOperations += operations;
		this.burstNanos += nanos;
		this.burstWorstNanos = Math.max(this.burstWorstNanos, nanos);
		this.worstTickNanos = Math.max(this.worstTickNanos, nanos);
	}

	/** Notes when queued work first became ready, so the delay until it is done can be measured. */
	private void markReady() {
		long now = this.server.getTickCount();
		for (Map.Entry<ServerLevel, ArrayDeque<Pending<LevelChunk>>> entry : this.chunkQueues.entrySet()) {
			for (Pending<LevelChunk> pending : entry.getValue()) {
				if (pending.readySince < 0 && entry.getKey().shouldTickBlocksAt(pending.target.getPos().pack())) {
					pending.readySince = now;
				}
			}
		}
		for (ArrayDeque<Pending<Entity>> queue : this.entityQueues.values()) {
			for (Pending<Entity> pending : queue) {
				if (pending.readySince < 0) {
					pending.readySince = now;
				}
			}
		}
	}

	/** A run of consecutive ticks in which something was caught up. */
	public record Burst(int ticks, int chunks, int entities, long operations, long nanos, long worstTickNanos, long longestWaitTicks) {
		@Override
		public String toString() {
			return String.format(Locale.ROOT,
					"%d chunk(s) and %d entity(s) over %d tick(s), %.1f ms in total, worst tick %.2f ms (%d operations), longest wait %d tick(s)",
					this.chunks, this.entities, this.ticks, this.nanos / 1.0e6, this.worstTickNanos / 1.0e6, this.operations, this.longestWaitTicks);
		}
	}

	// ---- Chunks

	/** Brings everything supported in a chunk up to date. Returns the work done, in operations. */
	private long catchUpChunk(ServerLevel level, LevelChunk chunk) {
		long debt = pendingTicks(chunk);
		CatchupContext context = new CatchupContext(level, debt, this.random, this.isDebug());
		HandlerRegistry registry = this.handlers();
		long operations = 1;
		int touched = 0;

		for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
			if (blockEntity.isRemoved()) {
				continue;
			}
			HandlerRegistry.Entry<BlockEntityHandler<?>> entry = registry.forBlockEntity(blockEntity);
			if (entry != null) {
				operations++;
				if (this.run(registry, entry.id(), entry.handler(), blockEntity, context)) {
					touched++;
					this.blockEntitiesCaughtUp++;
				}
			}
		}

		List<BlockPos> candidates = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(registry::mayHandle)) {
				continue;
			}
			operations += SECTION_SCAN_COST;
			int minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
			for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
				for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
					for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
						if (registry.mayHandle(section.getBlockState(x, y, z))) {
							candidates.add(new BlockPos(minX + x, minY + y, minZ + z));
						}
					}
				}
			}
		}
		// Collected first, so blocks grown by one plant are not caught up again as plants of their own
		for (BlockPos pos : candidates) {
			BlockState state = chunk.getBlockState(pos);
			HandlerRegistry.Entry<BlockHandler<?>> entry = registry.forState(state);
			if (entry == null) {
				continue;
			}
			operations += registry.cost(entry);
			if (this.run(registry, entry.id(), entry.handler(), new BlockTarget(level, chunk, pos, state), context)) {
				touched++;
				this.blocksCaughtUp++;
			}
		}

		((ElapsedChunk) chunk).elapsed$setDebt(0L);
		if (touched > 0) {
			// Save the chunk with a fresh timestamp even if nothing changed, so the same time is not tried again
			chunk.markUnsaved();
		}
		this.chunksCaughtUp++;
		if (context.isDebug() && touched > 0) {
			ChunkPos pos = chunk.getPos();
			Elapsed.LOGGER.info("[Elapsed] Caught up chunk [{}, {}] in {}: {} ticks away, {} thing(s) checked", pos.x(), pos.z(),
					level.dimension().identifier(), debt, touched);
			for (String note : context.notes()) {
				Elapsed.LOGGER.info("[Elapsed]   {}", note);
			}
		}
		return operations;
	}

	// ---- Entities

	private long catchUpEntity(ServerLevel level, Entity entity) {
		long debt = pendingTicks(entity);
		((ElapsedEntity) entity).elapsed$setDebt(0L);
		CatchupContext context = new CatchupContext(level, debt, this.random, this.isDebug());
		HandlerRegistry registry = this.handlers();
		List<HandlerRegistry.Entry<EntityHandler<?>>> entries = registry.forEntity(entity);
		// Every handler looks at the entity before any of them changes it (a chick's egg timer depends on its age)
		List<Runnable> results = new ArrayList<>(entries.size());
		for (HandlerRegistry.Entry<EntityHandler<?>> entry : entries) {
			Runnable apply = this.prepare(registry, entry.id(), entry.handler(), entity, context);
			if (apply != null) {
				results.add(apply);
			}
		}
		for (Runnable apply : results) {
			apply.run();
		}
		if (!entries.isEmpty()) {
			this.entitiesCaughtUp++;
		}
		if (context.isDebug() && !context.notes().isEmpty()) {
			Elapsed.LOGGER.info("[Elapsed] Caught up {} ticks for {}", debt, entity);
			for (String note : context.notes()) {
				Elapsed.LOGGER.info("[Elapsed]   {}", note);
			}
		}
		return 1L + entries.size();
	}

	// ---- Running handlers

	/** Captures, calculates and applies. Returns whether the handler had anything to look at. */
	private <T, S> boolean run(HandlerRegistry registry, Identifier id, OfflineProgressHandler<T, S> handler, T target, CatchupContext context) {
		Runnable apply = this.prepare(registry, id, handler, target, context);
		if (apply == null) {
			return false;
		}
		apply.run();
		return true;
	}

	/** Captures and calculates; the returned step applies the result (null when there was nothing to look at). */
	private <T, S> @Nullable Runnable prepare(HandlerRegistry registry, Identifier id, OfflineProgressHandler<T, S> handler, T target,
			CatchupContext context) {
		if (registry.isFailed(id)) {
			return null;
		}
		try {
			S state = handler.captureState(target, context);
			if (state == null) {
				return null;
			}
			long elapsed = Math.min(context.rawElapsedTicks(), HandlerRegistry.capTicks(handler.category()));
			S result = handler.calculateProgress(state, elapsed, context);
			return () -> {
				if (result == null || registry.isFailed(id)) {
					return;
				}
				try {
					handler.applyResult(target, result, context);
				} catch (RuntimeException | LinkageError e) {
					registry.fail(id, e);
				}
			};
		} catch (RuntimeException | LinkageError e) {
			registry.fail(id, e);
			return null;
		}
	}

	// ---- Status

	public int queuedChunks() {
		return this.chunkQueues.values().stream().mapToInt(ArrayDeque::size).sum();
	}

	public int queuedEntities() {
		return this.entityQueues.values().stream().mapToInt(ArrayDeque::size).sum();
	}

	public long chunksCaughtUp() {
		return this.chunksCaughtUp;
	}

	public long blockEntitiesCaughtUp() {
		return this.blockEntitiesCaughtUp;
	}

	public long blocksCaughtUp() {
		return this.blocksCaughtUp;
	}

	public long entitiesCaughtUp() {
		return this.entitiesCaughtUp;
	}

	/** The slowest single tick of catching up since the server started, in nanoseconds. */
	public long worstTickNanos() {
		return this.worstTickNanos;
	}

	/** The longest a ready chunk or entity waited in the queue since the server started, in ticks. */
	public long longestWaitTicks() {
		return this.longestWaitTicks;
	}

	public @Nullable Burst lastBurst() {
		return this.lastBurst;
	}

	public Set<Identifier> failedHandlers() {
		return this.handlers.failedHandlers();
	}

	public void shutdown() {
		this.clock.save();
		this.chunkQueues.clear();
		this.entityQueues.clear();
	}
}
