package com.ctn.Villager;

import com.ctn.Villager.command.VillagerBucketCommand;
import com.ctn.Villager.scheduler.SchedulerManager;
import com.ctn.Villager.scheduler.IScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
    
    @Override
    public void onEnable() {
        // 初始化调度器管理器
        this.schedulerManager = new SchedulerManager(this);
        this.scheduler = schedulerManager.getScheduler();
        
        // 保存默认配置
        saveDefaultConfig();
        setupMessagesConfig();
        
        // 初始化管理器
        this.villagerManager = new VillagerManager(this);
        
        // 🆕 初始化领地插件管理器（必须在其他管理器之后）
        this.claimPluginManager = new ClaimPluginManager(this);
        
        // 注册事件监听器
        this.interactionListener = new VillagerInteractionListener(this);
        getServer().getPluginManager().registerEvents(interactionListener, this);
        
        // 注册命令
        VillagerBucketCommand commandExecutor = new VillagerBucketCommand(this);
        getCommand("villagerbucket").setExecutor(commandExecutor);
        getCommand("villagerbucket").setTabCompleter(commandExecutor);
        
        // 检查更新（异步）
        scheduler.runAsyncLater(() -> {
            checkForUpdates();
        }, 20L);
        
        getLogger().info("村民桶插件已启用 - 支持: " + (schedulerManager.isFolia() ? "Folia" : "传统Bukkit"));
        getLogger().info("插件版本: " + getDescription().getVersion());
        getLogger().info("作者: " + String.join(", ", getDescription().getAuthors()));
        
        // 输出领地插件检测状态
        getLogger().info("领地插件支持: " + claimPluginManager.getDetailedStatus());
        
        // 输出调试信息
        if (getConfig().getBoolean("settings.debug-mode", false)) {
            getLogger().info("调试模式已启用");
        }
    }
    
    @Override
    public void onDisable() {
        // 取消所有任务
        if (schedulerManager != null) {
            schedulerManager.cancelAllTasks();
        }
        
        // 清理资源
        if (villagerManager != null) {
            villagerManager.cleanup();
        }
        
        getLogger().info("村民桶插件已禁用");
    }
    
    /**
     * 设置消息配置文件
     */
    private void setupMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        
        if (!messagesFile.exists()) {
            try {
                // 创建数据文件夹
                if (!getDataFolder().exists()) {
                    getDataFolder().mkdirs();
                }
                
                // 从jar中复制默认消息文件
                try (InputStream in = getResource("messages.yml")) {
                    if (in != null) {
                        Files.copy(in, messagesFile.toPath());
                    } else {
                        // 如果没有默认消息文件，创建一个空的
                        messagesFile.createNewFile();
                        getLogger().info("创建默认消息配置文件: messages.yml");
                    }
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法创建消息配置文件", e);
            }
        }
        
        reloadMessagesConfig();
    }
    
    /**
     * 重载消息配置
     */
    public void reloadMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // 设置默认值（如果文件为空）
        setDefaultMessages();
        
        // 保存更新后的配置
        saveMessagesConfig();
    }
    
    /**
     * 设置默认消息
     */
    private void setDefaultMessages() {
        // 权限消息
        messagesConfig.addDefault("no-permission", "&c你没有权限执行此操作！");
        messagesConfig.addDefault("no-permission-release", "&c你没有权限释放村民！");
        
        // 领地权限消息
        messagesConfig.addDefault("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！");
        messagesConfig.addDefault("no-claim-permission-release", "&c你没有权限在此领地内释放村民！");
        messagesConfig.addDefault("claim-bypass-permission", "&e你已绕过领地限制。");
        
        // 操作消息
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
        
        // 命令消息
        messagesConfig.addDefault("reloaded", "&a配置已重载！");
        messagesConfig.addDefault("give-success", "&a已成功给予 {0} 一个村民桶！");
        messagesConfig.addDefault("version-info", "&a村民桶插件 &e版本 {0}");
        messagesConfig.addDefault("usage", "&c用法: /villagerbucket [reload|give|info|version|help]");
        
        // 帮助消息
        messagesConfig.addDefault("help", Arrays.asList(
            "&6=== 村民桶插件帮助 ===",
            "&e/villagerbucket give <玩家> [职业] [类型] [等级] &7- 给予玩家村民桶",
            "&e/villagerbucket reload &7- 重载插件配置", 
            "&e/villagerbucket info &7- 查看插件信息",
            "&e/villagerbucket version &7- 查看版本信息",
            "&e/villagerbucket help &7- 显示此帮助信息"
        ));
        
        // 交互提示消息
        messagesConfig.addDefault("interaction.use-empty-bucket", "&e请使用空桶来捕获村民！");
        messagesConfig.addDefault("interaction.click-block-to-release", "&e请右键方块来释放村民！");
        messagesConfig.addDefault("interaction.cannot-use-on-animals", "&c村民桶不能用于收集牛奶，请使用空桶！");
        messagesConfig.addDefault("interaction.cannot-use-on-water-mobs", "&e村民桶不能用于捕获水生生物。");
        messagesConfig.addDefault("interaction.general-usage", "&e村民桶只能用于释放村民。");
        messagesConfig.addDefault("interaction.cannot-collect-fluid", "&c村民桶不能用于收集流体！");
        
        messagesConfig.options().copyDefaults(true);
    }
    
    /**
     * 保存消息配置
     */
    public void saveMessagesConfig() {
        if (messagesConfig == null || messagesFile == null) {
            return;
        }
        
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法保存消息配置文件", e);
        }
    }
    
    /**
     * 获取消息配置
     */
    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            reloadMessagesConfig();
        }
        return messagesConfig;
    }
    
    /**
     * 获取格式化消息
     */
    public String getMessage(String path, String defaultValue) {
        if (messagesConfig == null) {
            reloadMessagesConfig();
        }
        
        String message = messagesConfig.getString(path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', message != null ? message : defaultValue);
    }
    
    /**
     * 获取格式化消息
     */
    public String getMessage(String path) {
        return getMessage(path, "&c消息未找到: " + path);
    }
    
    /**
     * 获取消息列表
     */
    public java.util.List<String> getMessageList(String path) {
        if (messagesConfig == null) {
            reloadMessagesConfig();
        }
        return messagesConfig.getStringList(path);
    }
    
    /**
     * 检查更新
     */
    private void checkForUpdates() {
        if (!getConfig().getBoolean("settings.check-updates", true)) {
            return;
        }
        
        try {
            // 这里可以添加实际的更新检查逻辑
            // 目前只是模拟
            String currentVersion = getDescription().getVersion();
            getLogger().info("当前版本: " + currentVersion);
            getLogger().info("更新检查功能已启用 - 请关注GitHub获取最新版本");
            
        } catch (Exception e) {
            debug("更新检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 调试输出
     */
    public void debug(String message) {
        if (getConfig().getBoolean("settings.debug-mode", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * 获取插件实例
     */
    public static VillagerBucketPlugin getInstance() {
        return instance;
    }
    
    /**
     * 获取调度器管理器
     */
    public SchedulerManager getSchedulerManager() {
        return schedulerManager;
    }
    
    /**
     * 获取调度器实例
     */
    public IScheduler getScheduler() {
        return scheduler;
    }
    
    /**
     * 获取村民管理器
     */
    public VillagerManager getVillagerManager() {
        return villagerManager;
    }
    
    /**
     * 获取领地插件管理器
     */
    public ClaimPluginManager getClaimManager() {
        return claimPluginManager;
    }
    
    /**
     * 检查是否为Folia服务器
     */
    public boolean isFolia() {
        return schedulerManager != null && schedulerManager.isFolia();
    }
    
    /**
     * 重载插件配置
     */
    public void reloadPluginConfig() {
        reloadConfig();
        reloadMessagesConfig();
        
        // 重新检测领地插件
        if (claimPluginManager != null) {
            // 这里可以添加领地插件管理器的重载逻辑
            debug("配置已重载 - 领地插件管理器已通知");
        }
        
        // 通知其他组件配置已重载
        if (villagerManager != null) {
            // 村民管理器可以在这里处理配置重载
            debug("配置已重载 - 村民管理器已通知");
        }
        
        getLogger().info("插件配置和消息配置已重载");
    }
}