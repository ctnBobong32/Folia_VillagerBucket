package com.ctn.Villager.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface IScheduler {
    boolean isPrimaryThread();
    String runGlobal(Runnable task);
    String runAsync(Runnable task);
    String runAtEntity(Entity entity, Runnable task);
    String runAtLocation(Location location, Runnable task);
    String runAtEntityLater(Entity entity, Runnable task, long delay);
    String runAsyncLater(Runnable task, long delay);
    String runAsyncTimer(Runnable task, long delay, long period);
    String runLater(Runnable task, long delay);
    String runGlobalLater(Runnable task, long delay);
    String runGlobalTimer(Runnable task, long delay, long period);
    String runAtLocationLater(Location location, Runnable task, long delay);
    String runAtLocationTimer(Location location, Runnable task, long delay, long period);
    void cancelTask(String taskId);
    void cancelTask(int taskId);
}