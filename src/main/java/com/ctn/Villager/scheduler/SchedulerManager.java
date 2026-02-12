package com.ctn.Villager.scheduler;

import com.ctn.Villager.VillagerBucketPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SchedulerManager {
    private final Plugin plugin;
    private final IScheduler scheduler;
    private final boolean isFolia;
    private final Set<Integer> bukkitTaskIds = new HashSet<>();
    private final Set<String> foliaTaskIds = ConcurrentHashMap.newKeySet();
    
    public SchedulerManager(Plugin plugin) {
        this.plugin = plugin;
        
        boolean foliaDetected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException e) {
            foliaDetected = false;
        }
        
        this.isFolia = foliaDetected;
        
        if (isFolia) {
            this.scheduler = new FoliaScheduler(plugin);
        } else {
            this.scheduler = new BukkitScheduler(plugin);
        }
    }
    
    public IScheduler getScheduler() {
        return scheduler;
    }
    
    public boolean isFolia() {
        return isFolia;
    }
    
    public void addBukkitTaskId(int taskId) {
        bukkitTaskIds.add(taskId);
    }

    public void addFoliaTaskId(String taskId) {
        foliaTaskIds.add(taskId);
    }
    
    public void removeBukkitTaskId(int taskId) {
        bukkitTaskIds.remove(taskId);
    }
    
    public void removeFoliaTaskId(String taskId) {
        foliaTaskIds.remove(taskId);
    }
    
    public void cancelAllTasks() {
        if (isFolia) {
            for (String taskId : foliaTaskIds) {
                try {
                    scheduler.cancelTask(taskId);
                } catch (Exception e) {
                }
            }
            foliaTaskIds.clear();
        } else {
            for (int taskId : bukkitTaskIds) {
                try {
                    scheduler.cancelTask(taskId);
                } catch (Exception e) {
                }
            }
            bukkitTaskIds.clear();
            
            Bukkit.getScheduler().cancelTasks(plugin);
        }
        
        if (plugin instanceof VillagerBucketPlugin) {
            ((VillagerBucketPlugin) plugin).debug("已取消所有调度任务");
        }
    }
    
    public int getActiveTaskCount() {
        if (isFolia) {
            return foliaTaskIds.size();
        } else {
            return bukkitTaskIds.size();
        }
    }
    
    public void cleanupCompletedTasks() {
        if (isFolia) {
            foliaTaskIds.removeIf(taskId -> {
                try {
                    return false;
                } catch (Exception e) {
                    return true;
                }
            });
        }
    }
}