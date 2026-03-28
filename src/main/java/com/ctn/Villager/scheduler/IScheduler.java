package com.ctn.Villager.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface IScheduler {
    boolean isPrimaryThread();
    void runGlobal(Runnable task);
    void runAsync(Runnable task);
    void runAtEntity(Entity entity, Runnable task);
    void runAtLocation(Location location, Runnable task);
    void runAtEntityLater(Entity entity, Runnable task, long delay);
    void runAsyncLater(Runnable task, long delay);
    void runAsyncTimer(Runnable task, long delay, long period);
    void cancelTask(String taskId);
    void cancelTask(int taskId);
    void runLater(Runnable task, long delay);
    void runGlobalLater(Runnable task, long delay);
    void runGlobalTimer(Runnable task, long delay, long period);
    void runAtLocationLater(Location location, Runnable task, long delay);
    void runAtLocationTimer(Location location, Runnable task, long delay, long period);
}