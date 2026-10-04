package dev.romoslayer.elapsed.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.api.ElapsedApi;
import dev.romoslayer.elapsed.api.EntityHandler;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** F1 from the audit: a handler that throws while being asked what it handles must never escape. */
class HandlerIsolationTest {
	private static final Identifier BAD_BLOCK = Identifier.fromNamespaceAndPath("test", "bad_block");
	private static final Identifier BAD_COST = Identifier.fromNamespaceAndPath("test", "bad_cost");
	private static final Identifier BAD_BLOCK_ENTITY = Identifier.fromNamespaceAndPath("test", "bad_block_entity");
	private static final Identifier BAD_ENTITY = Identifier.fromNamespaceAndPath("test", "bad_entity");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void unregister() {
		ElapsedApi.unregister(BAD_BLOCK);
		ElapsedApi.unregister(BAD_COST);
		ElapsedApi.unregister(BAD_BLOCK_ENTITY);
		ElapsedApi.unregister(BAD_ENTITY);
	}

	/** Base for test handlers: does nothing but fail where told to. */
	private abstract static class Nothing<T> {
		public String category() {
			return "test";
		}

		public @Nullable Object captureState(T target, CatchupContext context) {
			return null;
		}

		public @Nullable Object calculateProgress(Object previous, long elapsedTicks, CatchupContext context) {
			return null;
		}

		public void applyResult(T target, Object result, CatchupContext context) {
		}
	}

	private static final class ThrowingBlockHandler extends Nothing<BlockTarget> implements BlockHandler<Object> {
		@Override
		public boolean handles(BlockState state) {
			throw new IllegalStateException("handles() broke");
		}
	}

	private static final class ThrowingCostHandler extends Nothing<BlockTarget> implements BlockHandler<Object> {
		@Override
		public boolean handles(BlockState state) {
			return state.is(Blocks.CARROTS);
		}

		@Override
		public int cost() {
			throw new IllegalStateException("cost() broke");
		}
	}

	private static final class ThrowingBlockEntityHandler extends Nothing<BlockEntity> implements BlockEntityHandler<Object> {
		@Override
		public boolean canHandle(BlockEntity target) {
			throw new IllegalStateException("canHandle(block entity) broke");
		}
	}

	private static final class ThrowingEntityHandler extends Nothing<Entity> implements EntityHandler<Object> {
		@Override
		public boolean canHandle(Entity target) {
			throw new IllegalStateException("canHandle(entity) broke");
		}
	}

	@Test
	void aThrowingBlockSelectorIsSwitchedOffAndElapsedsOwnHandlerTakesOver() {
		ElapsedApi.registerBlockHandler(BAD_BLOCK, new ThrowingBlockHandler());
		HandlerRegistry registry = HandlerRegistry.build();

		HandlerRegistry.Entry<BlockHandler<?>> wheat = registry.forState(Blocks.WHEAT.defaultBlockState());
		assertNotNull(wheat, "the built-in crops handler still handles wheat");
		assertEquals("elapsed:crops", wheat.id().toString());
		assertTrue(registry.isFailed(BAD_BLOCK));
		// Asked again, the failed handler is not consulted (and nothing throws)
		assertNotNull(registry.forState(Blocks.POTATOES.defaultBlockState()));
		assertNull(registry.forState(Blocks.STONE.defaultBlockState()));
	}

	@Test
	void aThrowingCostCountsAsOneAndSwitchesTheHandlerOff() {
		ElapsedApi.registerBlockHandler(BAD_COST, new ThrowingCostHandler());
		HandlerRegistry registry = HandlerRegistry.build();
		HandlerRegistry.Entry<BlockHandler<?>> carrots = registry.forState(Blocks.CARROTS.defaultBlockState());
		assertNotNull(carrots);
		assertEquals(BAD_COST, carrots.id());
		assertEquals(1, registry.cost(carrots));
		assertTrue(registry.isFailed(BAD_COST));
		// From now on carrots go to Elapsed's own crop handler
		assertEquals("elapsed:crops", registry.forState(Blocks.CARROTS.defaultBlockState()).id().toString());
	}

	@Test
	void aThrowingBlockEntitySelectorDoesNotEscape() {
		ElapsedApi.registerBlockEntityHandler(BAD_BLOCK_ENTITY, new ThrowingBlockEntityHandler());
		HandlerRegistry registry = HandlerRegistry.build();
		// Any block entity will do: the bad handler throws before looking at it, and the built-ins just say no
		assertNull(registry.forBlockEntity(new net.minecraft.world.level.block.entity.SignBlockEntity(net.minecraft.core.BlockPos.ZERO,
				Blocks.OAK_SIGN.defaultBlockState())));
		assertTrue(registry.isFailed(BAD_BLOCK_ENTITY));
	}

	/** tracks() runs while entities are being saved: it must not throw, whatever a handler does. */
	@Test
	void aThrowingEntitySelectorDoesNotBreakSaving() {
		ElapsedApi.registerEntityHandler(BAD_ENTITY, new ThrowingEntityHandler());
		HandlerRegistry registry = HandlerRegistry.build();
		assertFalse(registry.tracks(null));
		assertTrue(registry.forEntity(null).isEmpty());
		assertTrue(registry.isFailed(BAD_ENTITY));
		assertEquals(1, registry.failedHandlers().size());
	}

	@Test
	void aReloadGivesAFailedHandlerAnotherChance() {
		ElapsedApi.registerEntityHandler(BAD_ENTITY, new ThrowingEntityHandler());
		HandlerRegistry first = HandlerRegistry.build();
		first.tracks(null);
		assertTrue(first.isFailed(BAD_ENTITY));
		assertFalse(HandlerRegistry.build().isFailed(BAD_ENTITY));
	}
}
