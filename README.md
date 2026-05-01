<p align="center">
  <img src="docs/branding/rustbeds-banner.png" alt="RustBeds banner" width="855">
</p>

<p align="center">
  <strong>Saved beds and respawn anchors for Paper servers.</strong><br>
  Let players choose where they respawn while keeping setup simple for server staff.
</p>

<p align="center">
  <a href="https://github.com/HappySpleen/RustBeds/releases">Releases</a> |
  <a href="https://github.com/HappySpleen/RustBeds/wiki">Docs</a> |
  <a href="CHANGELOG.md">Changelog</a> |
  <a href="https://github.com/HappySpleen/RustBeds/wiki/Migration-from-MultipleBedSpawn">Migration guide</a>
</p>

# RustBeds

RustBeds is a Paper plugin that turns Minecraft's single saved bed into a full respawn-point system. Players can register beds and, when enabled, Nether respawn anchors, then choose which saved point to use after death.

The main player workflow lives in one `/beds` menu for managing, renaming, sharing, removing, and choosing primary respawn points. Staff get a matching admin menu for support work, including browsing saved points, teleporting players, granting points, and reloading plugin settings.

RustBeds was previously published as `MultipleBedSpawn`. Current releases use the new name, data folder, command flow, and `rustbeds.*` permission nodes while keeping legacy upgrade paths where they matter.

## Highlights

- Saved beds and respawn anchors with one primary point per player
- Death respawn menu with configurable delay, timeout, and default-spawn fallback
- Safe-location validation that blocks obstructed saved points before teleporting
- Cooldowns, per-player saved-point limits, world filters, linked-world visibility, sharing, and exclusive ownership
- Admin `/beds admin` menus for renaming, removing, teleporting to, and granting saved points
- SQLite persistence in `plugins/RustBeds/respawn-points.db`
- Optional Multiverse-Core teleport integration with vanilla teleport fallback
- Bundled language files for `enUS`, `deDE`, `esES`, `frFR`, `ptBR`, `ruRU`, `svSE`, and `zhCH`

## Requirements

- Paper `1.21.11`
- Java `21`
- Optional: Multiverse-Core, only when `teleport-provider: "multiverse"` is configured

## Installation

1. Download the latest `RustBeds <version>.jar` from [GitHub Releases](https://github.com/HappySpleen/RustBeds/releases).
2. Stop your server.
3. Place the jar in the server's `plugins` folder.
4. Start the server.
5. Review `plugins/RustBeds/config.yml`.
6. Give staff `rustbeds.admin` if they should use `/beds admin` or `/beds reload`.

See the full [Installation](https://github.com/HappySpleen/RustBeds/wiki/Installation) page for update notes, folder layout, and source-build details.

## Commands

| Command | Sender | Permission | Purpose |
| --- | --- | --- | --- |
| `/beds` | Player | None | Opens the player's saved respawn-point menu. |
| `/beds admin` | Player | `rustbeds.admin` | Opens the staff respawn-point browser. |
| `/beds reload` | Player or console | `rustbeds.admin` | Reloads config, language files, and storage handles. |

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `rustbeds.admin` | Operators | Allows `/beds admin` and `/beds reload`. |
| `rustbeds.skipcooldown` | False | Bypasses saved-point cooldowns. |
| `rustbeds.maxcount.<num>` | False | Overrides `max-beds` for a player, up to the plugin cap of 53. |

Legacy `multiplebedspawn.*` aliases still work for servers upgrading from MultipleBedSpawn. New setups should use the `rustbeds.*` nodes.

## Configuration

The shipped [`config.yml`](src/main/resources/config.yml) documents each option inline. The most important areas are:

- `max-beds` and `bed-cooldown` for player limits and reuse timing
- `allowlist`, `denylist`, and `link-worlds` for world visibility
- `spawn-on-sky`, `respawn-menu-open-delay-ticks`, and `respawn-menu-timeout-seconds` for the death-menu flow
- `exclusive-bed`, `bed-sharing`, and `respawn-anchors-enabled` for gameplay rules
- `command-on-spawn`, `run-command-as-player`, and `teleport-provider` for integration behavior

For server-owner explanations, see [Configuration](https://github.com/HappySpleen/RustBeds/wiki/Configuration) and [Respawn Flow and Safety](https://github.com/HappySpleen/RustBeds/wiki/Respawn-Flow-and-Safety).

## Documentation

| Page | Contents |
| --- | --- |
| [Player Guide](https://github.com/HappySpleen/RustBeds/wiki/Player-Guide) | Saving beds and anchors, using `/beds`, sharing, and primary points. |
| [Admin Guide](https://github.com/HappySpleen/RustBeds/wiki/Admin-Guide) | Staff menus, giving points, reload behavior, and operational notes. |
| [Commands and Permissions](https://github.com/HappySpleen/RustBeds/wiki/Commands-and-Permissions) | Command table, permissions, and legacy aliases. |
| [Languages](https://github.com/HappySpleen/RustBeds/wiki/Languages) | Bundled translations and how language selection works. |
| [Migration from MultipleBedSpawn](https://github.com/HappySpleen/RustBeds/wiki/Migration-from-MultipleBedSpawn) | Upgrade behavior for older installs. |
| [Releases and Building](https://github.com/HappySpleen/RustBeds/wiki/Releases-and-Building) | Maven builds, release artifacts, and versioning. |
| [Branding](https://github.com/HappySpleen/RustBeds/wiki/Branding) | Project logos, banners, icons, and usage notes. |

## Build From Source

Builds require Java 21 and Maven:

```bash
mvn -B -ntp clean package
```

The jar is written to:

```text
target/RustBeds-<version>.jar
```

Release notes are tracked in [`CHANGELOG.md`](CHANGELOG.md) using Keep a Changelog formatting, and RustBeds follows Semantic Versioning starting at `1.0.0`.

## Legacy Project Links

RustBeds began as MultipleBedSpawn. These pages are kept for historical reference:

- [Legacy Spigot page](https://www.spigotmc.org/resources/multiple-bed-spawn.107057)
- [Legacy Hangar page](https://hangar.papermc.io/GabiJ/MultipleBedSpawn)

## Contributing

Bug reports, feature requests, and translation updates are welcome through the repository issue tracker. For translations, copy `src/main/resources/languages/enUS.yml`, rename it for the target locale, update the strings, and open a pull request.
