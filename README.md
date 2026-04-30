VillagerBucket

一款基于 Folia 的 Minecraft 村民桶插件，让玩家能用空桶捕获村民、用村民桶释放村民，并自动适配领地插件进行权限检查。
A Folia-based Minecraft villager bucket plugin that allows players to capture villagers with an empty bucket and release them from a villager bucket, with automatic permission checks for land claim plugins.

特性 / Features

· 完整的村民捕获与释放机制，保留村民职业、交易、属性、物品栏等全部 NBT 数据，确保释放后与捕获前一致
    Complete villager capture and release mechanism, preserving all NBT data including profession, trades, attributes, and inventory, ensuring the released villager is identical to the one captured.
· 完美支持 Folia 多线程区域调度，采用调度器适配层，在 Folia 环境下自动使用区域化任务，在其他 Paper 分支上则降级为同步任务，保障最佳兼容性与性能
    Full support for Folia's multi-threaded region scheduling via a scheduler abstraction layer. Region-scoped tasks are used automatically on Folia, while falling back to synchronous tasks on other Paper forks, guaranteeing maximum compatibility and performance.
· 自动检测并兼容 Residence、Dominion 等领地插件，在玩家没有对应领地权限时自动拦截捕获、释放与破坏操作，同时提供领地绕过权限
    Automatically detects and integrates with land claim plugins such as Residence and Dominion. Interactions like capturing, releasing, and destroying are blocked if the player lacks the required claim permissions, with a bypass permission available.
· 高度可配置：config.yml 中可自定义权限检查开关、粒子效果、声音、操作冷却、禁用世界列表等所有核心参数
    Highly configurable: all core parameters such as permission checks, particle effects, sounds, cooldowns, and disabled worlds can be customized in config.yml.
· 独立的消息配置文件 messages.yml，所有游戏内提示均可自定义，支持 & 颜色代码和多语言翻译，重载命令生效
    Standalone message configuration file messages.yml. All in-game messages are customizable, support & color codes, and can be localized. Changes take effect after a reload command.
· 防重复释放机制：基于玩家 UUID 和释放位置的复合冷却，防止因网络延迟或意外双击导致的物品复制或异常
    Anti-duplicate release mechanism: a composite cooldown based on player UUID and release location prevents item duplication or glitches caused by network lag or accidental double clicks.
· 异步桶有效性验证：释放前在异步线程中校验村民桶数据完整性，避免在主线程进行 IO 操作，提升服务器 TPS
    Asynchronous bucket validation: the integrity of villager bucket data is verified on an async thread before release, avoiding IO operations on the main thread and improving server TPS.
· 物品掉落保护：当玩家背包已满时，村民桶或空桶会自动掉落在玩家脚下，防止物品丢失
    Inventory overflow protection: if the player's inventory is full, the villager bucket or empty bucket will be dropped naturally at the player's feet, preventing item loss.

安装 / Installation

1. 下载最新版本的 VillagerBucket-*.jar 文件
      Download the latest VillagerBucket-*.jar file.
2. 将其放入服务器的 plugins 文件夹中
      Place it into the server's plugins folder.
3. 重启服务器或使用插件管理器加载
      Restart the server or load it with a plugin manager.
4. 插件将自动生成默认配置文件 config.yml 和消息文件 messages.yml，请根据需要编辑
      The plugin will automatically generate the default configuration file config.yml and message file messages.yml. Edit them as needed.
5. 使用 /villagerbucket reload 应用更改
      Apply changes with /villagerbucket reload.

使用方法 / Usage

· 捕获村民：手持空桶，对村民右键即可将其捕获。村民的所有数据（职业、等级、交易等）都会保留在桶中。
    Capturing a villager: Right-click a villager with an empty bucket to capture it. All villager data (profession, level, trades, etc.) is preserved in the bucket.
· 释放村民：手持村民桶，对任意方块右键即可释放村民。释放位置必须安全（非流体且无领地阻拦）。
    Releasing a villager: Right-click a block with a villager bucket to release the villager. The release location must be safe (not in fluid and not blocked by claims).
· 注意事项：村民桶不能用于挤牛奶（对牛或蘑菇牛右键会提示错误），也不能在禁用世界中使用。
    Note: Villager buckets cannot be used to milk cows or mooshrooms (an error message will appear), and cannot be used in disabled worlds.

命令 / Commands

命令 说明
Command Description
/villagerbucket reload 重载插件配置与消息文件
/villagerbucket reload Reload plugin configuration and message files
/villagerbucket info 查看插件运行状态与领地插件支持情况
/villagerbucket info View plugin running status and land claim plugin support
/villagerbucket version 显示当前插件版本
/villagerbucket version Display the current plugin version
/villagerbucket help 查看帮助信息
/villagerbucket help Show help information

权限 / Permissions

权限节点 说明
Permission Node Description
villagerbucket.capture 允许使用空桶捕获村民
villagerbucket.capture Allows capturing villagers with an empty bucket
villagerbucket.release 允许使用村民桶释放村民
villagerbucket.release Allows releasing villagers from a villager bucket
villagerbucket.bypass.claim 绕过领地保护（无视领地权限）
villagerbucket.bypass.claim Bypass land claim protection (ignore claim permissions)

依赖 / Dependencies

· 服务器核心：Folia（或兼容的 Luminol 等 Paper-Folia 分支）
    Server core: Folia (or compatible forks like Luminol).
· 可选依赖：Residence、Dominion 等领地插件（非必需，插件适配后自动启用相关功能）
    Optional dependencies: Residence, Dominion, etc. (not required; the plugin will automatically enable related features if present).

构建 / Build

```bash
mvn -B clean package -Dmaven.test.skip=true -T 8C
```

许可证 / License

本项目采用 GNU General Public License v3.0 开源，详见 [COPYING](COPYING)文件。
This project is open-sourced under the GNU General Public License v3.0. See the [COPYING](COPYING) file for details.
