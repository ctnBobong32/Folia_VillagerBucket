package com.ctn.Villager.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * 调度器接口 - 统一Folia和传统Bukkit的调度操作
 */
public interface IScheduler {
    
    /**
     * 在指定位置同步执行任务
     */
    void runAtLocation(Location location, Runnable task);
    
    /**
     * 在指定位置延迟执行任务
     */
    void runAtLocationLater(Location location, Runnable task, long delayTicks);
    
    /**
     * 在指定位置定时执行任务
     */
    Task runAtLocationTimer(Location location, Runnable task, long delayTicks, long periodTicks);
    
    /**
     * 在实体上执行任务
     */
    void runAtEntity(Entity entity, Runnable task);
    
    /**
     * 在实体上延迟执行任务
     */
    void runAtEntityLater(Entity entity, Runnable task, long delayTicks);
    
    /**
     * 全局同步执行任务
     */
    void runGlobal(Runnable task);
    
    /**
     * 全局延迟执行任务
     */
    void runGlobalLater(Runnable task, long delayTicks);
    
    /**
     * 全局定时执行任务
     */
    Task runGlobalTimer(Runnable task, long delayTicks, long periodTicks);
    
    /**
     * 异步执行任务
     */
    void runAsync(Runnable task);
    
    /**
     * 异步延迟执行任务
     */
    void runAsyncLater(Runnable task, long delayTicks);
    
    /**
     * 异步定时执行任务
     */
    Task runAsyncTimer(Runnable task, long delayTicks, long periodTicks);
    
    /**
     * 检查当前是否在主线程
     */
    boolean isPrimaryThread();
    
    /**
     * 取消所有任务
     */
    void cancelAllTasks();
    
    /**
     * 通用任务接口，用于跨平台任务管理
     */
    interface Task {
        void cancel();
        boolean isCancelled();
    }
}