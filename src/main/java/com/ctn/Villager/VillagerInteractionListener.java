package com.ctn.Villager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class VillagerInteractionListener implements Listener {
    private final VillagerBucketPlugin plugin;
    private final VillagerManager villagerManager;
    
    public VillagerInteractionListener(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 确保只处理主手交互
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // 检查是否右键了村民
        if (!(entity instanceof Villager villager)) {
            return;
        }
        
        // 检查玩家手中是否拿着空桶
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.BUCKET || itemInHand.getAmount() < 1) {
            return;
        }
        
        // 检查权限
        if (!player.hasPermission("villagerbucket.capture")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用桶捕获村民!");
            return;
        }
        
        // 检查桶是否已经是村民桶（防止重复放入）
        if (villagerManager.isVillagerBucket(itemInHand)) {
            player.sendMessage(ChatColor.RED + "这个桶已经装了一个村民!");
            return;
        }
        
        // 检查村民是否有效
        if (villager.isDead() || !villager.isValid()) {
            player.sendMessage(ChatColor.RED + "这个村民无效或已死亡!");
            return;
        }
        
        // 取消事件，防止默认行为
        event.setCancelled(true);
        
        // 使用Folia的EntityScheduler来处理村民捕获
        player.getScheduler().execute(plugin, () -> {
            captureVillager(player, villager, itemInHand);
        }, null, 0);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 检查是否是村民桶
        if (!villagerManager.isVillagerBucket(item)) {
            return;
        }
        
        // 检查权限
        if (!player.hasPermission("villagerbucket.release")) {
            player.sendMessage(ChatColor.RED + "你没有权限释放村民!");
            return;
        }
        
        event.setCancelled(true);
        
        // 获取点击的位置
        if (event.getClickedBlock() == null) return;
        
        Block clickedBlock = event.getClickedBlock();
        BlockFace blockFace = event.getBlockFace();
        
        // 计算村民生成位置
        Location spawnLocation = calculateSpawnLocation(clickedBlock, blockFace);
        
        // 使用Folia的RegionScheduler来处理村民释放
        plugin.getServer().getRegionScheduler().execute(plugin, spawnLocation, () -> {
            releaseVillager(player, item, spawnLocation);
        });
    }
    
    private Location calculateSpawnLocation(Block clickedBlock, BlockFace blockFace) {
        // 计算村民生成位置（点击方块相邻的位置中心）
        Location spawnLocation = clickedBlock.getRelative(blockFace).getLocation().add(0.5, 0, 0.5);
        
        // 如果点击的是上方或下方，调整Y坐标
        if (blockFace == BlockFace.UP) {
            spawnLocation.add(0, 1, 0);
        } else if (blockFace == BlockFace.DOWN) {
            spawnLocation.add(0, -1, 0);
        }
        
        return spawnLocation;
    }
    
    private void captureVillager(Player player, Villager villager, ItemStack bucket) {
        try {
            // 检查村民是否仍然有效
            if (villager.isDead() || !villager.isValid()) {
                player.sendMessage(ChatColor.RED + "村民已消失或无效!");
                return;
            }
            
            // 创建村民桶
            ItemStack villagerBucket = villagerManager.createVillagerBucket(villager);
            
            if (villagerBucket == null) {
                player.sendMessage(ChatColor.RED + "创建村民桶失败，数据可能已损坏!");
                return;
            }
            
            // 减少玩家手中的空桶数量
            if (bucket.getAmount() > 1) {
                bucket.setAmount(bucket.getAmount() - 1);
                player.getInventory().addItem(villagerBucket);
            } else {
                player.getInventory().setItemInMainHand(villagerBucket);
            }
            
            // 移除村民
            villager.remove();
            
            // 播放效果
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1.0f, 1.0f);
            player.spawnParticle(Particle.CLOUD, villager.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            
            // 获取中文职业名称
            String professionName = VillagerManager.getProfessionName(villager.getProfession());
            
            // 发送成功消息
            String message = plugin.getConfig().getString("messages.captured", "&a成功捕获" + professionName + "!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "捕获村民时发生错误", e);
            player.sendMessage(ChatColor.RED + "捕获村民时发生错误!");
        }
    }
    
    private void releaseVillager(Player player, ItemStack villagerBucket, Location location) {
        try {
            // 从桶中恢复村民
            Villager villager = villagerManager.restoreVillagerFromBucket(villagerBucket, location);
            
            if (villager == null) {
                return;
            }
            
            // 检查村民是否成功生成
            if (villager.isDead() || !villager.isValid()) {
                player.sendMessage(ChatColor.RED + "村民生成失败!");
                return;
            }
            
            // 减少村民桶数量
            if (villagerBucket.getAmount() > 1) {
                villagerBucket.setAmount(villagerBucket.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            }
            
            // 播放效果
            player.playSound(location, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
            player.spawnParticle(Particle.HEART, location, 5, 0.5, 0.5, 0.5, 0.1);
            
            // 使用公共方法获取中文职业名称
            String professionName = VillagerManager.getProfessionName(villager.getProfession());
            
            // 发送成功消息
            String message = plugin.getConfig().getString("messages.released", "&a已成功释放" + professionName + "!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "释放村民时发生错误", e);
            player.sendMessage(ChatColor.RED + "释放村民时发生错误!");
        }
    }
}