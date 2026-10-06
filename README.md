# MTREEDeathPenalty (Paper 1.21.11, Java 21)

Each death: -1 max heart (min 6), Weakness I + Mining Fatigue I (5 min), -10% XP.
Hearts come back over time (1 / 2 / 3 Minecraft days depending on consecutive deaths).
Nobody is ever banned, kicked, put in spectator or eliminated.

## Build
Requires JDK 21 and Maven.
    mvn clean package
Output: `target/MTREEDeathPenalty.jar`

## Install
1. Stop the server, drop the jar into `plugins/`, start the server.
2. Edit `plugins/MTREEDeathPenalty/config.yml`, then `/deathpenalty reload`.

## Commands (permission `deathpenalty.admin`)
- `/deathpenalty reload`
- `/deathpenalty status [player]`  (your own status: `deathpenalty.status`, default everyone)
- `/deathpenalty reset <player>`
- `/deathpenalty heal <player>`
- `/deathpenalty sethearts <player> <amount>`
Target players must be online. Alias: `/dp`.

## How it works
- Uses the vanilla `MAX_HEALTH` attribute only (20 HP -> 18 -> ... -> 12). No fake health, no damage simulation.
- Recovery uses real-time timestamps (1 MC day = `day-length-minutes`, default 20), stored per UUID in `data.yml`.
  Persists across logout/restart and keeps counting while offline. One one-shot task exists per online injured
  player; nothing runs per tick. After a heart recovers, the next one uses the first-death delay.
- Death handling is observe-only (MONITOR priority). Drops, XP drops, respawn location and death messages are untouched.
- XP penalty is taken a tick after respawn from whatever XP the player has *then*. On vanilla death XP is usually
  already gone/dropped (or held by Graves), so the penalty bites mainly when XP is kept (keepInventory/keepLevel).
  It never goes negative.
- Effects are re-applied (refreshed, not stacked) on every death.

## Compatibility
Graves, Essentials, LevelledMobs, HeadDrop, FightBot, OrbitalStrike, etc.: no hooks, no drop/loot/respawn/PvP changes,
no dependency on ProtocolLib or any other plugin. A plugin that forces a player's MAX_HEALTH itself would conflict
(none of your list is known to).
Players who never died are not touched. A player who disconnects while on the death screen misses that death's
effects/XP penalty (the heart loss is still saved).

## Hardcore hearts (important limitation)
The client draws hardcore hearts only when the server's login/respawn packet says the world is hardcore. That is a
per-world flag, not a per-player one; the Bukkit API cannot set it. Forcing it with ProtocolLib packet rewriting
would also give clients the hardcore respawn screen and break server stability/Via/Geyser handling, so this plugin
does NOT do that.

Closest reliable solution: a **server resource pack** that swaps the normal heart sprites for the hardcore ones.
1. From the 1.21.11 client jar copy `assets/minecraft/textures/gui/sprites/hud/heart/` into a pack folder.
2. Overwrite `full.png`, `half.png`, `full_blinking.png`, `half_blinking.png`, `container.png`, `container_blinking.png`
   with the matching `hardcore_*` / `container_hardcore*` images (also the poisoned/withered/frozen/absorbing variants if wanted).
3. Host the zip and set `resource-pack`, `resource-pack-sha1` (and optionally `require-resource-pack`) in server.properties.
HP stays vanilla, the HUD stays the real HUD.

- Java: works with the pack.
- Bedrock (Geyser/Floodgate): Bedrock has no hardcore hearts and Java pack textures do not restyle the Bedrock HUD.
  Bedrock players see normal hearts, with the correct number of them.
