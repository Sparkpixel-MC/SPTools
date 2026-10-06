<div align="center">

**English** | [简体中文](README.zh-CN.md)

</div>

# SPTools

A lightweight server customization plugin for **Paper & Folia**, developed by the Sparkpixel team. It bundles a minigame queue system, fancy gradient join/quit messages with daily quotes, a chat-session blocker, and a few quality-of-life utilities — all fully compatible with Folia's multi-threaded regions.

## ✨ Features

- **Queue System** — Players join a named queue; when it fills up, a group is formed. Members confirm with `/ready`, a countdown runs, and everyone is handed off to your game via a configurable command. Fully supports confirmation timeout, per-queue timing, and `require-confirmation: false` for auto-start.
- **Gradient Join / Quit Messages** — Daily-quote ("Hitokoto") powered join messages with per-rank gradient styles, sounds, and particles (SVIP / VIP / default via [LuckPerms](https://luckperms.net) groups).
- **Chat Session Blocker** — Cancels the client's `CHAT_SESSION_UPDATE` packet via [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/), so chat signing is never established.
- **BossBar Cleaner** — `/rmbbars` removes every keyed BossBar currently visible.
- **Keyboard Menu Shortcut** — Sneak + swap-hands (default `Shift + F`) runs the `/cd` command, handy for menu plugins.

## 📋 Requirements

| | |
|---|---|
| Server | Paper or Folia **26.2+** (`folia-supported: true`) |
| Java | **25+** |
| Optional | [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997.0/) (chat session blocker), [LuckPerms](https://luckperms.net) (join-message styles), Vault |

> The plugin detects Folia at startup and uses the native region/entity/async schedulers on Folia, and the traditional scheduler on Paper — one jar for both.

## 📦 Installation

1. Download the latest JAR from [Releases](../../releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server. Edit `plugins/SPTools/config.yml` to taste and restart again.

## 🕹️ Commands

| Command | Aliases | Description |
|---|---|---|
| `/queue join <queue>` | | Join a queue |
| `/queue leave` | | Leave your queue |
| `/queue list` | | List available queues |
| `/queue info <queue>` | | Show a queue's configuration |
| `/confirm` | `/ready` | Confirm participation in a formed group |
| `/leavequeue` | `/leave`, `/lq` | Leave the current queue/group |
| `/rmbbars` | `/removebossbars` | Remove all BossBars |

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `sptools.use` | all | Use the queue commands |
| `sptools.admin` | op | Parent of the permissions below |
| `sptools.reload` | op | Reload the plugin configuration |
| `sptools.bypass` | op | Bypass queue limits |
| `removebossbars.use` | op | Use `/rmbbars` |

## ⚙️ Configuration

```yaml
queue-enabled: true
hitokoto-enabled: true

queues:
  survival:
    min-players: 2
    max-players: 10
    # Command executed for each member when the countdown ends,
    # run from the player's own context
    game-command: "mv tp survival"
    confirmation-time: 30   # seconds to wait for /ready
    countdown-time: 10      # teleport countdown after confirmation
    buffer-time: 20
    require-confirmation: true  # false = start the countdown immediately

messages:
  queue.join.success: "&a您已加入 &e{queue}&a 队列! 当前排队: &6{current}&a/&6{max}"
  # ... all messages are configurable, & = color code, {xxx} = placeholder
```

Join-message ranks are read from LuckPerms groups: members of `svip` / `vip` get their dedicated gradient styles; everyone else uses the default style.

## 🛠️ Building

```bash
./gradlew build
```

Requires JDK 25. The artifact is produced at `build/libs/SPTools-<version>.jar`.

GitHub Actions builds and publishes a release on every push to `main`/`master` — see [.github/workflows/build.yml](.github/workflows/build.yml).

## 🧵 Folia Compatibility

- All scheduling goes through a single wrapper that routes to the native **global-region / entity / async schedulers** on Folia.
- Shared state uses concurrent collections; cross-region player access (messages, sounds, commands) is dispatched on the owning entity scheduler.
- No unavailable Folia events (e.g. `PlayerTeleportEvent`) are used.

---

_If you encounter any issues, please open an Issue and include your server version, logs, config, and reproduction steps._
