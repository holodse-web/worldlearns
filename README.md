# World Learns

A Minecraft mod for Fabric and NeoForge by **RRM Network**. Mobs learn from your actions. Settlements grow, form alliances and go to war. Enemies remember you and can come after your home.

> **Back up your entire world before installing, updating or removing the mod.** Construction, raids and marches change real blocks.

## Download World Learns 1.2.0

Choose the row for your Minecraft version and download the JAR for your loader. Each Minecraft version links to its release notes.

| Minecraft | Java | Fabric | NeoForge |
| --- | --- | --- | --- |
| [26.3](https://github.com/holodse-web/worldlearns/releases/tag/v1.2.0%2B26.3) | 25+ | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B26.3/worldlearns-1.2.0%2B26.3.jar) | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B26.3/worldlearns-1.2.0%2B26.3-neoforge.jar) |
| [1.21.4](https://github.com/holodse-web/worldlearns/releases/tag/v1.2.0%2B1.21.4) | 21+ | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.21.4/worldlearns-1.2.0%2B1.21.4.jar) | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.21.4/worldlearns-1.2.0%2B1.21.4-neoforge.jar) |
| [1.21.1](https://github.com/holodse-web/worldlearns/releases/tag/v1.2.0%2B1.21.1) | 21+ | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.21.1/worldlearns-1.2.0%2B1.21.1.jar) | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.21.1/worldlearns-1.2.0%2B1.21.1-neoforge.jar) |
| [1.20.1](https://github.com/holodse-web/worldlearns/releases/tag/v1.2.0%2B1.20.1) | 17+ | [Download](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.20.1/worldlearns-1.2.0%2B1.20.1.jar) | [Download legacy build](https://github.com/holodse-web/worldlearns/releases/download/v1.2.0%2B1.20.1/worldlearns-1.2.0%2B1.20.1-neoforge.jar) |

See the [changelog](CHANGELOG.md#120) for more on what changed in 1.2.0.

## Features

- Persistent world and regional learning based on combat, equipment, building and interactions.
- New combat, movement, construction, defensive and humorous behaviors for supported vanilla mobs, including squids, fish, rabbits, cats, parrots, horses, wolves, bees, chickens, golems, phantoms, witches, slimes and pillagers.
- Growing villages and zombie, skeleton and creeper settlements, with leaders, trade, raids and relationships.
- Pillager outposts, witch covens, settlements in the Nether and Enderman cities in the End.
- World events: sworn enemies who remember you, raids on your home, a great march of united settlements, captives you can interrogate, mercenaries and village allies, frame-up banners and knowledge hearths.
- Bosses remember too: the Ender Dragon, Wither, Warden and Elder Guardian adapt to how they were beaten.
- Villager gossip, player reputation and crafting questions for defensive skills.
- Look at a nearby mob and speak in chat. Ask “Remember?” to hear its last exchange with you.
- Mob Knowledge (**K**), a world journal, settlement browser, sworn-enemies screen, settings, advancements and an in-game wiki.
- Separate interface language selection: English, Russian, Ukrainian, German, French and Spanish.
- Optional AI villager dialogue, disabled by default; built-in replies need no external service.

## Loader requirements

The mod ID is `mobslearn`.

| Minecraft | Fabric Loader minimum | Fabric API minimum | NeoForge minimum | Source |
| --- | --- | --- | --- | --- |
| 26.3 | 0.19.5 | 0.162.0 | 26.3.0.103 | [`source`](source/) |
| 1.21.4 | 0.16.9 | 0.115.0 | 21.4.161 | [`versions/source-1.21.4`](versions/source-1.21.4/) |
| 1.21.1 | 0.16.0 | 0.100.0 | 21.1.266 | [`versions/source-1.21.1`](versions/source-1.21.1/) |
| 1.20.1 | 0.14.21 | 0.87.0 | 47.1.106 (legacy) | [`versions/source-1.20.1`](versions/source-1.20.1/) |

Use a Fabric API release for your exact Minecraft version. The alternate build for Minecraft 1.20.1 targets [NeoForged's legacy 47.1.106 line](https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/).

## Install and play

1. Back up the complete world folder.
2. Install the matching NeoForge version, or Fabric Loader and Fabric API.
3. Put the World Learns JAR for your Minecraft version in `mods`.
4. Open a world, press **K**, and review the wiki and settings.

For multiplayer, install matching versions on the server and every client. Gameplay runs on the server; the client provides the interface and notifications. Clients without the mod have not been tested.

World owners and operators can adjust learning, difficulty, combat, humorous skills, simulation speed and settlement growth. The **Settlements** master switch turns every settlement off at once while learning keeps working; **Villager settlements** controls villages only. Sworn enemies, home raids, boss memory and the great march have their own switches on the **World events** tab. Building and digging obey `mobGriefing`. Unknown custom mob types do not automatically receive skills.

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
| `/mobslearn settlement zombie` | Found a settlement. Other types are `village`, `skeleton`, `creeper`, `pillager`, `witch`, `nether` and `enderman`. |
| `/mobslearn event march\|raid\|nemesis` | Start a great march, a raid on your home or a sworn enemy right away. |

`reset` does **not** undo terrain changes or remove settlements. Use a world backup to restore the previous world state.

## Optional AI dialogue and privacy

Built-in mob replies work without an account or key. The world stores recent exchanges for each mob and player in `mobslearn_dialogues.json`.

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

The older Fabric source snapshots and their porting scripts are included. These scripts use local Minecraft, mappings and Fabric API archives from the original build environment.

Build a NeoForge JAR with Gradle 9.x from the repository root. Use the Java version in the table above and have Python on `PATH`.

```powershell
gradle -p source/tools/neoforge -PminecraftVersion=1.21.1 build
```

The JAR is written to `source/tools/neoforge/build/<minecraftVersion>/libs/`.

## Repository contents

- `source/src/` — main Java source and resources for Minecraft 26.3.
- `source/tools/` — existing language, icon-generation and porting tools.
- `versions/source-*/src/` — source snapshots for older Minecraft versions.

Build outputs, Minecraft and dependency JARs, caches, backups, runtime configuration, logs and worlds are excluded.

## Validation and bug reports

Dedicated-server startup was smoke-tested during development. Compatibility with other mods, clients without the mod and migration of older saves still need testing.

Report the game/mod/Loader/API versions, steps to reproduce, installed mods and relevant logs. Remove keys, chat content and other private data before sharing.

## License and third-party material

Original contributions are **All Rights Reserved**, matching the existing mod metadata. See [`LICENSE`](LICENSE) for the limited permission to run official releases and reference official downloads in modpacks. Repository access does not grant a right to publish forks or reuse the code.

Minecraft-derived graphics and external dependencies are excluded from the original-contribution copyright claim. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md); public distribution still requires the appropriate rights for all included material.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
