Folia_VillagerBucket - 村民桶插件

一款专为 Folia 核心设计的 Minecraft 村民桶插件，允许玩家捕获和释放带有完整交易数据的村民。

功能特点

·  村民捕获：使用空桶右键点击村民即可捕获
·  交易保存：完整保存村民的所有交易数据和等级
·  自定义外观：支持自定义村民桶的物品模型和名称
·  Folia 兼容：完全兼容 Folia 多线程服务器核心
·  轻量高效：优化代码结构，减少服务器资源占用

安装要求

· Minecraft 服务器版本：1.21.8
· 服务器核心：Folia
· Java 版本：21+

安装方法

1. 下载最新版本的 VillagerBucket.jar 文件
2. 将文件放入服务器的 plugins 文件夹
3. 重启服务器
4. 插件会自动生成配置文件

使用方法

捕获村民

1. 手持空桶
2. 右键点击想要捕获的村民
3. 村民会被捕获到桶中，变成一个村民桶物品

释放村民

1. 手持村民桶
2. 右键点击地面
3. 村民会被释放，并保留所有原有的交易数据

权限节点

· villagerbucket.capture - 允许玩家捕获村民（默认所有玩家拥有）
· villagerbucket.release - 允许玩家释放村民（默认所有玩家拥有）
· villagerbucket.admin - 管理员权限，可以重载插件等

配置说明

插件会自动生成 config.yml 配置文件，主要配置项包括：

```yaml
# 村民桶的显示名称
bucket-name: "&6村民桶"

# 自定义模型数据编号
custom-model-data: 1000

# 是否启用权限系统
use-permissions: true

# 是否允许在任何世界使用
enabled-worlds:
  - world
  - world_nether
  - world_the_end
```

命令列表

· /villagerbucket reload - 重载插件配置（需要 villagerbucket.admin 权限）
· /villagerbucket give <玩家> [数量] - 给予玩家空村民桶（需要 villagerbucket.admin 权限）

构建方法

下载源码，在源码对应目录使用以下命令开始编译：

```bash
mvn package -Dmaven.test.skip=true -T 1C -Dmaven.compiler.fork=true
```

此插件使用 Maven 构建，主要依赖：

· Folia API
· Gson (用于数据序列化)

问题反馈

如果您遇到任何问题或有功能建议，请通过以下方式联系：

· GitHub Issues: https://github.com/ctnBobong32/Folia_VillagerBucket/issues
· 邮箱: 3647844952@qq.com

开源协议

本项目采用 MIT 开源协议，详情请查看 LICENSE 文件。

致谢

感谢以下项目提供的技术支持：

· Folia - 多线程服务器核心
· PaperMC - 优秀的 Minecraft 服务器软件

---

注意: 使用本插件前，请确保您的服务器已正确安装并配置 Folia 核心。
