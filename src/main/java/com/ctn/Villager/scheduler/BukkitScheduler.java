package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 传统Bukkit服务器调度器实现
 * 使用标准的Bukkit调度系统
 */
public class BukkitScheduler implements IScheduler {
    
    private final Plugin plugin;
    
    public BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void runAtLocation(Location location, Runnable task) {
        // 传统Bukkit没有区域调度，使用全局同步执行
        runGlobal(task);
    }
    
    @Override
    public void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        // 传统Bukkit没有区域调度，使用全局延迟执行
        runGlobalLater(task, delayTicks);
    }
    
    @Override
    public Task runAtLocationTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        // 传统Bukkit没有区域调度，使用全局定时执行
        return runGlobalTimer(task, delayTicks, periodTicks);
    }
    
    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        // 传统Bukkit没有实体调度，使用全局同步执行
        runGlobal(task);
    }
    
    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        // 传统Bukkit没有实体调度，使用全局延迟执行
        runGlobalLater(task, delayTicks);
    }
    
    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
    
    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    @Override
    public Task runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }
    
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    @Override
    public void runAsyncLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }
    
    @Override
    public Task runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }
    
    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
    
    @Override
    public void cancelAllTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
    
    /**
     * BukkitTask 包装器
     */
    private static class BukkitTaskWrapper implements Task {
        private final BukkitTask bukkitTask;
        
        public BukkitTaskWrapper(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
        }
        
        @Override
        public void cancel() {
            try {
                if (bukkitTask != null) {
                    bukkitTask.cancel();
                }
            } catch (Exception e) {
                // 忽略取消异常
            }
        }
        
        @Override
        public boolean isCancelled() {
            try {
                return bukkitTask != null && bukkitTask.isCancelled();
            } catch (Exception e) {
                return true;
            }
        }
    }
}