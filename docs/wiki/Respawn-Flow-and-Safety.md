# Respawn Flow and Safety

This page explains what happens when RustBeds handles a respawn.

## Registration flow

Beds are saved when a player enters a bed and Paper reports that the interaction can set spawn. RustBeds cancels the vanilla spawn update so it can manage multiple saved points itself.

Respawn anchors are saved when all of these are true:

- `respawn-anchors-enabled` is true
- The player interacts with a respawn anchor in an enabled world
- Paper reports a respawn-anchor spawn update
- The player has room under `max-beds` or a `rustbeds.maxcount.<num>` override
- `exclusive-bed` does not block ownership

## Death respawn flow

When a player respawns after death, RustBeds skips handling if:

- The respawn world is The End
- The respawn world is the Nether and respawn anchors are disabled
- The respawn was already caused by a plugin teleport
- The world is disabled by `allowlist` or `denylist`

Otherwise, RustBeds stores the default respawn location and opens the respawn menu after `respawn-menu-open-delay-ticks`.

## Menu protection

While the death respawn menu is open, RustBeds temporarily:

- Makes the player invulnerable
- Restores their previous invisibility state after the menu closes
- Disables item pickup
- Sets walk speed to 0
- Applies darkness and blindness effects
- Allows flight when `spawn-on-sky` is active

When the player chooses a point, closes the menu, times out, or rejoins, RustBeds restores these temporary properties.

## Saved-point statuses

Saved points can appear with different states:

| Status | Meaning |
| --- | --- |
| Available | The saved point exists and can be used. |
| Cooldown | The point exists but the player must wait before using it again. |
| Missing | The bed or anchor no longer exists. |
| Obstructed | The saved point exists, but no safe nearby respawn space is available. |
| Depleted | A respawn anchor has no charges. |
| Disabled | Respawn anchors are disabled in config. |

Missing destroyed points are cleaned up automatically when RustBeds detects them.

## Safe-location validation

Before teleporting to a saved point, RustBeds checks for a safe, non-colliding respawn space.

The current search looks for space:

- Within 2 blocks horizontally of the saved point
- Within 0.375 blocks vertically of the preferred respawn location
- With at least 1.625 blocks of non-colliding vertical space

If no safe location is found, the point is marked obstructed and the player must pick another point or use the normal respawn path.

## Timeout behavior

If the player does not choose a point before `respawn-menu-timeout-seconds`, RustBeds:

1. Attempts the player's primary respawn point if it is usable.
2. Falls back to the normal respawn path.
3. Runs `command-on-spawn` if configured for the default path.

Cooldowns are applied after successful saved-point respawns unless the player has `rustbeds.skipcooldown`.
