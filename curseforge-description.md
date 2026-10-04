# Elapsed

**Offline progression for Minecraft servers. Farms, furnaces and animals carry on while nobody is around.**

When a chunk unloads, everything in it stops: furnaces stop smelting, crops stop growing, baby animals stop growing up. Elapsed doesn't keep chunks loaded and doesn't simulate them. When a chunk (or an animal) loads again, it works out once how much time passed and what would have happened in that time, and applies the result.

It is **server-side only**: players join with an unmodified client, and nothing needs to be installed on their side.

## What catches up

- **Furnaces, smokers and blast furnaces**: fuel burns and items smelt, stopping when the fuel or input runs out or the output slot is full, exactly when the real furnace would have. The experience is stored in the furnace as usual.
- **Brewing stands**: brews finish, using up blaze powder and ingredients.
- **Campfires**: food finishes cooking and pops off, once.
- **Crops and plants**: wheat, carrots, potatoes, beetroot, torchflowers, melon and pumpkin stems (and their fruit), nether wart, cocoa, sweet berries, sugar cane, cactus (and its flower), bamboo, kelp, and weeping, twisting and cave vines. Growth follows the game's own growth chances, and only happens where the plant could grow right now (enough light, the right ground, room above).
- **Saplings**: move on to their second stage; the tree itself is grown by the game as usual.
- **Copper**: oxidises, following the game's rule that nearby younger copper slows it down. Waxed copper never changes.
- **Animals**: babies grow up, breeding cooldowns and love mode run out. Nothing is ever bred, and animals never move or fight because of Elapsed.
- **Chickens**: the egg timer keeps running, and missed eggs are laid (a handful at most).

Never: redstone, hoppers, fluids, mob AI, villagers, tree generation or chunk loading.

## Settings

Everything is in `config/elapsed.toml`:

- a master switch, and a switch per system
- the longest time anything catches up on, overall and per system (24 hours of play by default)
- optionally count the time the server itself was switched off
- which dimensions catch up
- how much catch-up work is done per tick, so a player teleporting into a large old base doesn't freeze the server

Operators can use `/elapsed status`, `/elapsed chunk`, `/elapsed reload` and `/elapsed debug`.

## Safety

- The time is saved together with the chunk's contents, so the two can never disagree, even after a crash or a rollback.
- Each stretch of time is caught up exactly once. Reloading a chunk over and over does not repeat anything.
- `/time set` and `/time add` don't count as time passing.

## Compatibility

- **Fabric**: requires Fabric API.
- **NeoForge**: no other mods needed.
- **Seasonfall**: crops unloaded through several seasons grow by the seasons they actually spent unloaded.
- Other mods can add their own systems through Elapsed's API.

Source code and issue tracker: [github.com/Romoslayer/Elapsed](https://github.com/Romoslayer/Elapsed)
