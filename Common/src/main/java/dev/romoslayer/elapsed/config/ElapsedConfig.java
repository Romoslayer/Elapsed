package dev.romoslayer.elapsed.config;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.config.ConfigBinder.Comment;
import dev.romoslayer.elapsed.config.ConfigBinder.Range;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config/elapsed.toml. Options missing from the file keep their defaults, and the file is rewritten after every
 * successful load so options added by an update appear in it. A file that cannot be parsed is left alone and the
 * defaults are used until it is fixed.
 */
public final class ElapsedConfig {
	/** Game ticks in one hour of play at the normal 20 ticks per second. */
	public static final long TICKS_PER_HOUR = 72000L;

	private static final String FILE_NAME = "elapsed.toml";
	private static final String HEADER = """
			Elapsed - offline progression for Minecraft servers.

			When a chunk unloads, everything in it stops. Elapsed does not keep chunks loaded and does not simulate
			them: when a chunk (or an animal) loads again, it works out once how much time passed and what the
			supported things in it would have done in that time - furnaces smelt, crops grow, babies grow up - and
			applies the result. Players do not need the mod.

			Times are given in hours of play at the normal 20 ticks per second (1 hour = 72000 ticks = 3 in-game
			days). Every system can only catch up on as much time as its own cap and the global cap allow.

			Reload with /elapsed reload.""";

	private static volatile ElapsedConfig instance = new ElapsedConfig();
	private static Path file;
	// Whether a config has been put in place yet (a failed reload keeps it rather than falling back to the defaults)
	private static boolean loaded;

	@Comment("Basic switches and the global catch-up limit.")
	public General general = new General();
	@Comment("Which dimensions catch up.")
	public Dimensions dimensions = new Dimensions();
	@Comment("""
			Spreading the work out. Catching up happens in the ticks after a chunk loads, a few chunks per tick, so a
			player teleporting into a large old base does not freeze the server.""")
	public Performance performance = new Performance();
	@Comment("""
			Crops and other plants that grow on random ticks: wheat, carrots, potatoes, beetroot, torchflowers, melon and
			pumpkin stems, nether wart, cocoa, sweet berries, sugar cane, cactus, bamboo, kelp and the weeping, twisting
			and cave vines. Growth is estimated from the game's own growth chances, not replayed tick by tick, and only
			happens where the plant could grow right now (enough light, the right ground, room above).""")
	public Crops crops = new Crops();
	@Comment("""
			Saplings only move on to their second growth stage; the tree itself is grown by the game as usual once the
			chunk is loaded, so no tree is ever placed while nobody can see whether there is room for it. A sapling lit
			only by the sky is assumed to have enough light half of the time (daytime); one under a torch or lamp all of it.""")
	public Saplings saplings = new Saplings();
	@Comment("""
			Copper oxidation. Follows the game's rule that a block ages more slowly while it has younger copper nearby
			(and not at all next to younger copper). Waxed copper never changes.""")
	public Copper copper = new Copper();
	@Comment("""
			Furnaces, smokers and blast furnaces carry on with what is in them: fuel burns, items smelt, the output
			slot fills up. Cooking stops for good when the input or fuel runs out or the output slot is full, exactly
			as it would have.""")
	public Furnaces furnaces = new Furnaces();
	@Comment("Brewing stands finish brewing what is in them, using up blaze powder and ingredients as normal.")
	public Brewing brewing = new Brewing();
	@Comment("Food on lit campfires (and soul campfires) finishes cooking and pops off, as normal.")
	public Campfires campfires = new Campfires();
	@Comment("""
			Animals (and other mobs that grow up) carry on growing up, and their breeding cooldowns run out. Nothing is
			bred while unloaded, and animals never move, fight or wander because of Elapsed.""")
	public Animals animals = new Animals();
	@Comment("Chickens keep counting down to their next egg.")
	public Chickens chickens = new Chickens();
	@Comment("""
			Optional link with the Seasonfall mod. Crops unloaded across several seasons grow by the seasons they
			actually spent unloaded (for example 30% spring, 40% summer, 30% autumn), not by the season it is when they
			load again.""")
	public Seasonfall seasonfall = new Seasonfall();
	@Comment("""
			Extra blocks (usually from other mods) that grow by counting up an age property on random ticks, like wheat.
			The key is the block id. Only use this for plants whose growth really is that simple.
			  property      name of the age property (usually "age")
			  growthChance  chance that one random tick makes the plant one stage older (wheat on farmland is about 0.33)
			  minLight      light the plant needs to grow (0 for none)
			Example:
			  [ageBasedBlocks."somemod:tomato"]
			  property = "age"
			  growthChance = 0.25
			  minLight = 9""")
	public Map<String, AgeBlock> ageBasedBlocks = new LinkedHashMap<>();
	@Comment("Switch off individual catch-up handlers, including ones added by other mods.")
	public Handlers handlers = new Handlers();

	public static final class General {
		@Comment("""
				Turns catching up off. The clock stands still while this is false, so that time is never caught up
				later; time a chunk had already spent away before it was switched off is kept and caught up once it is
				back on. Server downtime is not counted for a start with this false.""")
		public boolean enabled = true;
		@Comment("""
				Also count the time the server itself was switched off (real time between a shutdown and the next start).
				When false, only time the server spent running counts.""")
		public boolean useRealTime = false;
		@Comment("""
				The most time anything catches up on in one go, in hours. Every system below also has a cap of its own;
				the smaller of the two applies. 24 hours = 1728000 ticks = 72 in-game days.""")
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
		@Comment("""
				Count time from before Elapsed was installed. When false (the default) a world that gets Elapsed only
				starts counting from the moment it was installed, so old farms do not jump forward on the first load.""")
		public boolean countTimeBeforeInstall = false;
		@Comment("Write a line to the log for every chunk and animal that catches up (noisy; for testing).")
		public boolean debugLogging = false;
	}

	public static final class Dimensions {
		@Comment("""
				"whitelist": only the dimensions listed catch up. "blacklist": every dimension except those listed does.
				A chunk that loads in a dimension that does not catch up starts counting afresh: the time it was away is
				dropped, not kept for later. The same goes for things whose own switch below is off.""")
		public String mode = "whitelist";
		public List<String> list = new ArrayList<>(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
	}

	public static final class Performance {
		@Comment("Most chunks caught up per server tick.")
		@Range(min = 1, max = 1024)
		public int chunksPerTick = 8;
		@Comment("""
				Rough limit on the work done per tick (one block, furnace or animal caught up is one operation, a scanned
				16x16x16 section 16, copper 4). It decides whether another chunk is started: a chunk is always finished in
				the tick it was started in, so one very dense chunk can go well over it. Animals have their own limit.""")
		@Range(min = 16, max = 1000000)
		public int maxOperationsPerTick = 2048;
		@Comment("Most animals caught up per server tick.")
		@Range(min = 1, max = 4096)
		public int entitiesPerTick = 64;
	}

	public static final class Crops {
		public boolean enabled = true;
		@Comment("Catch-up cap for plants, in hours.")
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
		@Comment("Multiplies how fast plants grow while unloaded (1.0 = as fast as when loaded).")
		@Range(min = 0, max = 100)
		public double growthMultiplier = 1.0;
		@Comment("Sugar cane, cactus, bamboo, kelp and vines grow taller (placing new blocks above or below themselves).")
		public boolean tallPlants = true;
		@Comment("Fully grown melon and pumpkin stems may grow their fruit next to them.")
		public boolean stemFruit = true;
		@Comment("Cactus may grow a cactus flower, as it does when loaded.")
		public boolean cactusFlowers = true;
	}

	public static final class Saplings {
		public boolean enabled = true;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
	}

	public static final class Copper {
		public boolean enabled = true;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
		@Comment("Multiplies how fast copper oxidises while unloaded.")
		@Range(min = 0, max = 100)
		public double oxidationMultiplier = 1.0;
	}

	public static final class Furnaces {
		public boolean furnaces = true;
		public boolean smokers = true;
		public boolean blastFurnaces = true;
		@Comment("""
				Furnace-like blocks from other mods built on the vanilla furnace. Off by default: such a block may cook by
				rules of its own that Elapsed cannot know about. Only switch it on for blocks known to work like a furnace.""")
		public boolean otherFurnaces = false;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 12.0;
	}

	public static final class Brewing {
		public boolean enabled = true;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 12.0;
	}

	public static final class Campfires {
		public boolean enabled = true;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 12.0;
		@Comment("""
				Food that finished cooking more than 5 minutes before the chunk loaded would have despawned on the ground.
				When true it is gone, as it would have been; when false (the default) it is dropped anyway. Food is always
				dropped when there is a hopper under the campfire to catch it.""")
		public boolean expiredFoodDespawns = false;
	}

	public static final class Animals {
		@Comment("Babies keep growing up.")
		public boolean aging = true;
		@Comment("Breeding cooldowns and love mode run out.")
		public boolean breedingCooldowns = true;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
		@Comment("Mobs that never catch up, by id (\"minecraft:villager\").")
		public List<String> excluded = new ArrayList<>();
	}

	public static final class Chickens {
		public boolean eggTimers = true;
		@Comment("Most eggs one chicken lays while catching up. 0 only runs the timer down.")
		@Range(min = 0, max = 64)
		public int maxEggsPerCatchup = 2;
		@Range(min = 0.01, max = 8760)
		public double maxCatchupHours = 24.0;
	}

	public static final class Seasonfall {
		public boolean integrationEnabled = true;
	}

	public static final class AgeBlock {
		public String property = "age";
		@Range(min = 0, max = 1)
		public double growthChance = 0.1;
		@Range(min = 0, max = 15)
		public int minLight = 9;
	}

	public static final class Handlers {
		@Comment("Handler ids to switch off, for example \"elapsed:copper\" or a handler added by another mod.")
		public List<String> disabled = new ArrayList<>();
	}

	// ---- Loading

	public static ElapsedConfig get() {
		return instance;
	}

	public static void load(Path configDir) {
		file = configDir.resolve(FILE_NAME);
		reload();
	}

	/**
	 * Reads the file again. Returns the problems found, if any, so a command can show them. A file that cannot be read
	 * at all is left untouched: at start-up the defaults are used until it is fixed, and on a reload while running the
	 * settings already in use are kept (so a typo can never quietly switch catching up back on).
	 */
	public static List<String> reload() {
		List<String> problems = new ArrayList<>();
		ElapsedConfig config = new ElapsedConfig();
		if (file != null && Files.isRegularFile(file)) {
			try {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				problems.addAll(ConfigBinder.read(Toml.parse(text), config));
			} catch (IOException | Toml.ParseException | RuntimeException e) {
				boolean running = loaded;
				problems.add("Could not read " + FILE_NAME + " (" + e.getMessage() + "); "
						+ (running ? "keeping the settings already in use" : "using the defaults") + " until it is fixed");
				for (String problem : problems) {
					Elapsed.LOGGER.warn("[config] {}", problem);
				}
				if (!running) {
					instance = new ElapsedConfig();
					loaded = true;
				}
				return problems;
			}
		}
		config.validate(problems);
		for (String problem : problems) {
			Elapsed.LOGGER.warn("[config] {}", problem);
		}
		instance = config;
		loaded = true;
		if (file != null) {
			save(config);
		}
		return problems;
	}

	private static void save(ElapsedConfig config) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(FILE_NAME + ".tmp");
			Files.writeString(temp, ConfigBinder.write(config, HEADER), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Elapsed.LOGGER.error("Could not write {}", file, e);
		}
	}

	private void validate(List<String> problems) {
		String mode = this.dimensions.mode == null ? "" : this.dimensions.mode.toLowerCase(Locale.ROOT);
		if (!mode.equals("whitelist") && !mode.equals("blacklist")) {
			problems.add("dimensions.mode must be \"whitelist\" or \"blacklist\"; using \"whitelist\"");
			mode = "whitelist";
		}
		this.dimensions.mode = mode;
		if (this.dimensions.list == null) {
			this.dimensions.list = new ArrayList<>();
		}
		if (this.ageBasedBlocks == null) {
			this.ageBasedBlocks = new LinkedHashMap<>();
		}
		if (this.handlers.disabled == null) {
			this.handlers.disabled = new ArrayList<>();
		}
		if (this.animals.excluded == null) {
			this.animals.excluded = new ArrayList<>();
		}
	}

	// ---- Queries

	public long globalCapTicks() {
		return hoursToTicks(this.general.maxCatchupHours);
	}

	public boolean isDimensionEnabled(String dimension) {
		boolean listed = this.dimensions.list.contains(dimension);
		return this.dimensions.mode.equals("whitelist") == listed;
	}

	public static long hoursToTicks(double hours) {
		return Math.max(1L, Math.round(hours * TICKS_PER_HOUR));
	}
}
