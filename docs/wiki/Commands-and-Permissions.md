# Commands and Permissions

## Commands

| Command | Sender | Permission | Description |
| --- | --- | --- | --- |
| `/beds` | Player | None | Opens the player's saved respawn-point management menu. |
| `/beds admin` | Player | `rustbeds.admin` | Opens the admin saved-point browser. |
| `/beds reload` | Player or console | `rustbeds.admin` | Reloads config, language files, and storage handles. |

Only admin senders see `admin` and `reload` tab suggestions.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `rustbeds.admin` | Operators | Allows `/beds admin` and `/beds reload`. |
| `rustbeds.skipcooldown` | False | Bypasses saved-point cooldowns. |
| `rustbeds.maxcount.<num>` | False | Overrides `max-beds` for a player. Example: `rustbeds.maxcount.25`. |

`max-beds` and permission-based max counts are capped at 53.

## Legacy permission aliases

These legacy nodes still work for servers upgrading from MultipleBedSpawn:

| Legacy permission | Current equivalent |
| --- | --- |
| `multiplebedspawn.admin` | `rustbeds.admin` |
| `multiplebedspawn.skipcooldown` | `rustbeds.skipcooldown` |
| `multiplebedspawn.maxcount.<num>` | `rustbeds.maxcount.<num>` |

The current `rustbeds.*` nodes are preferred for new setups.

## Removed legacy commands

Older standalone command flows were removed in favor of `/beds`:

- `/renamebed`
- `/removebed`
- `/sharebed`
- `/respawnbed`

Use the `/beds` menu and `/beds admin` menu instead.
