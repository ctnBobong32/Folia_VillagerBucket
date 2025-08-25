package com.ctn.Villager;

import org.bukkit.plugin.java.JavaPlugin;

public class VillagerBucketPlugin extends JavaPlugin {
    
    private static VillagerBucketPlugin instance;
    private VillagerManager villagerManager;
    
    @Override
    public void onEnable() {
        instance = this;
        this.villagerManager = new VillagerManager(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new VillagerInteractionListener(this), this);
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 检查并创建Gson依赖
        checkDependencies();
        
        getLogger().info("§a村民桶插件已启用!");
        getLogger().info("§a支持职业: 盔甲匠, 屠夫, 制图师, 牧师, 农民, 渔夫, 制箭师, 皮匠, 图书管理员, 石匠, 傻子, 牧羊人, 工具匠, 武器匠");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("§c村民桶插件已禁用!");
    }
    
    private void checkDependencies() {
        try {
            Class.forName("com.google.gson.Gson");
            getLogger().info("§aGson库已找到，数据序列化功能正常");
        } catch (ClassNotFoundException e) {
            getLogger().severe("§c未找到Gson库，请确保服务器已安装Gson!");
            getLogger().severe("§c村民数据保存功能将无法正常工作!");
        }
    }
    
    public static VillagerBucketPlugin getInstance() {
        return instance;
    }
    
    public VillagerManager getVillagerManager() {
        return villagerManager;
    }
}