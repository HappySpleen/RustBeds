# Player Guide

RustBeds lets players save multiple respawn points and choose which one to use when they die.

## Saving a bed

Enter a bed normally. If the world is enabled and the bed can set spawn, RustBeds saves it as a respawn point instead of relying on the vanilla single-bed spawn.

The first saved point becomes the player's primary point automatically. Later points can be made primary from the menu.

## Saving a respawn anchor

If `respawn-anchors-enabled` is true, right-clicking a respawn anchor in an enabled world can save it as a respawn point.

Respawn anchors keep their vanilla charge behavior:

- Charges are shown in the respawn menu.
- A saved anchor with no charges cannot be used.
- A charge is consumed when the anchor is used for a RustBeds respawn.
- Destroyed anchors are removed from saved lists.

## Using `/beds`

Run:

```text
/beds
```

The menu shows saved respawn points the player can manage. From there, players can:

- Rename a saved point
- Set a primary point
- Remove a point
- Share a point with another online player, if `bed-sharing` is enabled

If a point is missing, obstructed, disabled, depleted, or on cooldown, RustBeds shows that state in the menu instead of silently failing.

## The death respawn menu

When a player dies in an enabled world, RustBeds opens a respawn menu after the configured delay.

The menu lets the player:

- Left-click a valid saved point to respawn there
- Use the normal spawn option
- Close the menu and fall back to the default respawn path

While the menu is open, RustBeds temporarily protects the player and darkens the background view. If `spawn-on-sky` is enabled in the overworld, the player is moved high above the normal respawn point while choosing.

## Primary respawn points

Each player has at most one primary point. The primary point matters when the respawn menu times out: RustBeds tries the primary point first, then falls back to the normal respawn path.

If the primary point is removed, RustBeds assigns another saved point when possible.

## Sharing behavior

Sharing is controlled by `bed-sharing` and `exclusive-bed`.

When `exclusive-bed: true`, sharing transfers the point to the receiver. When `exclusive-bed: false`, the receiver gets a copy and the original owner keeps theirs.

Shared points show `Shared By` information in the menu.
