# 🍪 OreoEssentials  
### The Most Advanced All-In-One Essentials Plugin for Paper 1.21+

**Homes • Warps • Chat • RTP • Kits • Moderation • Economy • GUIs • Portals • Cross-Server Teleports • Redis/Mongo/RabbitMQ Sync • Player Inventory Sync • EnderChest Sync • 200+ Commands**

Free & open-source plugin by **Oreo Studios**.

---

## ✨ Highlights

- 🧭 **Homes / Warps / Spawn** (with cross-server handoff)
- 🧙 **Kits** with cooldowns, permissions & GUI
- 🗺️ **Portals & JumpPads**
- 🎲 **/rtp** with rank-based radius & regions
- 💬 **Chat formatter** (hex gradient, RGB, PlaceholderAPI)
- 💸 **Economy** (Vault + internal DB backends)
- 🎒 **Cross-server inventory sync** (inventory/xp/health/hunger)
- 📦 **Cross-server EnderChest** with rank-based size
- 🧰 **Moderation tools** (ban/mute/kick/freeze/jail)
- 🧱 **OreoHolograms** (built-in hologram system)
- 🪄 **Alias editor** (rename commands in `aliases.yml`)
- 🍪 **DailyRewards & PlaytimeRewards**
- 🔁 **Same-server & cross-server trading**
- 🐰 **RabbitMQ / Redis / MongoDB** integration
- ⚡ **Async operations** & Redis caching for performance
- 🧩 **SmartInvs-based GUIs** for a modern UX

---

## 📦 Requirements

- **Server:** Paper / Spigot **1.21+**
- **Java:** **17+**
- (Optional) **MongoDB** for persistent storage
- (Optional) **Redis** for caching
- (Optional) **RabbitMQ** for cross-server messaging
- (Optional) **Vault** + any economy provider (for /balance, /pay, etc.)
- (Optional) **PlaceholderAPI** for placeholders in chat, holograms & GUIs

---

## 🚀 Installation (Single Server)

1. Download `OreoEssentials.jar`.
2. Drop it in your `/plugins` folder.
3. Start the server once to generate config files.
4. Edit configurations in `/plugins/OreoEssentials/`:
   - `settings.yml`
   - `database.yml`
   - `chat-format.yml`
   - `messages.yml`
   - etc.
5. Restart the server.

That’s it — you now have a full Essentials replacement with GUIs, holograms, rewards, and more.

---

## 🌍 Cross-Server Setup (Multi-Server Networks)

OreoEssentials is designed for **Velocity/Bungee + multiple Paper servers**.

### 1. Database

In `database.yml`:

```yaml
mongo:
  enabled: true
  host: "127.0.0.1"
  port: 27017
  database: "oreoessentials"
  username: ""
  password: ""

redis:
  enabled: true
  host: "127.0.0.1"
  port: 6379
  password: ""
```

MongoDB = central storage for homes, warps, data.

Redis = cache + fast sync signals (optional but recommended).

2. RabbitMQ (Cross-Server Messaging)

In rabbitmq.yml:
In rabbitmq.yml:

enabled: true
host: "127.0.0.1"
port: 5672
username: "guest"
password: "guest"
virtual-host: "/"
prefix: "oreo"


RabbitMQ is used for:

Cross-server /home, /warp, /spawn

Cross-server trades

Inventory & EnderChest sync signals

3. Cross-Server Feature Toggles

In settings.yml:

features:
  cross-server:
    homes: true
    warps: true
    spawn: true
    economy: true
    enderchest: true
    inventory-sync: true


Enable only what you actually need.

🧾 Configuration Files Overview
File	Description
settings.yml	Core toggles, features, cross-server options
database.yml	MongoDB / PostgreSQL / Redis configuration
rabbitmq.yml	Cross-server messaging configuration
chat-format.yml	Chat formats, gradients, channels, Discord relay
dailyrewards.yml	OreoDailyRewards configuration
playtime-rewards.yml	Playtime rewards & milestones
events.yml	Custom event triggers & actions
portals.yml	Portals & jump pads
aliases.yml	Alias editor (rename commands)
holograms.yml	OreoHolograms definitions
messages.yml	All messages (translatable & fully editable)
🧱 OreoHolograms System

Built-in hologram system:

Uses ArmorStands (no external plugin required)

Supports multi-line texts

Works with PlaceholderAPI

Async refreshing

Example (holograms.yml):

spawn-board:
  world: "world"
  x: 0.5
  y: 80.0
  z: 0.5
  lines:
    - "&b&lWelcome to &f&lYourServer"
    - "&7Online: &a%server_online%"
    - "&7Balance: &e%vault_eco_balance_fixed%"

🪄 Alias Editor

Rename or shorten ANY command in aliases.yml.

home:
  - "maison"
  - "hm"

spawn:
  - "hub"
  - "lobby"


Players can now run /maison or /hm instead of /home.

🍪 OreoDailyRewards

Daily login rewards with streaks, GUIs and flexible rewards:

Money (Vault or internal)

Items

Commands

Permissions

Example (dailyrewards.yml):

rewards:
  day-1:
    display-name: "&aJour 1"
    commands:
      - "eco give %player% 500"
  day-7:
    display-name: "&6Jour 7 (Streak!)"
    commands:
      - "eco give %player% 5000"
      - "lp user %player% parent add vip-temp"

⏱️ OreoPlaytimeRewards

Reward players for total playtime:

milestones:
  "3600":     # seconds => 1h
    commands:
      - "eco give %player% 1000"
  "21600":    # 6h
    commands:
      - "crate key give %player% playtime 1"


Includes:

Anti-AFK detection

Per-milestone messages

GUI integration (optional)

⚡ Interactive Commands (/ic, signs, entities)

Link commands to blocks, signs or entities with /ic:

/ic create warp_spawn
# Click a block, sign or entity
# When a player interacts => /warp spawn is executed


Use cases:

NPC menus

Warp signs

Quest entities

Custom GUI triggers

🧩 Events System (events.yml)

Create custom events with triggers & actions.

Example: region-based welcome event

spawn-welcome:
  trigger:
    type: "region-enter"
    region: "spawn"
  actions:
    - "message:&aBienvenue à &bSpawn&a !"
    - "sound:ENTITY_PLAYER_LEVELUP"
    - "title:&bSpawn:&7Profite de ton séjour"


Supported triggers (examples):

Region enter/leave

Join/quit

Kill entity/type/player

Block break/place

Command execute

Time of day, world, permission checks, etc.

Actions:

message:, broadcast:, command:, sound:, title:, actionbar:, etc.

💀 Death Messages & Death Events

Customize all death messages and trigger events on death:

death-messages:
  PLAYER:
    default:
      - "&c%player% &7a été éliminé."
  FALL:
    default:
      - "&c%player% &7a oublié son parachute."


You can also:

Run commands on death

Drop specific items

Block item drops in certain worlds

⚖️ Jail System

Use /jail to freeze players in a defined jail area:

Configurable jail region or location

Custom messages & titles

Integration with moderation logs (optional)

🍳 CustomCraftings System (/oecraft)

Add custom recipes via YAML and expose them in a GUI:

recipes:
  magic_apple:
    result: "GOLDEN_APPLE"
    shape:
      - "GGG"
      - "GAG"
      - "GGG"
    ingredients:
      G: "GOLD_INGOT"
      A: "APPLE"


GUI editor via /oecraft

Per-permission recipes

Supports custom items (via custom model data or PDC)

🔁 Trading System (Same Server & Cross-Server)

/trade <player> to open a secure trade GUI

Confirmation stage to prevent scams

Cross-server using RabbitMQ for:

Trade requests

Inventory snapshots

Secure item transfer

💬 Chat System

Configured in chat-format.yml:

Channels (global, staff, local)

Hex colors & gradients:
"<#ff8800:#ff00ff>Gradient Text"

PlaceholderAPI placeholders

Discord relay compatible

Example:

chat:
  format: "<#ff8800:%player_name%> &7» &f%message%"
  enable-gradient: true

💸 Economy System

Vault hook (use any Vault economy plugin)

Or internal database via Mongo/Postgres

Cross-server balance sync

Commands:

/balance (/bal)

/pay <player> <amount>

/baltop

📊 PlaceholderAPI Placeholders

Some examples (names may vary depending on final implementation):

%oreo_home_count%

%oreo_warp_count%

%oreo_playtime%

%oreo_daily_streak%

%oreo_jail_status%

%oreo_ec_balance%

%oreo_server_name%

%oreo_crossserver_enabled%

%oreo_trade_cooldown%

(Full list documented in the Wiki / GitBook.)

🔍 Comparison with Other Essentials Plugins
Feature / Plugin	OreoEssentials	EssentialsX	CMI	ZEssentials
🧭 Homes / Warps / Spawn	✅ Yes (cross-server supported)	✅ Yes	✅ Yes	✅ Yes
🌍 Cross-Server Teleports	✅ RabbitMQ + plugin messaging	❌ No	⚠️ Limited (Bungee only)	❌ No
💾 Cross-Server Economy	✅ MongoDB / PostgreSQL / Redis	❌ Vault-only	⚠️ Local / MySQL	⚠️ Local only
🧰 Database Backends	MongoDB, PostgreSQL, JSON, Redis cache	Flatfile / MySQL	SQLite / MySQL	YAML only
📦 Cross-Server EnderChest	✅ Rank-based slots	❌ No	⚠️ Local only	❌ No
🎒 Inventory Sync	✅ Inventory / XP / Health / Hunger	❌ No	⚠️ MySQL limited	❌ No
🪙 Vault Economy Support	✅ Yes	✅ Yes	✅ Yes	✅ Yes
⚙️ Redis Integration	✅ Optional cache	❌ No	❌ No	❌ No
🐰 RabbitMQ Integration	✅ Yes (multi-server sync)	❌ No	❌ No	❌ No
💬 Chat Formatter	✅ Gradient & PAPI	⚠️ Basic	✅ Advanced	✅ Basic
🗺️ Portals / JumpPads	✅ Built-in	❌ No	⚠️ With addon	❌ No
🎲 Random Teleport (/rtp)	✅ Rank-based, configurable	⚠️ Basic	✅ Yes	✅ Yes
✈️ Flight / God / Vanish	✅ Built-in	✅ Yes	✅ Yes	✅ Yes
⚒️ Moderation Tools	✅ Ban / Kick / Mute / Freeze / Jail	✅ Basic	✅ Full	✅ Basic
🔁 Proxy Support	✅ Velocity & BungeeCord	❌ No	⚠️ Bungee only	❌ No
🧩 PlaceholderAPI Support	✅ Yes	✅ Yes	✅ Yes	✅ Yes
💎 Custom GUIs	✅ SmartInvs-based	❌ No	✅ Yes	❌ No
📡 Multi-server Data Sync	✅ Automatic via DB + MQ	❌ No	⚠️ MySQL only	❌ No
🧩 API / Dev Hooks	✅ JSON + Java API	⚠️ Limited	✅ Extensive	⚠️ Minimal
💰 Pricing / License	Free (Oreo Studios)	Free	Paid	Free

TL;DR: OreoEssentials is the only Essentials-style plugin built for modern multi-server networks using Mongo + Redis + RabbitMQ, while still providing all the classic QoL commands.

🧑‍💻 Commands (High-Level Overview)

Full commands & permissions list is available in the Wiki / GitBook.

Player Commands

/home, /sethome, /delhome, /homes

/warp, /setwarp, /delwarp, /warps

/spawn

/rtp

/back

/tpa, /tpahere, /tpaccept, /tpdeny

/ec (EnderChest)

/kit, /kits

/bal, /pay

/daily, /playtime, /rewards

/trade <player>

Staff/Admin Commands

/ban, /tempban, /unban

/mute, /tempmute, /unmute

/kick

/freeze

/jail, /unjail

/invsee

/sudo

/oereload (reload configs)

/oecraft (custom craftings GUI)

/ic (interactive commands editor)

/oeteleport (admin teleports)

🤝 Contributing

Contributions are welcome:

Fork this repository

Create a feature branch: git checkout -b feature/my-feature

Commit your changes: git commit -m "Add my feature"

Push the branch: git push origin feature/my-feature

Open a Pull Request

Please follow existing code style & use meaningful commit messages.

🐞 Bug Reports & Support

SpigotMC resource page (discussion & reviews)

Oreo Studios Discord (recommended for fast support)

GitHub Issues for:

Bugs

Feature requests

Suggestions

When reporting a bug, include:

Server version

OreoEssentials version

Startup log / stacktrace

Config snippets (if relevant)

📄 License

OreoEssentials is free to use on any server.
License details and usage terms are provided in the LICENSE file.

Made with ❤️ by Oreo Studios
“Stop buying 10 plugins. Use one that actually understands networks.”
