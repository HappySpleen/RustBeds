# Migration from MultipleBedSpawn

RustBeds is the renamed and modernized continuation of `MultipleBedSpawn`.

## What changed

- The plugin name is now `RustBeds`.
- The Java namespace moved to `me.happy.rustbeds`.
- The data folder is now `plugins/RustBeds`.
- Player workflows are consolidated under `/beds`.
- Saved beds and respawn anchors are stored together as saved respawn points.
- Runtime storage uses `respawn-points.db`.
- Release versions follow Semantic Versioning starting at `1.0.0`.

## Before upgrading

1. Stop the server.
2. Back up `plugins/MultipleBedSpawn/`.
3. Back up any existing `plugins/RustBeds/` folder if one already exists.
4. Install the RustBeds jar.
5. Start the server and watch the console for migration messages.

## Data migration

On startup, RustBeds looks for a legacy `plugins/MultipleBedSpawn` folder. If the RustBeds data folder does not already contain matching files, it moves known legacy files into `plugins/RustBeds`:

- `config.yml`
- `respawn-points.db`
- `bed-ownership.yml`
- `respawn-anchor-registry.yml`

RustBeds also imports remaining legacy player persistent-data-container bed data when players are known to the server.

Legacy YAML ownership and anchor registry data are migrated into the SQLite database and marked complete in database metadata.

## Config migration

RustBeds rewrites `config.yml` from the bundled template while preserving existing configured values and custom unknown values where possible.

Removed legacy config paths are not carried forward:

- `disable-sleeping`
- `remove-beds-gui`

## Permission migration

Legacy permission aliases still work:

- `multiplebedspawn.admin`
- `multiplebedspawn.skipcooldown`
- `multiplebedspawn.maxcount.<num>`

New setups should use the `rustbeds.*` permissions listed on [Commands and Permissions](Commands-and-Permissions).

## Command migration

Older standalone commands were replaced by the `/beds` menu flow:

- `/renamebed`
- `/removebed`
- `/sharebed`
- `/respawnbed`

Players should use `/beds`. Staff should use `/beds admin`.
