package com.ctn.Villager.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class BukkitScheduler implements IScheduler {
    private final Plugin plugin;
    private final SchedulerManager manager;

    public BukkitScheduler(Plugin plugin, SchedulerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void runGlobal(Runnable task) {
        int id = Bukkit.getScheduler().runTask(plugin, task).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAsync(Runnable task) {
        int id = Bukkit.getScheduler().runTaskAsynchronously(plugin, task).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return;
        int id = Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) task.run();
        }).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return;
        int id = Bukkit.getScheduler().runTask(plugin, () -> {
            if (location.getWorld() != null) task.run();
        }).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return;
        int id = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) task.run();
        }, delay).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAsyncLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAsyncTimer(Runnable task, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLater(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runGlobalLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLater(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runGlobalTimer(Runnable task, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period).getTaskId();
        manager.addBukkitTaskId(id);
    }

    @Override
    public void runAtLocationLater(Location location, Runnable task, long delay) {
        runGlobalLater(task, delay);
    }

    @Override
    public void runAtLocationTimer(Location location, Runnable task, long delay, long period) {
        runGlobalTimer(task, delay, period);
    }

    @Override
    public void cancelTask(String taskId) {
        try {
            int id = Integer.parseInt(taskId);
            cancelTask(id);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
        manager.removeBukkitTaskId(taskId);
    }
}