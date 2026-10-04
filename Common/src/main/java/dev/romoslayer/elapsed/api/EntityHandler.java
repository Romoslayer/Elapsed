package dev.romoslayer.elapsed.api;

import net.minecraft.world.entity.Entity;

/**
 * Catches up a simple timer on an entity, such as a baby animal's age. Only timers: never movement, AI, combat or
 * anything else that needs the entity to actually be ticked.
 */
public interface EntityHandler<S> extends OfflineProgressHandler<Entity, S> {
}
