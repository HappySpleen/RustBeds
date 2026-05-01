# Installation

<p align="center">
  <img src="https://raw.githubusercontent.com/HappySpleen/RustBeds/main/docs/branding/rustbeds-logo-primary.png" alt="RustBeds logo" width="700">
</p>

## Requirements

- Paper `1.21.11`
- Java `21`
- A normal Bukkit/Paper `plugins` folder

Multiverse-Core is optional. RustBeds only looks for it when `teleport-provider: "multiverse"` is configured.

## Install from a release

1. Download the latest `RustBeds <version>.jar` from the GitHub Releases page.
2. Stop the server.
3. Place the jar in `plugins/`.
4. Start the server.
5. Confirm that `plugins/RustBeds/config.yml` and `plugins/RustBeds/respawn-points.db` are created.
6. Review permissions and config before opening the server to players.

## Update an existing install

1. Stop the server.
2. Back up `plugins/RustBeds/`.
3. Replace the old RustBeds jar with the new jar.
4. Start the server.
5. Review the console for config migration or compatibility warnings.
6. Check the changelog for user-facing behavior changes.

RustBeds preserves comments in `config.yml` when it adds new defaults. Custom unknown values are also retained unless they are removed legacy options.

## Install from source

Builds require Java 21 and Maven:

```bash
mvn -B -ntp clean package
```

The jar is written to `target/RustBeds-<version>.jar`.

## Folder layout

After startup, the plugin stores runtime data in:

```text
plugins/
  RustBeds/
    config.yml
    respawn-points.db
```

`respawn-points.db` is the SQLite database for saved respawn points, pending offline messages, migration markers, and respawn-anchor location bindings.
