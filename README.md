Folia_VillagerBucket - 村民桶插件

一款专为 Folia 核心设计的 Minecraft 村民桶插件，支持完整捕获与释放村民，并保留所有交易数据。

---

功能特点

· 村民捕获：手持空桶右键村民即可捕获，村民变为村民桶物品
· 交易保存：完整存储村民的职业、等级、交易内容及经验值
· 自定义外观：支持自定义村民桶的物品名称、Lore 及模型数据
· Folia 兼容：完全适配 Folia 多线程架构，线程安全
· 轻量高效：代码精简，占用低，兼容主流服务端

---

安装要求

· Minecraft 版本：1.21+
· 服务端核心：Folia 或兼容 Bukkit/Paper 的其他核心
· Java 版本：21 或更高

---

安装方法

1. 下载最新版本的 VillagerBucket.jar
2. 将文件放入服务端 plugins/ 目录
3. 重启服务器或加载插件
4. 首次运行自动生成配置文件 config.yml

---

使用方法

捕获村民

· 手持空桶，右键点击村民 → 村民消失，获得对应的村民桶

释放村民

· 手持村民桶，右键点击地面 → 村民出现，保留原交易数据

---

权限节点

权限节点 描述 默认
villagerbucket.capture 允许捕获村民 所有玩家
villagerbucket.release 允许释放村民 所有玩家

---

配置示例（config.yml）

```yaml
# 村民桶显示名称（支持颜色代码）
bucket-name: "&6村民桶"

# 自定义模型数据编号（需资源包配合）
custom-model-data: 1000

# 是否启用权限检查
use-permissions: true

# 允许使用村民桶的世界
enabled-worlds:
  - world
  - world_nether
  - world_the_end
```

---

命令列表

· /villagerbucket reload — 重载配置（权限：villagerbucket.admin）

---

构建方法

克隆源码后，在项目根目录执行：

```bash
mvn package -Dmaven.test.skip=true -T 8C -Dmaven.compiler.fork=true
```

构建依赖：

· Folia API
· Gson（交易数据序列化）

---

问题反馈

· GitHub Issues：https://github.com/ctnBobong32/Folia_VillagerBucket/issues
· 邮箱：<3647844952@qq.com>

---

开源协议

本项目基于 MIT 许可证 开源，详情参见 LICENSE 文件。

---

致谢

· https://github.com/PaperMC/Folia —— 多线程服务端核心
· https://papermc.io —— 高性能 Minecraft 服务端软件