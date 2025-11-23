# 🍪 OreoEssentials

<p align="center">
  <img width="500" height="500" alt="OreoEssentials logo" src="https://github.com/user-attachments/assets/dd1bd76b-3602-4019-95c6-36337faa1222" />
</p>

<p align="center">
  <strong>The most advanced all-in-one Essentials plugin for Paper 1.21+</strong><br />
  Free &amp; open-source plugin by <strong>Oreo Studios</strong>
</p>

<p align="center">
  Documentation: <a href="https://docs.oreo-studio.shop/oreoessentials/">https://docs.oreo-studio.shop/oreoessentials/</a>
</p>

OreoEssentials provides a modern Essentials replacement for single and multi-server Paper networks with homes, warps, kits, GUIs, cross-server syncing (inventory/enderchest/economy), RabbitMQ/Redis/Mongo integrations, moderation tools, and much more — all designed for high-performance networks.

---

## ✨ Highlights

- Homes • Warps • Spawn (including cross-server handoff)
- Kits with cooldowns, permissions & GUI
- Portals & JumpPads
- /rtp with rank-based radius & region support
- Chat formatter (hex gradients, RGB, PlaceholderAPI)
- Economy (Vault + internal DB backends)
- Cross-server inventory, XP, health & hunger sync
- Cross-server EnderChest with rank-based size
- Moderation tools: ban, mute, kick, freeze, jail
- OreoHolograms — built-in hologram system (armor stands)
- Alias editor for command renames (aliases.yml)
- DailyRewards & PlaytimeRewards
- Same-server & cross-server trading
- Integrations: RabbitMQ, Redis, MongoDB
- Async operations and Redis caching for performance
- SmartInvs-based GUIs for modern UX
- 200+ commands and extensive PlaceholderAPI placeholders

---

## 📦 Requirements

- Server: Paper / Spigot **1.21+**
- Java: **17+**
- Optional services:
  - MongoDB (recommended for persistence)
  - Redis (cache and fast sync signals)
  - RabbitMQ (cross-server messaging)
  - Vault + economy provider (for /balance, /pay)
  - PlaceholderAPI (for placeholders in chat, holograms & GUIs)

---

## 🚀 Installation (Single Server)

1. Download `OreoEssentials.jar`.
2. Drop it into your server `/plugins` folder.
3. Start the server to generate the configuration files.
4. Edit configs at `/plugins/OreoEssentials/` (see list below).
5. Restart the server.

Once started, configure permissions and optional integrations (Mongo/Redis/RabbitMQ/Vault/PAPI) as needed.

---

## 🌍 Cross-Server Setup (Multi-Server Networks)

OreoEssentials is designed to work across Velocity/Bungee + multiple Paper servers.

### Database (Mongo / Redis)

Set the database options in `database.yml`. Example:

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

- MongoDB: recommended as the canonical storage for homes, warps, player data.
- Redis: used as a cache + fast sync signals (optional but recommended).

### RabbitMQ (Cross-Server Messaging)

In `rabbitmq.yml`:

```yaml
enabled: true
host: "127.0.0.1"
port: 5672
username: "guest"
password: "guest"
virtual-host: "/"
prefix: "oreo"
```

RabbitMQ facilitates cross-server home/warp/spawn teleports, trades, inventory and enderchest sync signals.

### Feature toggles

Enable only what you need in `settings.yml`:

```yaml
features:
  cross-server:
    homes: true
    warps: true
    spawn: true
    economy: true
    enderchest: true
    inventory-sync: true
```

---

## 🧾 Configuration files overview

Common config files created under `/plugins/OreoEssentials/`:

- settings.yml — core toggles & features
- database.yml — MongoDB / PostgreSQL / Redis configuration
- rabbitmq.yml — cross-server messaging configuration
- chat-format.yml — chat formats, gradients, channels
- messages.yml — all translatable messages
- dailyrewards.yml — daily rewards configuration
- playtime-rewards.yml — playtime reward milestones
- portals.yml — portals & jump pads
- aliases.yml — alias editor
- holograms.yml — OreoHolograms definitions
- other plugin-specific files (kits.yml, warps.yml, homes.yml, etc.)

---

## 🔧 Examples

Hologram example (`holograms.yml`):

```yaml
spawn-board:
  world: "world"
  x: 0.5
  y: 80.0
  z: 0.5
  lines:
    - "&b&lWelcome to &f&lYourServer"
    - "&7Online: &a%server_online%"
    - "&7Balance: &e%vault_eco_balance_fixed%"
```

Alias editor (`aliases.yml`):

```yaml
home:
  - "maison"
  - "hm"

spawn:
  - "hub"
  - "lobby"
```

Daily rewards (`dailyrewards.yml`):

```yaml
rewards:
  day-1:
    display-name: "&aDay 1"
    commands:
      - "eco give %player% 500"
  day-7:
    display-name: "&6Day 7 (Streak!)"
    commands:
      - "eco give %player% 5000"
      - "lp user %player% parent add vip-temp"
```

Playtime rewards (`playtime-rewards.yml`):

```yaml
milestones:
  "3600":     # seconds => 1 hour
    commands:
      - "eco give %player% 1000"
  "21600":    # 6 hours
    commands:
      - "crate key give %player% playtime 1"
```

Chat format sample (`chat-format.yml`):

```yaml
chat:
  format: "<#ff8800:%player_name%> &7» &f%message%"
  enable-gradient: true
```

---

## 🧑‍💻 Commands (Overview)

A small subset — see the full commands & permissions in the Wiki/Docs:

Player commands:
- /home, /sethome, /delhome, /homes
- /warp, /setwarp, /delwarp, /warps
- /spawn
- /rtp
- /back
- /tpa, /tpahere, /tpaccept, /tpdeny
- /ec (enderchest)
- /kit, /kits
- /bal, /pay
- /daily, /playtime, /rewards
- /trade <player>

Staff/Admin commands:
- /ban, /tempban, /unban
- /mute, /tempmute, /unmute
- /kick
- /freeze
- /jail, /unjail
- /invsee
- /sudo
- /oereload (reload configs)
- /oecraft (custom crafting GUI)
- /ic (interactive commands editor)

---

## 🔌 Integrations & Placeholders

- Vault (economy interactions)
- PlaceholderAPI (chat, holograms, GUIs)
- MongoDB, Redis, RabbitMQ (optional multi-server sync)
- Example placeholders:
  - %oreo_home_count%
  - %oreo_warp_count%
  - %oreo_playtime%
  - %oreo_daily_streak%
  - %oreo_ec_balance%
  - %oreo_server_name%
  - %oreo_crossserver_enabled%

---

## 🤝 Contributing

Contributions are welcome!

- Fork the repository ( READ FORK POLICY FIRST)
- Create a feature branch: git checkout -b feature/my-feature
- Commit your changes: git commit -m "Add my feature"
- Push the branch: git push origin feature/my-feature
- Open a Pull Request

Please follow existing code style and use meaningful commit messages. See the documentation link above for developer guidelines and contribution details.

---

## 🐞 Bug Reports & Support

When reporting a bug, include:
- Server version (Paper/Spigot)
- OreoEssentials version
- Startup log / stacktrace
- Relevant config snippets

Support channels:
- SpigotMC resource page (discussions & reviews)
- Oreo Studios Discord (recommended for fast help)
- GitHub Issues (for reproducible bugs & feature requests)

---

## Forking Policy

Oreo Studios appreciates community interest in contributing and collaborating. To avoid confusion about official forks and derivative distributions, we require explicit permission before publicly forking or distributing a derived version of this repository.

If you wish to fork or publicly distribute a derived plugin, please follow these steps to request permission:

1. Open a ticket on our official Oreo Studios Discord. Include a link to your Discord profile or tag so we can verify your request.
2. In the ticket, provide:
   - Your GitHub username
   - A short description of how you intend to use or distribute the fork
   - The repository or project URL you plan to publish (if applicable)
   - Any substantive changes you plan to make that differ from the original project
3. Wait for a reply from an Oreo Studios maintainer. We will respond with approval, denial, or follow-up questions.

Note: This is our project policy. The legal rights and permissions that apply to this repository are governed by the repository's LICENSE file. If you are unsure how the license applies to your intended use, consult legal counsel.

---

## Copyright & Company Info

Copyright (c) Oreo Studios.

Oreo Studios is an officially registered company in France.
- SIRET: 993 823 469 00017
- Code APE: 62.01Z

All rights reserved. Portions of this project are provided under the terms of the LICENSE file included in this repository. For formal copyright claims, permission requests, or questions related to commercial use, please open a ticket on our official Oreo Studios Discord so we can respond and track your request.

---

## 📄 License

OreoEssentials is free to use. See the LICENSE file in this repository for full licensing terms.

---

Made with ❤️ by Oreo Studios  
“Stop buying 10 plugins. Use one that actually understands networks.”
