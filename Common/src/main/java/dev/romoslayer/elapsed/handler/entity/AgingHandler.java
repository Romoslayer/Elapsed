package dev.romoslayer.elapsed.handler.entity;

import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.api.EntityHandler;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/**
 * A mob's age is one number: below zero it is a baby that counts up to 0 and grows up, above zero it is an adult that
 * has just bred and counts down to 0 before it can breed again. Both simply move by the elapsed time. Love mode (after
 * being fed) runs out the same way. Nothing is ever bred; age-locked babies stay babies.
 */
public final class AgingHandler implements EntityHandler<AgingHandler.Ages> {
	public record Ages(int age, int inLove, int newAge, int newInLove) {
	}

	@Override
	public String category() {
		return CatchupCategory.ANIMALS;
	}

	@Override
	public boolean canHandle(Entity target) {
		ElapsedConfig.Animals config = ElapsedConfig.get().animals;
		return target instanceof AgeableMob && (config.aging || config.breedingCooldowns) && !isExcluded(target);
	}

	static boolean isExcluded(Entity entity) {
		return ElapsedConfig.get().animals.excluded.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
	}

	@Override
	public @Nullable Ages captureState(Entity target, CatchupContext context) {
		AgeableMob mob = (AgeableMob) target;
		if (!mob.isAlive()) {
			return null;
		}
		int inLove = mob instanceof Animal animal ? animal.getInLoveTime() : 0;
		return new Ages(mob.getAge(), inLove, mob.getAge(), inLove);
	}

	@Override
	public @Nullable Ages calculateProgress(Ages ages, long elapsedTicks, CatchupContext context) {
		// Whether a baby may grow at all (it can be age-locked) is checked on the mob itself in applyResult
		ElapsedConfig.Animals config = ElapsedConfig.get().animals;
		int newAge = ages.age;
		if (ages.age < 0 && config.aging) {
			newAge = (int) Math.min(0L, ages.age + elapsedTicks);
		} else if (ages.age > 0 && config.breedingCooldowns) {
			newAge = (int) Math.max(0L, ages.age - elapsedTicks);
		}
		int newInLove = config.breedingCooldowns ? (int) Math.max(0L, ages.inLove - elapsedTicks) : ages.inLove;
		if (newAge == ages.age && newInLove == ages.inLove) {
			return null;
		}
		return new Ages(ages.age, ages.inLove, newAge, newInLove);
	}

	@Override
	public void applyResult(Entity target, Ages result, CatchupContext context) {
		AgeableMob mob = (AgeableMob) target;
		if (!mob.isAlive() || mob.getAge() != result.age) {
			return;
		}
		boolean grows = result.age < 0 && mob.canAgeUp();
		if (grows || result.age > 0) {
			mob.setAge(result.newAge);
		}
		if (mob instanceof Animal animal && result.newInLove != result.inLove) {
			animal.setInLoveTime(result.newInLove);
		}
		if (context.isDebug()) {
			context.note(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()) + " at " + mob.blockPosition().toShortString() + ": age " + result.age + " -> "
				+ mob.getAge() + (result.newInLove != result.inLove ? ", love " + result.inLove + " -> " + result.newInLove : ""));
		}
	}
}
