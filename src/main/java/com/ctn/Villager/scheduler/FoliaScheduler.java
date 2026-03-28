package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FoliaScheduler implements IScheduler {
    private final Plugin plugin;
    private final SchedulerManager manager;
    private final ConcurrentHashMap<String, ScheduledTask> taskMap = new ConcurrentHashMap<>();

    public FoliaScheduler(Plugin plugin, SchedulerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void runGlobal(Runnable task) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            try {
                task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAsync(Runnable task) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = entity.getScheduler().run(plugin, t -> {
            try {
                if (entity.isValid()) task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, null);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().run(plugin, location, t -> {
            try {
                if (location.getWorld() != null) task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = entity.getScheduler().runDelayed(plugin, t -> {
            try {
                if (entity.isValid()) task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, null, delay);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAsyncLater(Runnable task, long delay) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
            try {
                task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay * 50, TimeUnit.MILLISECONDS);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAsyncTimer(Runnable task, long delay, long period) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            try {
                task.run();
            } catch (Throwable ex) {
                plugin.getLogger().warning("异步定时任务异常: " + ex.getMessage());
            }
        }, delay * 50, period * 50, TimeUnit.MILLISECONDS);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runLater(Runnable task, long delay) {
        runAsyncLater(task, delay); // Folia 无全局同步延迟，改用异步
    }

    @Override
    public void runGlobalLater(Runnable task, long delay) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            try {
                task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runGlobalTimer(Runnable task, long delay, long period) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            try {
                task.run();
            } catch (Throwable ex) {
                plugin.getLogger().warning("全局定时任务异常: " + ex.getMessage());
            }
        }, delay, period);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAtLocationLater(Location location, Runnable task, long delay) {
        if (location == null || location.getWorld() == null) return;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> {
            try {
                if (location.getWorld() != null) task.run();
            } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void runAtLocationTimer(Location location, Runnable task, long delay, long period) {
        if (location == null || location.getWorld() == null) return;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> {
            try {
                if (location.getWorld() != null) task.run();
            } catch (Throwable ex) {
                plugin.getLogger().warning("区域定时任务异常: " + ex.getMessage());
            }
        }, delay, period);
        taskMap.put(id, scheduledTask);
    }

    @Override
    public void cancelTask(String taskId) {
        ScheduledTask task = taskMap.get(taskId);
        if (task != null) {
            task.cancel();
            taskMap.remove(taskId);
            manager.removeFoliaTaskId(taskId);
        }
    }

    @Override
    public void cancelTask(int taskId) {
        cancelTask(String.valueOf(taskId));
    }
}