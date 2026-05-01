# RustBeds
RustBeds is a Paper plugin that lets players save beds and respawn anchors, then choose which saved respawn point they want to use. This project was previously published as `MultipleBedSpawn`.

[Legacy Spigot page](https://www.spigotmc.org/resources/multiple-bed-spawn.107057)

[Legacy Hangar page](https://hangar.papermc.io/GabiJ/MultipleBedSpawn)

## How it works

When a player dies, RustBeds opens a respawn menu if that player has at least one saved respawn point. Players can also run `/beds` at any time to manage their saved points, set a primary point, share or transfer points, or remove them. Pending share and transfer requests can be opened directly with `/beds requests`.

Players register a bed or respawn anchor by interacting with it in game, and the plugin stores those points in `respawn-points.db`.

![image](https://user-images.githubusercontent.com/69057368/210019366-3a981d52-79a2-4bfd-9217-0aac37918243.png)

## Features

- Unified `/beds` workflow for player management, admin browsing, and config reloads
- Saved beds and respawn anchors with primary-point selection
- Safe respawn checks that block obstructed saved points
- Cooldowns, sharing or transfer flows, exclusive ownership, and offline destroyed-point notifications
- Multiverse-compatible teleports with a vanilla fallback
- Config-driven world filters, safe-location search, and respawn timing controls
- SQLite-backed persistence in `respawn-points.db`

## Configuration

The shipped [`config.yml`](src/main/resources/config.yml) documents every option inline. Key settings include:

- `lang` to select the bundled language file
- `max-beds` and `bed-cooldown` to control saved-point limits
- `allowlist` / `denylist` and `link-worlds` to control world visibility
- `spawn-on-sky`, `respawn-menu-open-delay-ticks`, and `respawn-menu-timeout-seconds` for respawn flow timing
- `safe-location-search` to tune obstruction search radius and required player space
- `exclusive-bed`, `bed-sharing`, and `respawn-anchors-enabled` for gameplay rules
- `command-on-spawn`, `run-command-as-player`, and `teleport-provider` for integration behavior

Bundled translations currently include `enUS`, `deDE`, `esES`, `frFR`, `ptBR`, `ruRU`, `svSE`, and `zhCH`.

## Permissions

- `rustbeds.skipcooldown` lets players bypass saved-point cooldowns
- `rustbeds.maxcount.{num}` lets players override the configured saved-point limit
- `rustbeds.admin` allows `/beds admin` and `/beds reload`

Legacy `multiplebedspawn.*` permission nodes still work so existing server setups can upgrade without rewriting permissions immediately.

## Versioning

RustBeds now follows Semantic Versioning starting at `1.0.0`, and release notes are tracked in [`CHANGELOG.md`](CHANGELOG.md) using Keep a Changelog.

## Contributing

To add or update a translation:

- Fork the repository
- Copy `src/main/resources/languages/enUS.yml`
- Rename it to the language you are translating
- Replace the strings inside the file
- Open a pull request

Bug reports and feature requests are welcome through the repository issue tracker.
