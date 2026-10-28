# World Learns

A Minecraft Fabric mod by **RRM Network**. Mobs learn from player actions, while villages and monster settlements grow, form alliances and go to war.

> **Beta — back up your entire world before installing, updating or removing the mod.** Construction changes real blocks. Bugs and balance changes are possible.

## Features

- Persistent world and regional learning based on combat, equipment, building and interactions.
- New combat, movement, construction, defensive and humorous behaviors for supported vanilla mobs.
- Growing villages and zombie, skeleton and creeper settlements, with leaders, trade, raids and relationships.
- Villager gossip, player reputation and crafting questions for defensive skills.
- Mob Knowledge (**K**), a world journal, settlement browser, settings, advancements and an in-game wiki.
- Separate interface language selection: English, Russian, Ukrainian, German, French and Spanish.
- Optional AI villager dialogue, disabled by default; built-in replies need no external service.

## Versions and requirements

Current mod version: **1.0.0-beta**, with a Minecraft version suffix. Internal mod ID: `mobslearn`.

| Minecraft | Java | Fabric Loader minimum | Fabric API minimum | Source |
| --- | --- | --- | --- | --- |
| 26.3 | 25 | 0.19.5 | 0.162.0 | [`source`](source/) |
| 1.21.4 | 21 | 0.16.9 | 0.115.0 | [`versions/source-1.21.4`](versions/source-1.21.4/) |
| 1.21.1 | 21 | 0.16.0 | 0.100.0 | [`versions/source-1.21.1`](versions/source-1.21.1/) |
| 1.20.1 | 17 | 0.14.21 | 0.87.0 | [`versions/source-1.20.1`](versions/source-1.20.1/) |

These are declared minimums from each build's metadata, not a claim that every possible dependency combination has been tested. Use a Fabric API release for the exact Minecraft version. Forge and NeoForge builds are not provided.

## Install and play

1. Back up the complete world folder.
2. Install the matching Fabric Loader and Fabric API.
3. Put the World Learns JAR for your Minecraft version in `mods`.
4. Open a world, press **K**, and review the wiki and settings.

For the complete multiplayer experience, install matching versions on the server and clients. Gameplay runs on the server; the client provides the interface and notifications. Dedicated-server and mixed-client behavior still needs runtime validation.

World owners and operators can adjust learning, difficulty, combat and humorous skills, and simulation speed. Building and digging obey `mobGriefing`. Unknown custom mob types do not automatically receive skills.

The name changed from Mobs Learn to World Learns; the internal ID remains `mobslearn`. This does not guarantee that every older save can be migrated safely: keep a backup.

## Operator commands

Commands require operator permissions or cheats in single-player.

| Command | Purpose |
| --- | --- |
| `/mobslearn info` | Show world learning information. |
| `/mobslearn set <skill> <level>` | Set a skill level from 0 to 3. |
| `/mobslearn maxall` | Unlock skills for experimentation. |
| `/mobslearn reset` | Clear learned points, regional knowledge and the journal. |
| `/mobslearn speed <multiplier>` | Adjust learning speed. |
| `/mobslearn timewarp <multiplier>` | Adjust simulation speed. |
| `/mobslearn settlement list` | List settlements. |
| `/mobslearn settlement zombie` | Create a zombie settlement; `village`, `skeleton` and `creeper` are also available. |

`reset` does **not** undo terrain changes or remove settlements. Use a world backup to restore the previous world state.

## Optional AI dialogue and privacy

The ordinary learning system uses game logic and does not require an AI service. Built-in villager replies work without an account or key.

AI dialogue is disabled by default. A world owner or operator can configure an OpenAI-compatible endpoint, including a local Ollama instance. The default remote preset is Groq; OpenRouter and Ollama presets are also available. When enabled, the mod sends the addressed message, up to eight previous conversation messages, player and villager display names, the villager's profession and attitude, and a dialogue prompt to that endpoint. Names and messages are not anonymized. Remote providers may charge for requests and process the data under their own terms. Review the selected provider's privacy and data-processing terms, and inform players before enabling this on a server.

Settings, including any API key, are stored in plaintext in `config/mobslearn-ai.json`. Keep this file private. Configure credentials through the local file instead of sharing them in command histories, logs or screenshots. `/mobslearn ai off` disables the integration; `/mobslearn ai` shows its status.

## Build the main version on Windows

The main source targets Minecraft 26.3 and uses the existing PowerShell build script, rather than Gradle.

```powershell
Set-Location source
.\build.ps1 -JavaHome 'C:\Java\jdk-25'
```

If `JAVA_HOME` or `javac.exe` on `PATH` already points to the correct JDK, omit `-JavaHome`. The script verifies downloaded dependencies using the checksums pinned in `tools/build-dependencies.json`. After dependencies are cached, add `-Offline` to build without downloading them again.

Output: `source/build/libs/mobslearn-<version>.jar`. Distribution filenames use `worldlearns-<version>.jar`; the metadata identifies the mod in either case. Dependencies are not bundled in the mod JAR.

Older-version source snapshots and the original porting tools are included. The shell build/port scripts depend on their original development environment and local dependency archives, which are excluded from this repository; they are not portable build instructions for a fresh clone.

## Repository contents

- `source/src/` — main Java source and resources for Minecraft 26.3.
- `source/tools/` — existing language, icon-generation and porting tools.
- `versions/source-*/src/` — source snapshots for older Minecraft versions.

Build outputs, Minecraft and dependency JARs, caches, backups, runtime configuration, logs and worlds are excluded. This repository is private.

## Validation and bug reports

Runtime compatibility, dedicated-server behavior and save migration still need in-game testing.

Report the game/mod/Loader/API versions, steps to reproduce, installed mods and relevant logs. Remove keys, chat content and other private data before sharing.

## License and third-party material

Original contributions are **All Rights Reserved**, matching the existing mod metadata. See [`LICENSE`](LICENSE) for the limited permission to run official releases and reference official downloads in modpacks. Repository access does not grant a right to publish forks or reuse the code.

Minecraft-derived graphics and external dependencies are excluded from the original-contribution copyright claim. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md); public distribution still requires the appropriate rights for all included material.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
