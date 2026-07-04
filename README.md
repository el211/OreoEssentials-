# 🍪 OreoSlop

---

> ## ⛔ FORKING POLICY — READ BEFORE ANYTHING ELSE
>
> **Forking or publicly distributing any derivative of this repository without explicit written authorization from ghosty.im is strictly allowed.**
>
> This applies to forks, mirrors, re-uploads, rebrands, and any derived work — whether modified or not.
> Violations may result in a DMCA takedown and legal action under French intellectual property law.
>
> To request authorization, do Not open a ticket on the official **Ghosty's Stuff Discord** and provide:
> - Your GitHub username
> - A description of your intended use / distribution
> - The repository URL you plan to publish (if any)
> - A summary of the changes you plan to make
>
> A maintainer will respond with approval, denial, or follow-up questions.
> The non-existant (cause it's AI slop) LICENSE file governs your legal rights. If you are unsure how it applies, consult legal counsel.

---

OreoEssentials is a modern Essentials replacement for single and multi-server Paper networks. It provides homes, warps, kits, GUIs, cross-server syncing (inventory / enderchest / economy), RabbitMQ/Redis/MongoDB integrations, moderation tools, and much more — all designed for high-performance networks.

---

## ✨ Highlights

- Homes • Warps • Spawn (including cross-server handoff)
- Kits with cooldowns, permissions & GUI
- Portals & JumpPads
- /rtp with rank-based radius & region support
- Chat formatter (hex gradients, RGB, PlaceholderAPI, MiniMessage)
- Economy (Vault + internal DB backends)
- Multi-currency system (async, leaderboard, PAPI placeholders)
- Cross-server inventory, XP, health & hunger sync
- Cross-server EnderChest with rank-based size
- Moderation tools: ban, mute, kick, freeze, jail
- OreoHolograms — built-in hologram system (armor stands + display entities)
- Alias editor for command renames (`aliases.yml`)
- DailyRewards & PlaytimeRewards
- Same-server & cross-server trading
- Market orders / auction house
- AFK detection with optional AFK pool teleport
- Vanish, freeze, temp-fly, maintenance mode
- Web panel integration (REST API + RabbitMQ sync)
- Integrations: RabbitMQ, Redis, MongoDB, Vault, PlaceholderAPI
- Async operations and Redis caching for performance
- SmartInvs-based GUIs for modern UX
- 200+ commands and extensive PlaceholderAPI placeholders
- Folia-compatible

---

## 📦 Requirements

| Requirement | Details |
|---|---|
| Server | Paper / Spigot **1.21+** (Folia supported) |
| Java | **17+** |
| MongoDB | Recommended for persistence |
| Redis | Cache and fast sync signals |
| RabbitMQ | Cross-server messaging |
| Vault | For `/balance`, `/pay` |
| PlaceholderAPI | For placeholders in chat, holograms & GUIs |

---

## 🚀 Installation (Single Server)

1. Download `OreoEssentials.jar`.
2. Drop it into your server `/plugins` folder.
3. Start the server to generate configuration files.
4. Edit configs under `/plugins/OreoEssentials/`.
5. Restart the server.

---

## 🌍 Cross-Server Setup (Multi-Server Networks)

OreoEssentials is designed to work across Velocity/BungeeCord + multiple Paper servers.

### Database (`database.yml`)

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

### RabbitMQ (`rabbitmq.yml`)

```yaml
enabled: true
host: "127.0.0.1"
port: 5672
username: "guest"
password: "guest"
virtual-host: "/"
prefix: "oreo"
```

### Feature Toggles (`settings.yml`)

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

## 🧾 Configuration Files

| File | Purpose |
|---|---|
| `settings.yml` | Core toggles & features |
| `database.yml` | MongoDB / PostgreSQL / Redis |
| `rabbitmq.yml` | Cross-server messaging |
| `chat-format.yml` | Chat formats, gradients, channels |
| `messages.yml` | All translatable messages |
| `dailyrewards.yml` | Daily rewards |
| `playtime-rewards.yml` | Playtime reward milestones |
| `portals.yml` | Portals & jump pads |
| `aliases.yml` | Command alias editor |
| `holograms.yml` | OreoHolograms definitions |
| `afk/config.yml` | AFK module + pool teleport |
| `orders/` | Market orders module |

---

## 🧑‍💻 Commands Overview

**Player commands:**
`/home` `/sethome` `/delhome` `/homes` `/warp` `/setwarp` `/delwarp` `/warps` `/spawn` `/rtp` `/back` `/tpa` `/tpahere` `/tpaccept` `/tpdeny` `/ec` `/kit` `/kits` `/bal` `/pay` `/daily` `/playtime` `/rewards` `/trade` `/afk` `/msg` `/reply` `/sit`

**Staff / Admin commands:**
`/ban` `/tempban` `/unban` `/mute` `/tempmute` `/unmute` `/kick` `/freeze` `/jail` `/unjail` `/invsee` `/sudo` `/vanish` `/oereload` `/oecraft` `/ic`

Full commands & permissions: [docs.oreostudios.fr](https://docs.oreostudios.fr/oreoessentials/)

---

## 🔌 Integrations & Placeholders

- Vault, PlaceholderAPI, MongoDB, Redis, RabbitMQ, ItemsAdder, Nexo, PacketEvents, SmartInvs

Default PAPI placeholders:

| Placeholder | Returns |
|---|---|
| `%oreo_home_count%` | Number of homes set |
| `%oreo_warp_count%` | Number of warps |
| `%oreo_playtime%` | Total playtime |
| `%oreo_daily_streak%` | Current daily streak |
| `%oreo_ec_balance%` | Enderchest row count |
| `%oreo_server_name%` | Current server name |
| `%oreo_crossserver_enabled%` | Cross-server toggle state |

Currency placeholders (via `oreocurrency` expansion — see API section):

| Placeholder | Returns |
|---|---|
| `%oreocurrency_balance_<id>%` | Raw balance |
| `%oreocurrency_balance_formatted_<id>%` | Formatted with symbol |
| `%oreocurrency_symbol_<id>%` | Currency symbol |
| `%oreocurrency_name_<id>%` | Currency display name |
| `%oreocurrency_rank_<id>%` | Leaderboard rank |
| `%oreocurrency_top_<id>_<n>_name%` | Top-N player name |
| `%oreocurrency_top_<id>_<n>_balance%` | Top-N player balance |

---

## 🛠️ Developer API

OreoEssentials exposes a full developer API for other plugins.

### Adding the Dependency

Add to your `plugin.yml`:
```yaml
softdepend:
  - OreoEssentials
```

### Getting the API Instance

```java
RegisteredServiceProvider<OreoEssentialsAPI> rsp =
    Bukkit.getServicesManager().getRegistration(OreoEssentialsAPI.class);
if (rsp == null) {
    getLogger().warning("OreoEssentials not loaded — API unavailable.");
    return;
}
OreoEssentialsAPI oes = rsp.getProvider();
```

Or via the static shorthand:
```java
OreoEssentialsAPI api = OreoEssentialsAPI.get(); // null if not loaded
```

---

### API Modules (44 interfaces)

#### Economy & Currency

| Interface | Description |
|---|---|
| `IEconomyAPI` | Built-in Vault-compatible economy (synchronous) |
| `ICurrencyAPI` | Multi-currency system — async, CompletableFuture-based |
| `IOrdersAPI` | Market orders (create, fill, cancel, list) |
| `IAuctionHouseAPI` | Auction house (list, purchase, cancel, browse) |
| `IShopAPI` | Buy/sell shop processing |

**Async currency example:**
```java
ICurrencyAPI gems = oes.currency("gems");
if (gems != null) {
    gems.withdraw(playerUUID, 100.0).thenAccept(success -> {
        if (success) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("Withdrawn 100 gems.");
            });
        }
    });
}
```

`ICurrencyAPI` methods: `getBalance(UUID)`, `has(UUID, amount)`, `deposit(UUID, amount)`, `withdraw(UUID, amount)`, `setBalance(UUID, amount)`, `transfer(UUID from, UUID to, amount)` — all return `CompletableFuture<Boolean>` or `CompletableFuture<Double>`.

#### Teleportation & Locations

| Interface | Description |
|---|---|
| `ITeleportAPI` | TPA requests and silent teleportation |
| `IWarpsAPI` | Server warps (list, get, set, delete, rename) |
| `IPlayerWarpsAPI` | Player-created personal warps |
| `ISpawnAPI` | Main spawn and first-spawn management |
| `IBackAPI` | `/back` last-location tracking |
| `IDeathBackAPI` | Death location tracking and teleport |
| `IJumpPadsAPI` | Jump pad creation and management |
| `IPortalsAPI` | Cuboid portal creation and management |

#### Inventory & Storage

| Interface | Description |
|---|---|
| `IPlayerVaultsAPI` | Personal vaults (multi-vault, tier-based slots) |
| `IEnderChestAPI` | Ender chest with rank-based row counts |
| `ISellGuiAPI` | Item selling GUI |

#### Player Status & Cosmetics

| Interface | Description |
|---|---|
| `IAfkAPI` | AFK detection and toggle (`isAfk`, `toggleAfk`, `getAfkForSeconds`) |
| `IVanishAPI` | Staff invisibility toggle |
| `IFreezeAPI` | Freeze / unfreeze players |
| `ITempFlyAPI` | Grant timed flight (`grantFlight(Player, seconds)`) |
| `IScoreboardAPI` | Animated sidebar scoreboard |
| `IBossBarAPI` | Personal boss bar display |
| `INametagAPI` | Custom nametags above players |
| `IHologramsAPI` | OHolograms — create, update, remove, reload |

#### Moderation & Control

| Interface | Description |
|---|---|
| `IPunishmentAPI` | Punishment history (get, clear) |
| `IWarningsAPI` | Warning system (warn, unwarn, list, clear) |
| `IJailAPI` | Jail / release players |
| `IIgnoreAPI` | Player ignore lists |
| `ICommandControlAPI` | Block / unblock commands at runtime |
| `ICommandToggleAPI` | Enable / disable commands at runtime |
| `ICrossServerAPI` | Cross-server moderation (kill, kick, ban, freeze, vanish…) |
| `IMaintenanceAPI` | Maintenance mode + whitelist |

#### Gameplay

| Interface | Description |
|---|---|
| `IKitsAPI` | Kits (list, get, claim, cooldown check) |
| `IMailAPI` | In-game mail (send, get, mark read, delete) |
| `ITradeAPI` | Player-to-player trading sessions |
| `IDailyAPI` | Daily reward claim and status |
| `IPlaytimeAPI` | Playtime tracking (get, set, add) |
| `IAliasesAPI` | Command aliases with cooldowns |
| `ICustomCraftAPI` | Custom crafting recipes |
| `IInteractiveCommandsAPI` | IC commands bound to blocks/entities |

#### Server & Infrastructure

| Interface | Description |
|---|---|
| `IChatAPI` | Cross-server chat, broadcast mute/unmute |
| `IClearLagAPI` | Entity cleanup trigger |
| `IShardsAPI` | World sharding and cross-shard player transfer |
| `IAutoRebootAPI` | Automatic reboot scheduler |

---

### Plugin Events

Listen to OreoEssentials events by implementing Bukkit's `@EventHandler`:

#### Currency Events (`fr.elias.oreoEssentials.api.events`)

**`CurrencyTransactionEvent`** — Async, Cancellable
Fired before any deposit / withdraw / set-balance operation.

```java
@EventHandler
public void onTransaction(CurrencyTransactionEvent e) {
    if (e.getCurrencyId().equals("gems") 
        && e.getType() == CurrencyTransactionEvent.Type.WITHDRAW
        && e.getAmount() > 1000) {
        e.setCancelled(true);
    }
}
```

Fields: `getPlayerId()` · `getCurrencyId()` · `getType()` (DEPOSIT / WITHDRAW / SET) · `getAmount()`

**`CurrencyTransferEvent`** — Async, Cancellable
Fired before a player-to-player currency transfer.

Fields: `getFrom()` · `getTo()` · `getCurrencyId()` · `getAmount()`

#### Hologram Events (`fr.elias.oreoEssentials.modules.holograms.api.events`)

| Event | Thread | Cancellable | Description |
|---|---|---|---|
| `HologramCreateEvent` | Sync | Yes | Hologram being created |
| `HologramDeleteEvent` | Sync | Yes | Hologram being deleted |
| `HologramUpdateEvent` | Sync | Yes | Hologram data modified |
| `HologramShowEvent` | Sync | Yes | Hologram shown to a player |
| `HologramHideEvent` | Sync | Yes | Hologram hidden from a player |
| `HologramsLoadedEvent` | Async | No | All holograms loaded from disk |
| `HologramsUnloadedEvent` | Async | No | Holograms being unloaded |

`HologramUpdateEvent` exposes a `HologramModification` enum: `TEXT`, `POSITION`, `SCALE`, `TRANSLATION`, `BILLBOARD`, `BACKGROUND`, `TEXT_SHADOW`, `TEXT_ALIGNMENT`, `SEE_THROUGH`, `SHADOW_RADIUS`, `SHADOW_STRENGTH`, `UPDATE_TEXT_INTERVAL`, `UPDATE_VISIBILITY_DISTANCE`.

---

### RabbitMQ Packet API

Extend `Packet` to send custom cross-server data:

```java
public class MyPacket extends Packet {
    private String data;

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUtf(data);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        data = in.readUtf();
    }
}
```

Built-in packet types:

| Packet | Fields | Purpose |
|---|---|---|
| `DeathMessagePacket` | deadPlayerId, deadPlayerName, message, sourceServer | Broadcast death messages |
| `SendRemoteMessagePacket` | targetId, message | Send message to player on another server |
| `SafeZoneEnterPacket` | playerId, targetServer, worldName, regionName | World sharding / safe zone tracking |
| `JsonPacket` | json | Generic lightweight JSON wrapper |

All packets carry a unique `packetId (UUID)` set automatically by `PacketManager`.

---

## 🌐 Web Panel REST API

The web panel module (`WebPanelConfig`) connects OreoEssentials to an external Spring Boot dashboard.

### Authentication

All requests require the header:
```
X-Api-Key: oreo_<prefix>_<secret>
```

The key is configured in `plugins/OreoEssentials/webpanel/config.yml`.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/plugin/ping` | Health check / verify API key |
| `POST` | `/api/v1/plugin/sync` | Push full player data snapshot |
| `POST` | `/api/v1/plugin/weblink/register` | Register a web-link account code |
| `GET` | `/api/v1/plugin/players/{uuid}/registered` | Check if UUID has a linked panel account |
| `POST` | `/api/v1/plugin/heartbeat` | Periodic heartbeat with online UUIDs |
| `GET` | `/api/v1/plugin/actions?onlinePlayers=...` | Poll pending SELL/DELETE actions from panel |
| `GET` | `/api/v1/plugin/deliveries?playerUuid=...` | Poll items queued for delivery on login |
| `POST` | `/api/v1/plugin/deliveries/confirm` | Confirm delivered items |
| `POST` | `/api/v1/plugin/orders/sync` | Push active market orders to panel |
| `POST` | `/api/v1/plugin/luckperms/sync` | Sync LuckPerms groups + permissions to panel |
| `POST` | `/api/v1/plugin/afk/status` | Push AFK enter/exit event (REST fallback) |

### Sync Mechanism

- **HTTP polling** for actions and deliveries (configurable interval)
- **RabbitMQ (optional)** for real-time async messaging
- **Automatic fallback** to REST when RabbitMQ is unavailable

### Sync Body Examples

**POST /api/v1/plugin/sync**
```json
{
  "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
  "playerName": "Notch",
  "playerDataJson": "{...}"
}
```

**POST /api/v1/plugin/heartbeat**
```json
{
  "onlineUuids": ["uuid1", "uuid2"]
}
```

**POST /api/v1/plugin/afk/status**
```json
{
  "playerUuid": "...",
  "playerName": "Notch",
  "serverName": "survival",
  "world": "world",
  "x": 0,
  "y": 64,
  "z": 0,
  "afkSinceMs": 1716633600000,
  "entering": true
}
```

---

## 🐞 Bug Reports & Support

When reporting a bug, include:
- Server version (Paper/Spigot build)
- OreoEssentials version
- Startup log / stacktrace
- Relevant config snippets

Support channels:
- **Oreo Studios Discord** — fastest response
- **GitHub Issues** — reproducible bugs & feature requests

---

## Copyright & Company Info

Copyright © Oreo Studios. All rights reserved.

Oreo Studios is an officially registered company in France.
- **SIRET:** 993 823 469 00017
- **Code APE:** 62.01Z

For formal copyright claims, permission requests, or commercial use inquiries, open a ticket on the official Oreo Studios Discord.

---

## 📄 License

See the `LICENSE` file in this repository for full licensing terms.
Check the live license page: https://docs.oreostudios.fr/oreoessentials/general-license/general-license-oreoessentials

---

Made with ❤️ by Oreo Studios  
*"Stop buying 10 plugins. Use one that actually understands networks."*
