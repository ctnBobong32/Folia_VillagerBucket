package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 调度器管理器 - 自动检测服务器类型并选择合适的调度器
 */
public class SchedulerManager {
    
    private final Plugin plugin;
    private final IScheduler scheduler;
    private final boolean isFolia;
    
    public SchedulerManager(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = detectFolia();
        this.scheduler = createScheduler();
        
        plugin.getLogger().info("使用调度器: " + (isFolia ? "FoliaScheduler" : "BukkitScheduler"));
        
        // 输出Folia特定信息
        if (isFolia) {
            plugin.getLogger().info("Folia调度器已启用，支持区域化调度");
        }
    }
    
    /**
     * 检测服务器是否运行Folia
     */
    private boolean detectFolia() {
        try {
            // 方法1: 检查Folia特定类是否存在
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            plugin.getLogger().info("检测到Folia服务器 - 使用区域化调度器");
            return true;
        } catch (ClassNotFoundException e) {
            // 方法2: 检查Folia特定方法是否存在
            try {
                Bukkit.class.getMethod("getRegionScheduler");
                plugin.getLogger().info("检测到Folia API - 使用区域化调度器");
                return true;
            } catch (NoSuchMethodException ex) {
                plugin.getLogger().info("未检测到Folia - 使用传统Bukkit调度器");
                return false;
            }
        }
    }
    
    /**
     * 创建合适的调度器实例
     */
    private IScheduler createScheduler() {
        if (isFolia) {
            try {
                return new FoliaScheduler(plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("创建Folia调度器失败，回退到Bukkit调度器: " + e.getMessage());
                return new BukkitScheduler(plugin);
            }
        } else {
            return new BukkitScheduler(plugin);
        }
    }
    
    /**
     * 获取调度器实例
     */
    public IScheduler getScheduler() {
        return scheduler;
    }
    
    /**
     * 检查是否为Folia服务器
     */
    public boolean isFolia() {
        return isFolia;
    }
    
    /**
     * 取消所有任务
     */
    public void cancelAllTasks() {
        try {
            scheduler.cancelAllTasks();
            plugin.getLogger().info("已取消所有调度任务");
        } catch (Exception e) {
            plugin.getLogger().warning("取消调度任务时发生错误: " + e.getMessage());
        }
    }
}