# PokeZoid – Project Viewpoint Projection

An unofficial **client-side compatibility mod** for **PokeZoid (CreatureViewerPrototype)** and **Project Viewpoint** on **Project Zomboid Build 42.21**.

The bridge displays PokeZoid's Pokémon and scan pickups in Viewpoint's first and third person cameras, using the original PokeZoid textures.

## Features

- Pokémon and PokeZoid scan-item billboards in first- and third-person Viewpoint modes.
- Native Java rendering with depth testing, so scene geometry can occlude sprites.
- Camera-aware projection and distance-based sizing.
- Mouse interaction aligned with projected sprites.
- Client-side multiplayer support, including Lua reattachment when switching sessions.

This mod **does not** generate Pokémon, change PokeZoid's authoritative data, or replace PokeZoid's multiplayer synchronization. PokeZoid must provide the underlying sightings.

## Requirements

**To play:**

- Project Zomboid **Build 42.21.0**.
- [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)
- [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528)
- [PokeZoid / `CreatureViewerPrototype`](https://steamcommunity.com/sharedfiles/filedetails/?id=3810664743)

**To build:**

- **JDK 25**.
- **Gradle 9.1 or newer**, with access to a JDK 25 toolchain.
- Local dependency JARs under `libs/`: `projectzomboid.jar`, `Viewpoint.jar`, and `ZombieBuddy.jar`.

The dependencies are compile-time inputs only (`compileOnly`); the other mods still need to be installed in the game.

## Build with Gradle

1. Place the required compile-time JARs in `libs/`, using the filenames listed above.
2. From the repository root, run:

   ```bash
   gradle build
   ```

   Alternatively, build only the assembled mod or distribution:

   ```bash
   gradle assembleMod
   gradle packageMod
   ```

### Version metadata

`modVersion` in `gradle.properties` controls the Gradle project version and the distribution ZIP filename. `${modId}`, `${modName}`, and `${modPackage}` in `42/mod.info` are expanded by the current `assembleMod` task.

## Install

1. Run `gradle assembleMod` (or `gradle build`).
2. Copy the generated `build/mod/PokeZoid - Viewpoint Projection/` folder into your local Project Zomboid mods directory:
   - **Windows:** `%USERPROFILE%\Zomboid\mods\`
   - **Linux:** `~/Zomboid/mods/`
3. Make sure that ZombieBuddy, Project Viewpoint, PokeZoid, and this compatibility mod are enabled or otherwise loaded appropriately.
4. Start or restart the game. Java JAR changes require a **full game restart**.

The Java JAR is installed by the Gradle task at `42/media/java/PokeZoidViewpointProjection.jar` inside the assembled mod, consistent with the `javaJarFile` setting in `mod.info`. Do not install a second copy of the JAR elsewhere in the same mod.

### Multiplayer

The projection bridge runs on the **client**; the PokeZoid server-side components remain responsible for supplying shared sightings. The bridge includes a client Lua reattachment mechanism for servers that do not list the compatibility mod. Each player using Viewpoint projection should install the compatibility bridge locally and authorize its Java mod through ZombieBuddy.

If nothing renders on a server, first verify that PokeZoid's entities appear in isometric view, then examine the client `console.txt` for `[PokeZoidViewpoint]` heartbeat/submission logs and PokeZoid's shared-sighting synchronization messages.

## Scope

The bridge integrates with the internals of Project Viewpoint and Project Zomboid Build 42. Changes to the game or Viewpoint's rendering implementation may require corresponding updates. This is a compatibility layer, not a replacement for either original mod.

