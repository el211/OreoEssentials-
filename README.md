# 🍪 OreoEssentials
<img width="500" height="500" alt="image" src="https://github.com/user-attachments/assets/dd1bd76b-3602-4019-95c6-36337faa1222" />

> The most advanced all-in-one Essentials plugin for Paper 1.21+  
> Free & open-source plugin by **Oreo Studios**

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
- AND MORE
---

## Table of contents

- [Requirements](#requirements)  
- [Installation (Single Server)](#installation-single-server)  
- [Cross-Server Setup (Multi-Server Networks)](#cross-server-setup-multi-server-networks)  
  - [Database (Mongo / Redis)](#database-mongo--redis)  
  - [RabbitMQ (Cross-Server Messaging)](#rabbitmq-cross-server-messaging)  
  - [Feature toggles](#feature-toggles)  
- [Configuration files overview](#configuration-files-overview)  
- [Examples](#examples)  
- [Commands (Overview)](#commands-overview)  
- [Contributing](#contributing)  
- [Bug reports & Support](#bug-reports--support)  
- [Forking Policy](#forking-policy)  
- [Copyright & Company Info](#copyright--company-info)  
- [License](#license)

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

... (rest of README unchanged) ...

---

## 🤝 Contributing

Contributions are welcome!

- Fork the repository
- Create a feature branch: git checkout -b feature/my-feature
- Commit your changes: git commit -m "Add my feature"
- Push the branch: git push origin feature/my-feature
- Open a Pull Request

Please follow existing code style and use meaningful commit messages.

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

We appreciate interest in contributing and collaborating. To avoid confusion about official forks and derivative distributions, Oreo Studios requests that anyone who wishes to fork this repository for any public distribution, modification, or to release a derived plugin publicly first obtains explicit permission.

To request permission:
1. Open a ticket on our official Oreo Studios Discord (please include a link to your Discord server profile or tag).
2. In the ticket, provide:
   - Your GitHub username
   - A short description of how you intend to use or distribute the fork
   - The repository or project URL you plan to publish (if applicable)
   - Any changes you plan to make that differ from the original project
3. Wait for a reply from an Oreo Studios maintainer. We will respond with approval, denial, or follow-up questions.

Note: This is our project policy. The legal rights and permissions that apply to this repository are ultimately governed by the repository's LICENSE file. Requesting permission via Discord is required by Oreo Studios for public forks/releases; if you are unsure how the license applies to your intended use, please consult legal counsel.

---

## Copyright & Company Info

Copyright (c) Oreo Studios.

Oreo Studios is an officially registered company in France.
- SIRET: 993 823 469 00017
- Code APE: 62.01Z

All rights reserved. Portions of this project are provided under the terms of the LICENSE file included in this repository. For any formal copyright claims, permission requests, or questions related to commercial use, please open a ticket on our official Oreo Studios Discord so we can respond and track your request.

---

## 📄 License

OreoEssentials is free to use. See the LICENSE file in this repository for full licensing terms.

---

Made with ❤️ by Oreo Studios  
“Stop buying 10 plugins. Use one that actually understands networks.”
```
