# OzAnarchy Towns

Town and land-claim plugin for Paper/Spigot `1.21.x` with chunk protection, ranks, town bank, spawn flow, and raid/takeover mechanics.

## Features

- Town lifecycle: create, rename, transfer mayor, abandon.
- Land claiming with adjacency checks and unclaim support.
- Member management: invite/add, accept/deny, remove, promote, demote, leave.
- Town spawn system with delayed teleport and movement cancellation.
- Spawn reminder + automatic cleanup for towns that never set a spawn.
- Town bank support (deposit, withdraw, balance) with scheduled upkeep.
- Chunk protection for block place/break/interactions and explosion rules.
- Raid/takeover integration with `OminousChestLock`.
- Chunk visualizer for own/enemy/wilderness claims.
- Optional PlaceholderAPI integration.
- GUI menus for towns, members, and bank.
- Optional town chat command (`/tm`) when enabled in config.

## Requirements

- Java `21`
- Paper/Spigot API `1.21.x`
- MySQL
- Required plugins:
  - `ozanarchy-economy`
  - `OminousChestLock`
- Optional plugin:
  - `PlaceholderAPI`

## Build

```bash
mvn clean package
```

Output jar:

- `target/ozanarchy-towns-1.0.jar`

## Installation

1. Build the plugin.
2. Copy `target/ozanarchy-towns-1.0.jar` to your server `plugins/` directory.
3. Start the server once to generate default config files.
4. Edit `plugins/ozanarchy-towns/config.yml` and set MySQL credentials:
   - `mysql.host`
   - `mysql.port`
   - `mysql.database`
   - `mysql.username`
   - `mysql.password`
5. Restart the server.

## Commands

### Town Commands (`/towns`)

Aliases: `/town`, `/oztowns`, `/towny`

- `/towns` or `/towns gui`
- `/towns help [1-2]` (alias: `commands`)
- `/towns create <name>`
- `/towns rename <newName>`
- `/towns abandon confirm`
- `/towns claim`
- `/towns unclaim`
- `/towns setspawn`
- `/towns spawn`
- `/towns add <player>` (alias: `invite`)
- `/towns accept`
- `/towns deny`
- `/towns remove <player>`
- `/towns promote <player>`
- `/towns demote <player>`
- `/towns setmayor <player>`
- `/towns leave`
- `/towns members`
- `/towns visualizer` (alias: `chunks`)

### Bank Commands (`/townbank`)

Aliases: `/tbank`, `/townsbank`

- `/townbank` or `/townbank gui`
- `/townbank deposit <amount>`
- `/townbank withdraw <amount>`
- `/townbank balance` (alias: `bal`)
- `/townbank help` (alias: `commands`)

### Admin Commands (`/townadmin`)

Alias: `/tadmin`

- `/townadmin help`
- `/townadmin reload`
- `/townadmin delete <town>`
- `/townadmin spawn <town>` (teleport to town spawn)
- `/townadmin setspawn <town>`
- `/townadmin removespawn <town>`
- `/townadmin add <town> <player>`
- `/townadmin remove <town> <player>`
- `/townadmin setmayor <town> <player>`

### Town Chat

- `/tm <message>` (registered when `townmessages: true` in `config.yml`)

## Permissions

### Player

- `oztowns.commands`
- `oztowns.commands.create`
- `oztowns.commands.rename`
- `oztowns.commands.setspawn`
- `oztowns.commands.spawn`
- `oztowns.commands.claim`
- `oztowns.commands.unclaim`
- `oztowns.commands.abandon`
- `oztowns.commands.add`
- `oztowns.commands.remove`
- `oztowns.commands.promote`
- `oztowns.commands.demote`
- `oztowns.commands.leave`
- `oztowns.commands.members`
- `oztowns.commands.transfer`
- `oztowns.commands.visualizer`
- `oztowns.commands.help` (checked by `/towns help` and `/towns commands`)
- `oztowns.commands.bank`
- `oztowns.commands.bank.deposit`
- `oztowns.commands.bank.withdraw`
- `oztowns.commands.bank.balance`
- `oztowns.commands.bank.help`

### Admin

- `oztowns.admin` (default: `op`)
- `oztowns.admin.protectionbypass` (default: `op`)

## Configuration Highlights

From `config.yml`:

- `townmessages`: enable/disable `/tm` town chat.
- `townusercolor`: name color in town chat.
- `spawn-delay`: `/towns spawn` delay (seconds).
- `spawn-reminder.max-age-minutes`: auto-delete threshold when no spawn is set.
- `spawn-reminder.reminder-interval-minutes`: reminder interval.
- `town-creation-command`: command run when spawn is set.
- `towns.createcost`, `towns.claimcost`: economy costs.
- `towns.addedmemberupkeep`, `towns.refundedmemberupkeep`: member upkeep tuning.
- `towns.addedclaimupkeep`, `towns.refundedclaimupkeep`: claim upkeep tuning.
- `towns.setspawntimer`: spawn set cooldown.
- `unclaimable-worlds`: worlds where claims are blocked.
- `visualizer.enabled`, `visualizer.duration`, `visualizer.own|enemy|wild`: chunk visualizer settings.
- `bossbar.town|wilderness|raid`: bossbar display styles.
- `blacklisted-names`: disallowed town names.

Other configurable files:

- `messages.yml`: plugin messages/help text.
- `gui.yml`: GUI layouts and icons.

## Notes

- MySQL tables are created automatically on startup.
- This project targets Paper/Spigot `1.21.x` APIs.
- `/towns help` and `/towns commands` are both supported and read from `messages.yml`.
- `/townbank help` and `/townbank commands` are both supported and read from `messages.yml`.
