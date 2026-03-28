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
    public String runGlobal(Runnable task) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            try { task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAsync(Runnable task) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try { task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return null;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = entity.getScheduler().run(plugin, t -> {
            try { if (entity.isValid()) task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, null);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return null;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().run(plugin, location, t -> {
            try { if (location.getWorld() != null) task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        });
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return null;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = entity.getScheduler().runDelayed(plugin, t -> {
            try { if (entity.isValid()) task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, null, delay);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAsyncLater(Runnable task, long delay) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
            try { task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay * 50, TimeUnit.MILLISECONDS);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAsyncTimer(Runnable task, long delay, long period) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            try { task.run(); } catch (Throwable ex) {
                plugin.getLogger().warning("异步定时任务异常: " + ex.getMessage());
            }
        }, delay * 50, period * 50, TimeUnit.MILLISECONDS);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runLater(Runnable task, long delay) {
        return runAsyncLater(task, delay);
    }

    @Override
    public String runGlobalLater(Runnable task, long delay) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            try { task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runGlobalTimer(Runnable task, long delay, long period) {
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            try { task.run(); } catch (Throwable ex) {
                plugin.getLogger().warning("全局定时任务异常: " + ex.getMessage());
            }
        }, delay, period);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAtLocationLater(Location location, Runnable task, long delay) {
        if (location == null || location.getWorld() == null) return null;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> {
            try { if (location.getWorld() != null) task.run(); } finally {
                manager.removeFoliaTaskId(id);
                taskMap.remove(id);
            }
        }, delay);
        taskMap.put(id, scheduledTask);
        return id;
    }

    @Override
    public String runAtLocationTimer(Location location, Runnable task, long delay, long period) {
        if (location == null || location.getWorld() == null) return null;
        String id = java.util.UUID.randomUUID().toString();
        manager.addFoliaTaskId(id);
        ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> {
            try { if (location.getWorld() != null) task.run(); } catch (Throwable ex) {
                plugin.getLogger().warning("区域定时任务异常: " + ex.getMessage());
            }
        }, delay, period);
        taskMap.put(id, scheduledTask);
        return id;
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