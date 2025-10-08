package com.ctn.Villager;

import com.ctn.Villager.command.VillagerBucketCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;

public class VillagerBucketPlugin extends JavaPlugin {
    
    private static VillagerBucketPlugin instance;
    private VillagerManager villagerManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 检查插件是否启用
        if (!getConfig().getBoolean("settings.enabled", true)) {
            getLogger().warning("插件在配置中被禁用，将不会启用!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 检查并创建Gson依赖
        if (!checkDependencies()) {
            getLogger().severe("&c插件依赖检查失败，插件将禁用!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化管理器
        this.villagerManager = new VillagerManager(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new VillagerInteractionListener(this), this);
        
        // 注册命令
        VillagerBucketCommand commandExecutor = new VillagerBucketCommand(this);
        getCommand("villagerbucket").setExecutor(commandExecutor);
        getCommand("villagerbucket").setTabCompleter(commandExecutor);
        
        // 记录启用信息
        logEnableInfo();
        
        getLogger().info("&a村民桶插件 v" + getDescription().getVersion() + " 已启用!");
        getLogger().info("&aGitHub: https://github.com/ctnBobong32/VillagerBucket");
    }
    
    @Override
    public void onDisable() {
        if (villagerManager != null) {
            // 清理资源
            villagerManager.cleanup();
        }
        getLogger().info("&c村民桶插件已禁用!");
    }
    
    private boolean checkDependencies() {
        try {
            Class.forName("com.google.gson.Gson");
            getLogger().info("&aGson库已找到，数据序列化功能正常");
            return true;
        } catch (ClassNotFoundException e) {
            getLogger().severe("&c未找到Gson库，请确保服务器已安装Gson!");
            getLogger().severe("&c村民数据保存功能将无法正常工作!");
            return false;
        }
    }
    
    private void logEnableInfo() {
        // 记录配置信息
        boolean checkPermissions = getConfig().getBoolean("settings.check-permissions", true);
        boolean debugMode = getConfig().getBoolean("settings.debug-mode", false);
        boolean saveTrades = getConfig().getBoolean("settings.save-trades", true);
        boolean saveExperience = getConfig().getBoolean("settings.save-experience", true);
        boolean saveRestockInfo = getConfig().getBoolean("settings.save-restock-info", true);
        
        getLogger().info("&e配置信息:");
        getLogger().info("&e- 权限检查: " + (checkPermissions ? "启用" : "禁用"));
        getLogger().info("&e- 调试模式: " + (debugMode ? "启用" : "禁用"));
        getLogger().info("&e- 保存交易: " + (saveTrades ? "启用" : "禁用"));
        getLogger().info("&e- 保存经验: " + (saveExperience ? "启用" : "禁用"));
        getLogger().info("&e- 保存补货信息: " + (saveRestockInfo ? "启用" : "禁用"));
    }
    
    /**
     * 重载插件配置
     */
    public void reloadPluginConfig() {
        reloadConfig();
        getLogger().info("&a配置重载完成!");
    }
    
    /**
     * 获取构建日期
     */
    public String getBuildDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    
    /**
     * 检查调试模式是否启用
     */
    public boolean isDebugMode() {
        return getConfig().getBoolean("settings.debug-mode", false);
    }
    
    /**
     * 调试日志
     */
    public void debug(String message) {
        if (isDebugMode()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    public static VillagerBucketPlugin getInstance() {
        return instance;
    }
    
    public VillagerManager getVillagerManager() {
        return villagerManager;
    }
}