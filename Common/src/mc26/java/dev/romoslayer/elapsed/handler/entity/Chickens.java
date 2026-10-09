package dev.romoslayer.elapsed.handler.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Chickens and their eggs. The chicken class moved and egg laying changed between Minecraft versions, so each version
 * folder (Common/src/mc20, mc21, mc26) has its own copy of this class with the same methods; this one is for 26.x.
 */
final class Chickens {
	private Chickens() {
	}

	static boolean isChicken(Entity entity) {
		return entity instanceof Chicken;
	}

	static boolean isJockey(Entity chicken) {
		return ((Chicken) chicken).isChickenJockey();
	}

	/** Ticks until the next egg. */
	static int eggTime(Entity chicken) {
		return ((Chicken) chicken).eggTime;
	}

	static void setEggTime(Entity chicken, int ticks) {
		((Chicken) chicken).eggTime = ticks;
	}

	/** Lays one egg as the chicken itself would (from its laying loot table). Returns whether anything was dropped. */
	static boolean layEgg(Entity chicken, ServerLevel level) {
		Chicken laying = (Chicken) chicken;
		return laying.dropFromGiftLootTable(level, BuiltInLootTables.CHICKEN_LAY, laying::spawnAtLocation);
	}
}
