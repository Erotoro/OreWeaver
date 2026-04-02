# OreWeaver

[![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-green.svg)](https://papermc.io/)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-red.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

Lightweight ore vein mining plugin for Folia, Paper, and Spigot.

## Overview

OreWeaver lets players mine connected ore veins in one action instead of breaking every block manually.  
It is built for modern servers with native Folia support and also works on Paper and Spigot.

## Info

Author: Erotoro  
Version: 1.1.1  
Minecraft Version: 1.21+  
Dependencies: None

## Features

- Mines connected ore veins with a single break
- Native support for Folia, Paper, and Spigot
- Configurable activation mode: `SNEAK`, `ALWAYS`, or `TOGGLED`
- Cooldown, hunger cost, durability use, and block limits
- Multi-language messages: `en`, `ru`, `ua`
- bStats integration

## Supported Ores

- Coal
- Iron
- Gold
- Copper
- Lapis
- Redstone
- Emerald
- Diamond
- Nether Quartz
- Ancient Debris

Deepslate variants are supported automatically through alias groups.

## Commands

- `/oreweaver toggle`
- `/oreweaver info`
- `/oreweaver reload`

Aliases:

- `/ow`

## Permissions

- `oreweaver.use`
- `oreweaver.command`
- `oreweaver.command.toggle`
- `oreweaver.command.info`
- `oreweaver.command.reload`
- `oreweaver.bypass.maxblocks`
- `oreweaver.bypass.hunger`
- `oreweaver.bypass.cooldown`

## Configuration

Main options in `config.yml`:

- `activation-mode`
- `language`
- `max-blocks`
- `server-max-blocks`
- `cooldown-ticks`
- `use-durability`
- `hunger-per-block`
- `minimum-food-level`

## Notes

- OreWeaver is focused on ores only.
- Axes and tree vein mining are intentionally not included.

## bStats

[![bStats](https://bstats.org/signatures/bukkit/OreWeaver.svg)](https://bstats.org/plugin/bukkit/OreWeaver/30533)

## Support

- Discord: [Join Discord](https://discord.gg/FMhuu3meH2)
- Ko-fi: [Support me](https://ko-fi.com/erotoro)
- Bugs and suggestions: [GitHub Issues](https://github.com/Erotoro/OreWeaver/issues)
