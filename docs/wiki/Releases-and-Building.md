# Releases and Building

RustBeds uses Maven, Java 21, and GitHub Actions for release builds.

## Build locally

```bash
mvn -B -ntp clean package
```

The plugin jar is created at:

```text
target/RustBeds-<version>.jar
```

The Maven project version is defined in `pom.xml`.

## Versioning

RustBeds follows Semantic Versioning starting at `1.0.0`.

Release notes are tracked in `CHANGELOG.md` using Keep a Changelog formatting.

## GitHub release workflow

The repository includes a `Release` GitHub Actions workflow.

The workflow can publish when:

- A version bump lands on `main`
- A `v*.*.*` tag is pushed
- A manual workflow dispatch provides a release tag or version

For normal releases, bump the Maven `<version>` in `pom.xml`, update `CHANGELOG.md`, and push to `main`. The workflow compares the previous `pom.xml` version to the current one and skips when no version bump is detected.

## Release artifact

Published releases upload:

```text
RustBeds <version>.jar
```

The release notes are extracted from the matching `CHANGELOG.md` section when available.
