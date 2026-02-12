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
    private final ConcurrentHashMap<String, ScheduledTask> taskMap;
    
    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.taskMap = new ConcurrentHashMap<>();
    }
    
    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
    
    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }
    
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }
    
    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return;
        entity.getScheduler().run(plugin, t -> {
            if (entity.isValid()) {
                task.run();
            }
        }, null);
    }
    
    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return;
        Bukkit.getRegionScheduler().run(plugin, location, t -> {
            if (location.getWorld() != null) {
                task.run();
            }
        });
    }
    
    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return;
        entity.getScheduler().runDelayed(plugin, t -> {
            if (entity.isValid()) {
                task.run();
            }
        }, null, delay);
    }
    
    @Override
    public void runAsyncLater(Runnable task, long delay) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), delay * 50, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void runAsyncTimer(Runnable task, long delay, long period) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void runLater(Runnable task, long delay) {
        // Folia中没有直接的runLater，使用异步延迟代替
        runAsyncLater(task, delay);
    }
    
    @Override
    public void cancelTask(String taskId) {
        ScheduledTask task = taskMap.get(taskId);
        if (task != null) {
            task.cancel();
            taskMap.remove(taskId);
        }
    }
    
    @Override
    public void cancelTask(int taskId) {
        cancelTask(String.valueOf(taskId));
    }
}