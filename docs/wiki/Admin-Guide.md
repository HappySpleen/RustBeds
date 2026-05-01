# Admin Guide

RustBeds keeps player-facing management simple, but it gives staff tools for support work.

## Admin command

Run:

```text
/beds admin
```

Requires:

```text
rustbeds.admin
```

The admin menu lets staff browse players with saved respawn points and then manage a selected player's points.

## Admin actions

From the admin menus, staff can:

- Rename a player's saved respawn point
- Remove a saved point
- Teleport themselves to a saved point
- Teleport another online player to a saved point
- Give a saved point to an online or known offline player

Admin give behavior follows `exclusive-bed`:

- `exclusive-bed: true` transfers ownership away from the original owner.
- `exclusive-bed: false` copies the point to the receiver.

The admin give action does not add `Shared By` provenance. It is treated as staff-managed ownership, not player sharing.

## Reloading settings

Run:

```text
/beds reload
```

This reloads config, language messages, saved respawn-point storage, and respawn-anchor storage.

The command can be used by console or by a sender with `rustbeds.admin`.

## Backups

Back up the full plugin folder before updates or major config changes:

```text
plugins/RustBeds/
```

The important runtime file is:

```text
plugins/RustBeds/respawn-points.db
```

This database stores saved points, pending offline notifications, migration flags, and respawn-anchor bindings.

## Operational notes

- World filtering is controlled by `allowlist` and `denylist`.
- `link-worlds: false` limits menus to saved points from the current respawn world.
- `max-beds` is hard-capped at 53.
- `rustbeds.maxcount.<num>` can raise or lower an individual player's limit, up to 53.
- Destroyed beds and anchors are removed automatically. Offline owners receive queued messages on their next login.
- Safe-location validation blocks obstructed saved respawn teleports until a nearby safe space is available.
