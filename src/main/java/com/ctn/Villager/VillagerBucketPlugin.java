package com.ctn.Villager;

import com.ctn.Villager.command.VillagerBucketCommand;
import com.ctn.Villager.scheduler.IScheduler;
import com.ctn.Villager.scheduler.SchedulerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

public class VillagerBucketPlugin extends JavaPlugin {

    private static VillagerBucketPlugin instance;
    private SchedulerManager schedulerManager;
    private IScheduler scheduler;
    private VillagerManager villagerManager;
    private ClaimPluginManager claimPluginManager;
    private VillagerInteractionListener interactionListener;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onLoad() {
        instance = this;
    }
   //注册指令
    @Override
    public void onEnable() {
        this.schedulerManager = new SchedulerManager(this);
        this.scheduler = schedulerManager.getScheduler();
        saveDefaultConfig();
        setupMessagesConfig();
        this.villagerManager = new VillagerManager(this);
        this.interactionListener = new VillagerInteractionListener(this);
        Bukkit.getPluginManager().registerEvents(interactionListener, this);
        VillagerBucketCommand cmd = new VillagerBucketCommand(this);
        PluginCommand command = getCommand("villagerbucket");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        } else {
            getLogger().warning("命令 villagerbucket 未在 plugin.yml 注册，命令功能不可用！");
        }
        scheduleClaimDetectionAfterStartup();
        scheduler.runAsyncLater(new Runnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        }, 40L);
        getLogger().info("村民桶插件已启用 - 支持: " + (schedulerManager.isFolia() ? "Folia多线程核心" : "单线程Bukkit核心"));
        getLogger().info("插件版本: " + getDescription().getVersion());
        getLogger().info("作者: " + String.join(", ", getDescription().getAuthors()));
    }
    
    //取消任务并清理资源
    @Override
    public void onDisable() {
        try {
            if (schedulerManager != null) {
                schedulerManager.cancelAllTasks();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "取消任务时出错", t);
        }
        try {
            if (interactionListener != null) {
                interactionListener.cleanup();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "清理交互监听器时出错", t);
        }
        try {
            if (villagerManager != null) {
                villagerManager.cleanup();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "清理村民管理器时出错", t);
        }
        getLogger().info("村民桶插件已禁用");
    }
    private void scheduleClaimDetectionAfterStartup() {
        scheduler.runGlobalLater(new Runnable() {
            @Override
            public void run() {
                try {
                    claimPluginManager = new ClaimPluginManager(VillagerBucketPlugin.this);
                    scheduler.runGlobalLater(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                claimPluginManager.redetectClaimPlugins();
                                getLogger().info("领地插件支持: " + claimPluginManager.getDetailedStatus());
                            } catch (Throwable t) {
                                getLogger().log(Level.WARNING, "领地插件二次检测失败", t);
                            }
                        }
                    }, 60L);
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "初始化领地插件管理器失败", t);
                    claimPluginManager = null;
                }
            }
        }, 1L);
    }
    
    //消息文件
    private void setupMessagesConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("无法创建插件数据目录: " + getDataFolder().getAbsolutePath());
        }
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try {
                saveResource("messages.yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    if (messagesFile.createNewFile()) {
                        getLogger().info("已创建 messages.yml");
                    }
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "无法创建 messages.yml", e);
                }
            }
        }
        reloadMessagesConfig();
    }
    
   //默认消息文本
    public void reloadMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        setDefaultMessages();
        saveMessagesConfig();
    }

    private void setDefaultMessages() {
        messagesConfig.addDefault("no-permission", "&c你没有权限执行此操作！");
        messagesConfig.addDefault("no-permission-release", "&c你没有权限释放村民！");
        messagesConfig.addDefault("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！");
        messagesConfig.addDefault("no-claim-permission-release", "&c你没有权限在此领地内释放村民！");
        messagesConfig.addDefault("claim-bypass-permission", "&e你已绕过领地限制。");
        messagesConfig.addDefault("captured", "&a成功捕获{0}村民！");
        messagesConfig.addDefault("released", "&a已成功释放{0}村民！");
        messagesConfig.addDefault("invalid-villager", "&c这个村民无效或已死亡，请尝试换一个重试！");
        messagesConfig.addDefault("villager-owned", "&c这个村民已经有主人了！");
        messagesConfig.addDefault("bucket-already-used", "&c这个桶已经装了一个村民！");
        messagesConfig.addDefault("cannot-place-in-fluid", "&c不能将村民放置在流体中！");
        messagesConfig.addDefault("unsafe-spawn-location", "&c这个位置不安全，无法释放村民！");
        messagesConfig.addDefault("world-disabled", "&c这个世界禁止使用村民桶！");
        messagesConfig.addDefault("player-offline", "&c玩家不在线或不存在！");
        messagesConfig.addDefault("inventory-full", "&e物品栏已满，物品已掉落在地上！");
        messagesConfig.addDefault("duplicate-release", "&c请勿重复释放村民！");
        messagesConfig.addDefault("nearby-villager", "&c附近已存在太多村民，请换个位置释放！");
        messagesConfig.addDefault("reloaded", "&a配置已重载！");
        messagesConfig.addDefault("version-info", "&a村民桶插件 &e版本 {0}");
        messagesConfig.addDefault("usage", "&c用法: /villagerbucket [reload|info|version|help]");
        messagesConfig.addDefault("help", Arrays.asList(
                "&6=== 村民桶插件帮助 ===",
                "&e/villagerbucket reload &7- 重载插件配置",
                "&e/villagerbucket info &7- 查看插件信息",
                "&e/villagerbucket version &7- 查看版本信息",
                "&e/villagerbucket help &7- 显示此帮助信息"
        ));
        messagesConfig.addDefault("interaction.use-empty-bucket", "&e请使用空桶来捕获村民！");
        messagesConfig.addDefault("interaction.click-block-to-release", "&e请右键方块来释放村民！");
        messagesConfig.addDefault("interaction.cannot-use-on-animals", "&c村民桶不能用于收集牛奶，请使用空桶！");
        messagesConfig.addDefault("interaction.cannot-use-on-water-mobs", "&e村民桶不能用于捕获水生生物。");
        messagesConfig.addDefault("interaction.general-usage", "&e村民桶只能用于释放村民。");
        messagesConfig.addDefault("interaction.cannot-collect-fluid", "&c村民桶不能用于收集流体！");
        messagesConfig.options().copyDefaults(true);
    }

    public void saveMessagesConfig() {
        if (messagesConfig == null || messagesFile == null) return;
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法保存 messages.yml", e);
        }
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) reloadMessagesConfig();
        return messagesConfig;
    }

    public String getMessage(String path, String defaultValue) {
        if (messagesConfig == null) reloadMessagesConfig();
        String msg = messagesConfig.getString(path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : defaultValue);
    }

    public String getMessage(String path) {
        return getMessage(path, "&c消息未找到: " + path);
    }

    public java.util.List<String> getMessageList(String path) {
        if (messagesConfig == null) reloadMessagesConfig();
        return messagesConfig.getStringList(path);
    }
    
    //还未完工的更新检查QwQ
    private void checkForUpdates() {
        if (!getConfig().getBoolean("settings.check-updates", true)) return;

        try {
            String currentVersion = getDescription().getVersion();
            getLogger().info("当前版本: " + currentVersion);
            getLogger().info("更新检查功能已启用 - 请关注GitHub获取最新版本");
        } catch (Exception e) {
            debug("更新检查失败: " + e.getMessage());
        }
    }

    public void debug(String message) {
        if (getConfig().getBoolean("settings.debug-mode", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public static VillagerBucketPlugin getInstance() {
        return instance;
    }

    public SchedulerManager getSchedulerManager() {
        return schedulerManager;
    }

    public IScheduler getScheduler() {
        return scheduler;
    }

    public VillagerManager getVillagerManager() {
        return villagerManager;
    }

    public ClaimPluginManager getClaimManager() {
        return claimPluginManager;
    }

    public boolean isFolia() {
        return schedulerManager != null && schedulerManager.isFolia();
    }

    public void reloadPluginConfig() {
        reloadConfig();
        reloadMessagesConfig();

        if (claimPluginManager != null) {
            try {
                claimPluginManager.redetectClaimPlugins();
                debug("配置已重载 - 领地插件已重检: " + claimPluginManager.getDetailedStatus());
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "重检领地插件失败", t);
            }
        }

        debug("配置已重载 - 村民管理器已通知");
        getLogger().info("插件配置和消息配置已重载");
    }
}