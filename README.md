# Elapsed

Offline progression for Minecraft servers. When a chunk unloads, everything in it stops. Elapsed does **not** keep
chunks loaded and does **not** simulate them: when a chunk (or an animal) loads again, it works out once how much
time passed and what the supported things in it would have done in that time, and applies the result.

> Reconcile elapsed time. Do not simulate absence.

100% server-side: vanilla clients connect without installing anything. Fabric, NeoForge and Forge, Minecraft 26.2 and 26.3.

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/elapsed) ·
[GitHub releases](https://github.com/Romoslayer/Elapsed/releases)

## What catches up

| System | What happens | Stops when |
|---|---|---|
| Furnace, smoker, blast furnace (modded furnaces on the vanilla class only with `otherFurnaces = true`) | Fuel burns, items smelt, XP is stored in the furnace as usual | Fuel or input runs out, output slot is full, no recipe |
| Brewing stand | Brews finish, blaze powder and ingredients are used | No fuel, no ingredient, nothing left to brew |
| Campfire, soul campfire | Food finishes and pops off, once | — (unlit fires slowly lose progress, as in vanilla) |
| Wheat, carrots, potatoes, beetroot, torchflowers | Age by the vanilla growth chance (farmland moisture, row layout) | Not enough light, can't survive, fully grown |
| Melon and pumpkin stems | Grow, and may grow their fruit | Same, plus room for the fruit |
| Nether wart, cocoa, sweet berries | Age by their vanilla chance | Same |
| Sugar cane, cactus (+ cactus flower), bamboo | Grow taller, block by block | 3 high / bamboo's own height / no room |
| Kelp, weeping, twisting and cave vines | Grow longer | Age 25 / no room |
| Saplings | Move to their second stage only; the tree is grown by the game afterwards | — |
| Copper | Oxidises, following vanilla's "younger copper nearby slows or blocks it" rule | Waxed copper never changes |
| Baby animals (any mob that grows up) | Grow up | Age-locked babies stay babies |
| Breeding cooldown, love mode | Run out | Nothing is ever bred |
| Chickens | Egg timer runs on; missed eggs are laid, capped (default 2) | Chicks only start once grown |

Never: redstone, hoppers, fluids, mob movement/AI/combat, villagers, tree generation, leaf decay, chunk loading.

### How growth is estimated

Random ticks reach a block as a Poisson process, so a plant's growth steps over a stretch of time are a Poisson process
too, with the vanilla per-tick growth chance. Elapsed draws the outcome directly — a handful of random numbers per plant —
instead of replaying ticks. Furnaces and brewing stands follow their real tick logic on a copy of their contents but
skip the stretches where only a timer counts, so a furnace that smelted 64 items costs a few hundred steps.

## Time

- Default: only time the **server was running** counts (`useRealTime = false`).
- `useRealTime = true` also counts real time the server was switched off. After a crash, running time the world had
  already saved is not counted again.
- While `general.enabled = false` the clock stands still: that time is never caught up. Time a chunk spent away before
  the switch-off is kept and caught up once it is switched back on.
- A dimension that does not catch up, or a system that is switched off, simply drops the time for what loads there.
- `/time set` and `/time add` do **not** create elapsed time (Elapsed uses game ticks, not the time of day).
- Everything is capped: a global `maxCatchupHours` (default 24 h of play = 1,728,000 ticks) and a cap per system.
- By default a world only starts counting from the moment Elapsed was installed (`countTimeBeforeInstall = false`).

## Performance

Nothing is done for unloaded chunks. When a chunk loads, its catch-up is queued and done a few chunks per tick
(`chunksPerTick`, `maxOperationsPerTick`) once the chunk is actually ticking. Chunk sections are only scanned when their
block palette contains something supported.

## Safety

- The "last saved at" timestamp is written into the chunk's own saved data at the same moment as its blocks, so the
  state on disk and the time it describes can never disagree — not after a crash, not after a rollback.
- Each stretch of time is caught up exactly once. A chunk that unloads before its catch-up ran carries the pending time
  back to disk with it; reloading over and over does not repeat anything.
- Handlers only ever produce what the captured inventory/state accounts for, and re-check the world before applying.
- A handler that throws (or a growth provider that throws or returns nonsense) is switched off and logged once instead
  of breaking the server; `/elapsed reload` gives it another chance.
- Catch-up only runs once all neighbouring chunks are loaded, so it never makes the game load or generate a chunk.

## Approximations

Catching up is an estimate of what vanilla would have done, not a replay. Where it is not exact:

- The world as it is now (light, neighbouring blocks, random_tick_speed) is assumed for the whole absence.
- Copper looks at its neighbours once and is caught up block by block in scan order, so in a large copper build the
  exact pattern of aged blocks can differ from an unloaded-and-replayed one (the overall rate follows vanilla).
- A sapling lit only by the sky is assumed to get enough light half of the time.
- Things placed in a chunk in the few ticks between it starting to tick and its catch-up running share in that catch-up.
- NeoForge and Forge brewing events are not fired for caught-up brews.

## Configuration

`config/elapsed.toml`, written with comments on first start. Main sections: `[general]`, `[dimensions]`
(whitelist/blacklist), `[performance]`, one section per system with its own switch and `maxCatchupHours`,
`[seasonfall]`, `[ageBasedBlocks."mod:block"]` for simple modded crops, and `[handlers] disabled = [...]` to switch any
handler off by id (`elapsed:crops`, `elapsed:tall_plants`, `elapsed:bamboo`, `elapsed:vines`, `elapsed:saplings`,
`elapsed:copper`, `elapsed:furnaces`, `elapsed:brewing`, `elapsed:campfires`, `elapsed:aging`, `elapsed:chicken_eggs`).

## Commands (operators)

- `/elapsed status` — mode, caps, queue, totals, slowest catch-up tick and last burst, handlers switched off after errors
- `/elapsed chunk` — pending catch-up for the chunk you stand in
- `/elapsed reload` — reload the config
- `/elapsed debug [true|false]` — log every catch-up to the server log

## For mod authors

```java
// Simplest: a crop that grows by an age property
ElapsedApi.registerAgeProperty(MY_CROP, MyCrop.AGE, 0.25, 9);

// Full control: capture -> calculate (pure) -> apply
ElapsedApi.registerBlockHandler(id, new BlockHandler<MyState>() { ... });
ElapsedApi.registerBlockEntityHandler(id, ...);
ElapsedApi.registerEntityHandler(id, ...);

// Seasons/climate: average growth speed over the time a plant was unloaded
ElapsedApi.registerGrowthRateProvider(id, (level, pos, state, elapsedTicks) -> 1.0);
```

Handlers get the elapsed time already cut to their cap. Registered handlers are tried before Elapsed's own. For
blocks and block entities the first handler that matches wins; for entities every matching handler runs (an animal can
have several timers), each looking at the entity before any of them changes it.

## Seasonfall

Crops unloaded across several seasons grow by the seasons they actually spent unloaded, not by the season it is when
they load again: Seasonfall works out the plant's average growth speed over the time it was away, through
`SeasonfallApi.averageCropGrowthMultiplier(ServerLevel, BlockPos, BlockState, long elapsedTicks)`. With an older
Seasonfall that lacks that method (or with `[seasonfall] integrationEnabled = false`) crops catch up at the normal rate. Seasonfall can instead register its own
`GrowthRateProvider`, in which case the built-in bridge stands down. Stormcell needs no integration.

## Building

```
gradlew build            # Minecraft 26.3
gradlew build -Pmc=26.2  # Minecraft 26.2
```

Jars land in `Fabric/build/libs`, `NeoForge/build/libs` and `Forge/build/libs`. `gradlew runVanillaClient -Pserver=localhost:25565` starts the official,
unmodded client and joins a test server, to see what players without the mod see. The few classes that differ between game versions live in
`Common/src/version/<mc>/java`.

## License

MIT. See [LICENSE](LICENSE).
