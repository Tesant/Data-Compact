# Datapack Compat — Minecraft 1.21.11

A small Fabric 1.21.11 diagnostic mod for finding common legacy datapack patterns after moving a 1.21/1.21.1 world to 1.21.11.

## What it does

Adds:

- `/datapackcompat scan` — scans every world's `datapacks` directory and reports likely legacy patterns in `latest.log`.
- `/datapackcompat version` — prints the installed diagnostic version.

This is intentionally diagnostic rather than an automatic rewriter. Some patterns are valid in particular contexts, so blindly replacing them can corrupt a datapack.

## Build

Use JDK 21 and run:

```text
./gradlew build
```

The resulting jar is in `build/libs/`.

## Notes for porting

Minecraft 1.21.11 has data pack version 94.1 and renamed game rules to namespaced snake-case IDs. Item stack data also uses components rather than the old free-form item `tag` layout. See Mojang's 1.21.11 technical changes and the 24w09a item-component technical announcement.

Recommended workflow:

1. Back up the world.
2. Load it on 1.21.11.
3. Reproduce the bug.
4. Run `/datapackcompat scan`.
5. Send `latest.log` plus this source tree to your coding assistant.
6. Have it patch only confirmed cases, then test again.
