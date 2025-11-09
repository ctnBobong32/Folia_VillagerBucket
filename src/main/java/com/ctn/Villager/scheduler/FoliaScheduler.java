package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Folia服务器调度器实现
 * 使用Folia的区域化调度系统 - 修复版本
 */
public class FoliaScheduler implements IScheduler {
    
    private final Plugin plugin;
    
    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("尝试在无效位置执行任务");
            runGlobal(task);
            return;
        }
        
        try {
            // 使用Folia的RegionScheduler在指定位置执行任务
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia location scheduler failed: " + e.getMessage());
            // 对于关键操作，记录更详细的信息
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                plugin.getLogger().info("失败位置: " + location + ", 世界: " + location.getWorld().getName());
            }
            
            // 根据配置决定是否回退到全局调度
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                runGlobal(task);
            }
        }
    }
    
    @Override
    public void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("尝试在无效位置执行延迟任务");
            runGlobalLater(task, delayTicks);
            return;
        }
        
        try {
            // 使用Folia的RegionScheduler延迟执行
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia location delayed scheduler failed, falling back to global: " + e.getMessage());
            
            // 根据配置决定是否回退到全局调度
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                runGlobalLater(task, delayTicks);
            }
        }
    }
    
    @Override
    public Task runAtLocationTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("尝试在无效位置执行定时任务");
            return runGlobalTimer(task, delayTicks, periodTicks);
        }
        
        try {
            // 使用Folia的RegionScheduler定时执行
            io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask = 
                Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, scheduledTask -> task.run(), delayTicks, periodTicks);
            
            return new FoliaTaskWrapper(foliaTask);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia location timer scheduler failed, falling back to global: " + e.getMessage());
            
            // 根据配置决定是否回退到全局调度
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                return runGlobalTimer(task, delayTicks, periodTicks);
            } else {
                return new FoliaTaskWrapper(null); // 返回空任务包装器
            }
        }
    }
    
    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) {
            plugin.getLogger().warning("尝试在无效实体上执行任务");
            return;
        }
        
        try {
            // 使用Folia的EntityScheduler在实体上执行任务
            entity.getScheduler().execute(plugin, task, null, 0L);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity scheduler failed: " + e.getMessage());
            
            // 回退到全局执行但添加警告
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                plugin.getLogger().warning("回退到全局调度可能引起线程问题，请及时处理");
                runGlobal(task);
            }
        }
    }
    
    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null || !entity.isValid()) {
            plugin.getLogger().warning("尝试在无效实体上执行延迟任务");
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                runGlobalLater(task, delayTicks);
            }
            return;
        }
        
        try {
            // 使用Folia的EntityScheduler延迟执行
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity delayed scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                runGlobalLater(task, delayTicks);
            }
        }
    }
    
    @Override
    public void runGlobal(Runnable task) {
        try {
            // 使用Folia的GlobalRegionScheduler执行全局任务
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia global scheduler failed: " + e.getMessage());
            // 最终回退方案
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }
    
    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        try {
            // 使用Folia的GlobalRegionScheduler延迟执行
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia global delayed scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        }
    }
    
    @Override
    public Task runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            // 使用Folia的GlobalRegionScheduler定时执行
            io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask = 
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
            
            return new FoliaTaskWrapper(foliaTask);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia global timer scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                return runGlobalTimerFallback(task, delayTicks, periodTicks);
            } else {
                return new FoliaTaskWrapper(null);
            }
        }
    }
    
    @Override
    public void runAsync(Runnable task) {
        try {
            // 使用Folia的AsyncScheduler执行异步任务
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } catch (Exception e) {
            plugin.getLogger().warning("Folia async scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        }
    }
    
    @Override
    public void runAsyncLater(Runnable task, long delayTicks) {
        try {
            // 使用Folia的AsyncScheduler延迟执行异步任务
            long delayMillis = ticksToMillis(delayTicks);
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia async delayed scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            }
        }
    }
    
    @Override
    public Task runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            // 使用Folia的AsyncScheduler定时执行异步任务
            long delayMillis = ticksToMillis(delayTicks);
            long periodMillis = ticksToMillis(periodTicks);
            
            io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask = 
                Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayMillis, periodMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            return new FoliaTaskWrapper(foliaTask);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia async timer scheduler failed: " + e.getMessage());
            
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                return runAsyncTimerFallback(task, delayTicks, periodTicks);
            } else {
                return new FoliaTaskWrapper(null);
            }
        }
    }
    
    @Override
    public boolean isPrimaryThread() {
        try {
            // Folia的线程检查
            return Bukkit.isPrimaryThread();
        } catch (Exception e) {
            // 回退到传统检查
            return Bukkit.getServer().isPrimaryThread();
        }
    }
    
    @Override
    public void cancelAllTasks() {
        try {
            // 取消Folia的所有任务
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia cancel tasks failed: " + e.getMessage());
            // 回退到传统取消方式
            if (plugin.getConfig().getBoolean("settings.folia.fallback-global-scheduler", true)) {
                Bukkit.getScheduler().cancelTasks(plugin);
            }
        }
    }
    
    /**
     * 将游戏刻转换为毫秒
     */
    private long ticksToMillis(long ticks) {
        return ticks * 50L; // 1 tick = 50ms
    }
    
    /**
     * 回退方法：使用传统Bukkit调度器
     */
    private Task runGlobalTimerFallback(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }
    
    /**
     * 回退方法：使用传统Bukkit异步调度器
     */
    private Task runAsyncTimerFallback(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }
    
    /**
     * Folia ScheduledTask 包装器
     */
    private static class FoliaTaskWrapper implements Task {
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask;
        
        public FoliaTaskWrapper(io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask) {
            this.foliaTask = foliaTask;
        }
        
        @Override
        public void cancel() {
            try {
                if (foliaTask != null) {
                    foliaTask.cancel();
                }
            } catch (Exception e) {
                // 忽略取消异常
            }
        }
        
        @Override
        public boolean isCancelled() {
            try {
                return foliaTask != null && foliaTask.isCancelled();
            } catch (Exception e) {
                return true;
            }
        }
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