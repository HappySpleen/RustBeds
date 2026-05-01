# Configuration

RustBeds ships with a comment-preserving `config.yml`. On startup, the plugin adds new defaults while keeping existing values and comments where possible.

Run `/beds reload` after editing most settings, or restart the server if another plugin is also changing respawn behavior.

## General

| Key | Default | Description |
| --- | --- | --- |
| `config-version` | `2` | Internal marker for the shipped config layout. |
| `lang` | `enUS` | Language file to load from the bundled `languages` folder. Falls back to `enUS`. |

## Limits and cooldowns

| Key | Default | Description |
| --- | --- | --- |
| `max-beds` | `15` | Maximum saved respawn points per player. Hard-capped at 53. |
| `bed-cooldown` | `120` | Seconds before the same saved point can be used again. Set to `0` to disable. |

Players with `rustbeds.skipcooldown` bypass the cooldown. Players with `rustbeds.maxcount.<num>` can override `max-beds`, up to 53.

## World filtering

| Key | Default | Description |
| --- | --- | --- |
| `denylist` | `[]` | Worlds where RustBeds should not register or open respawn menus. |
| `allowlist` | `[]` | If non-empty, only worlds in this list are enabled. |
| `link-worlds` | `true` | If true, players can see saved points across all saved worlds. If false, they only see points from the current respawn world. |

If both lists are used, a world must be in `allowlist` and not in `denylist`.

## Menu lore

| Key | Default | Description |
| --- | --- | --- |
| `disable-bed-world-desc` | `true` | Hides world names from saved-point lore. |
| `disable-bed-coords-desc` | `false` | Hides coordinates from saved-point lore. |

These are UI-only settings.

## Respawn timing

| Key | Default | Description |
| --- | --- | --- |
| `spawn-on-sky` | `true` | In the overworld, places the player high above their normal respawn point while the menu is open. |
| `respawn-menu-open-delay-ticks` | `2` | Delay before opening the death respawn menu. 20 ticks is 1 second. Negative values behave like 0. |
| `offline-respawn-point-destroyed-message-delay-ticks` | `140` | Delay before queued destroyed-point messages are sent after login. Negative values behave like 0. |
| `respawn-menu-timeout-seconds` | `30` | How long the death menu stays open before auto-respawning. Values are clamped to at least 1 tick. |

When the respawn menu times out, RustBeds tries the player's primary point first, then uses the normal respawn path.

## Gameplay rules

| Key | Default | Description |
| --- | --- | --- |
| `exclusive-bed` | `true` | If true, one physical bed or anchor can only belong to one player at a time. |
| `bed-sharing` | `true` | Enables the player sharing action in the manage menu. |
| `respawn-anchors-enabled` | `true` | Enables saved respawn anchors in the Nether. |

When `exclusive-bed` is true, player sharing and admin giving transfer a point. When false, they copy it.

## Integrations

| Key | Default | Description |
| --- | --- | --- |
| `command-on-spawn` | `""` | Optional command to run after RustBeds sends a player to the default respawn path. Do not include a leading slash. |
| `run-command-as-player` | `true` | Runs `command-on-spawn` as the player when true, or console when false. |
| `teleport-provider` | `vanilla` | `vanilla` uses Bukkit teleport. `multiverse` dispatches Multiverse-Core `mv tp` and falls back to vanilla if needed. |

Invalid teleport providers fall back to vanilla with a console warning.
