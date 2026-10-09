package dev.romoslayer.elapsed.handler.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Items;

/**
 * Chickens and their eggs. The chicken class moved and egg laying changed between Minecraft versions, so each version
 * folder (Common/src/mc20, mc21, mc26) has its own copy of this class with the same methods; this one is for 1.21.x and
 * 1.20.x.
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

	/** Lays one egg as the chicken itself would (an egg dropped where it stands). Returns whether anything was dropped. */
	static boolean layEgg(Entity chicken, ServerLevel level) {
		return chicken.spawnAtLocation(Items.EGG) != null;
	}
}
