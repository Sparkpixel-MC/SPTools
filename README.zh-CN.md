<div align="center">

[English](README.md) | **简体中文**

</div>

# SPTools

由 Sparkpixel 团队开发的 **Paper & Folia** 轻量级服务器自定义插件。集小游戏排队系统、渐变进退服消息（附每日一言）、聊天签名拦截器和若干实用小功能于一体，完整兼容 Folia 的多线程 Region 架构。

## ✨ 功能特性

- **队列系统** — 玩家加入指定队列，人数满足后自动成组；成员使用 `/ready` 确认，倒计时结束后通过可配置的命令将每位成员交给你的小游戏。支持确认超时、逐队列参数配置，以及 `require-confirmation: false` 的免确认直开。
- **渐变进退服消息** — 基于"每日一言"（Hitokoto）API 的进服祝福消息，按权限组展示不同渐变样式、音效与粒子效果（SVIP / VIP / 默认，基于 [LuckPerms](https://luckperms.net)）。
- **聊天签名拦截** — 通过 [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 拦截客户端的 `CHAT_SESSION_UPDATE` 数据包，阻止聊天签名建立。
- **BossBar 清理** — `/rmbbars` 一键移除当前所有 BossBar。
- **键盘菜单快捷键** — 潜行 + 副手切换（默认 `Shift + F`）触发 `/cd` 命令，方便配合菜单插件使用。

## 📋 环境要求

| | |
|---|---|
| 服务端 | Paper 或 Folia **26.2+**（已声明 `folia-supported: true`） |
| Java | **25+** |
| 可选依赖 | [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997.0/)（聊天签名拦截）、[LuckPerms](https://luckperms.net)（进服消息样式）、Vault |

> 插件启动时自动检测 Folia：Folia 下使用原生 Region / 实体 / 异步调度器，Paper 下自动回退传统调度器——一个 jar 通吃两端。

## 📦 安装

1. 从 [Releases](../../releases) 下载最新 JAR。
2. 放入服务器的 `plugins/` 目录。
3. 重启服务器。按需修改 `plugins/SPTools/config.yml` 后再次重启即可生效。

## 🕹️ 命令

| 命令 | 别名 | 说明 |
|---|---|---|
| `/queue join <队列名>` | | 加入队列 |
| `/queue leave` | | 离开队列 |
| `/queue list` | | 查看可用队列 |
| `/queue info <队列名>` | | 查看队列配置详情 |
| `/confirm` | `/ready` | 确认参与已组成的队伍 |
| `/leavequeue` | `/leave`, `/lq` | 离开当前队列/队伍 |
| `/rmbbars` | `/removebossbars` | 移除所有 BossBar |

## 🔑 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `sptools.use` | 所有玩家 | 使用队列相关命令 |
| `sptools.admin` | OP | 以下权限的父节点 |
| `sptools.reload` | OP | 重载插件配置 |
| `sptools.bypass` | OP | 无视队列限制 |
| `removebossbars.use` | OP | 使用 `/rmbbars` |

## ⚙️ 配置文件

```yaml
queue-enabled: true
hitokoto-enabled: true

queues:
  survival:
    min-players: 2
    max-players: 10
    # 倒计时结束后为每位成员执行的命令，在玩家自己的上下文中运行
    game-command: "mv tp survival"
    confirmation-time: 30   # 等待 /ready 确认的时间（秒）
    countdown-time: 10      # 确认后的传送倒计时（秒）
    buffer-time: 20
    require-confirmation: true  # false = 无需确认，直接倒计时

messages:
  queue.join.success: "&a您已加入 &e{queue}&a 队列! 当前排队: &6{current}&a/&6{max}"
  # ... 所有消息均可配置，& 为颜色代码，{xxx} 为占位符
```

进服消息的等级样式读取 LuckPerms 权限组：`svip` / `vip` 组成员拥有专属渐变样式，其余玩家使用默认样式。

## 🛠️ 构建

```bash
./gradlew build
```

需要 JDK 25，产物输出至 `build/libs/SPTools-<version>.jar`。

每次推送到 `main`/`master` 分支时，GitHub Actions 会自动构建并发布 Release，详见 [.github/workflows/build.yml](.github/workflows/build.yml)。

## 🧵 Folia 兼容性

- 所有调度统一经由调度器包装层，Folia 下路由到原生**全局 Region / 实体 / 异步调度器**。
- 共享状态均使用并发集合；跨 Region 的玩家操作（消息、音效、命令）都会派发到其所属的实体调度器执行。
- 未使用任何 Folia 不支持的事件（如 `PlayerTeleportEvent`）。

---

_如遇问题，请提交 Issue，并附上服务端版本、日志、配置文件与复现步骤。_
