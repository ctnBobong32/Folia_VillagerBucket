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
    public String runGlobal(Runnable task) {
        int id = Bukkit.getScheduler().runTask(plugin, task).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAsync(Runnable task) {
        int id = Bukkit.getScheduler().runTaskAsynchronously(plugin, task).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAtEntity(Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return null;
        int id = Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) task.run();
        }).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return null;
        int id = Bukkit.getScheduler().runTask(plugin, () -> {
            if (location.getWorld() != null) task.run();
        }).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAtEntityLater(Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return null;
        int id = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) task.run();
        }, delay).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAsyncLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAsyncTimer(Runnable task, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLater(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runGlobalLater(Runnable task, long delay) {
        int id = Bukkit.getScheduler().runTaskLater(plugin, task, delay).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runGlobalTimer(Runnable task, long delay, long period) {
        int id = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period).getTaskId();
        manager.addBukkitTaskId(id);
        return String.valueOf(id);
    }

    @Override
    public String runAtLocationLater(Location location, Runnable task, long delay) {
        return runGlobalLater(task, delay);
    }

    @Override
    public String runAtLocationTimer(Location location, Runnable task, long delay, long period) {
        return runGlobalTimer(task, delay, period);
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