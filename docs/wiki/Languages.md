# Languages

RustBeds loads player-facing text from bundled language files.

## Bundled languages

Set `lang` in `config.yml` to one of:

| Code | File |
| --- | --- |
| `enUS` | `languages/enUS.yml` |
| `deDE` | `languages/deDE.yml` |
| `esES` | `languages/esES.yml` |
| `frFR` | `languages/frFR.yml` |
| `ptBR` | `languages/ptBR.yml` |
| `ruRU` | `languages/ruRU.yml` |
| `svSE` | `languages/svSE.yml` |
| `zhCH` | `languages/zhCH.yml` |

If the selected language is missing or cannot be loaded, RustBeds falls back to `enUS`.

## Reload language changes

After editing `lang`, run:

```text
/beds reload
```

or restart the server.

## Updating translations

To add or update a translation in the repository:

1. Copy `src/main/resources/languages/enUS.yml`.
2. Rename the copy to the target language code.
3. Translate the values while keeping the same keys.
4. Keep placeholders like `{1}`, `{2}`, and `{3}` intact.
5. Open a pull request.

Translation files are packaged into the plugin jar at build time.
