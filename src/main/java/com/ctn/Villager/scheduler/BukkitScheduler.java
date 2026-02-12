package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class BukkitScheduler implements IScheduler {
    private final Plugin plugin;
    
    public BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
    
    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
    
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            }
        });
    }
    
    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (location.getWorld() != null) {
                task.run();
            }
        });
    }
    
    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            }
        }, delay);
    }
    
    @Override
    public void runAsyncLater(Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
    }
    
    @Override
    public void runAsyncTimer(Runnable task, long delay, long period) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
    }
    
    @Override
    public void runLater(Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
    
    @Override
    public void cancelTask(String taskId) {
        try {
            int id = Integer.parseInt(taskId);
            cancelTask(id);
        } catch (NumberFormatException e) {
        }
    }
    
    @Override
    public void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
    }
}