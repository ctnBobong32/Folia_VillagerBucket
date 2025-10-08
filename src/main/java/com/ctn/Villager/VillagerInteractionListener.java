package com.ctn.Villager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class VillagerInteractionListener implements Listener {
    private final VillagerBucketPlugin plugin;
    private final VillagerManager villagerManager;
    
    // 音效映射表
    private static final Map<String, Sound> SOUND_MAPPING = createSoundMapping();
    
    public VillagerInteractionListener(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
    }
    
    /**
     * 创建音效映射表
     */
    private static Map<String, Sound> createSoundMapping() {
        Map<String, Sound> mapping = new HashMap<>();
        
        // 核心音效映射
        addSoundSafe(mapping, "ITEM_BUCKET_FILL", Sound.ITEM_BUCKET_FILL);
        addSoundSafe(mapping, "ITEM_BUCKET_EMPTY", Sound.ITEM_BUCKET_EMPTY);
        addSoundSafe(mapping, "ENTITY_VILLAGER_YES", Sound.ENTITY_VILLAGER_YES);
        addSoundSafe(mapping, "ENTITY_VILLAGER_NO", Sound.ENTITY_VILLAGER_NO);
        addSoundSafe(mapping, "ENTITY_VILLAGER_TRADE", Sound.ENTITY_VILLAGER_TRADE);
        addSoundSafe(mapping, "ENTITY_VILLAGER_AMBIENT", Sound.ENTITY_VILLAGER_AMBIENT);
        addSoundSafe(mapping, "UI_BUTTON_CLICK", Sound.UI_BUTTON_CLICK);
        addSoundSafe(mapping, "ENTITY_PLAYER_LEVELUP", Sound.ENTITY_PLAYER_LEVELUP);
        
        return mapping;
    }
    
    private static void addSoundSafe(Map<String, Sound> mapping, String key, Sound sound) {
        try {
            mapping.put(key, sound);
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 确保只处理主手交互
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        
        // 检查玩家是否手持村民桶
        if (villagerManager.isVillagerBucket(itemInHand)) {
            // 取消所有实体交互，防止产生牛奶桶等
            event.setCancelled(true);
            
            // 如果是村民，尝试捕获
            if (entity instanceof Villager villager) {
                handleVillagerCapture(player, villager, itemInHand);
            } else if (entity instanceof Cow || entity instanceof MushroomCow) {
                // 特别处理牛和哞菇，防止牛奶桶bug
                sendMessage(player, "&c村民桶不能用于收集牛奶！");
                plugin.debug("阻止玩家 " + player.getName() + " 使用村民桶与动物交互");
            } else {
                // 其他实体给出提示
                handleOtherEntityInteraction(player, entity);
            }
            return;
        }
        
        // 检查是否右键了村民且手持空桶
        if (!(entity instanceof Villager villager)) {
            return;
        }
        
        // 检查玩家手中是否拿着空桶
        if (itemInHand == null || itemInHand.getType() != Material.BUCKET || itemInHand.getAmount() < 1) {
            return;
        }
        
        // 检查桶是否已经是村民桶（防止重复放入）
        if (villagerManager.isVillagerBucket(itemInHand)) {
            sendMessage(player, plugin.getConfig().getString("messages.bucket-already-used", 
                "&c这个桶已经装了一个村民！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.capture", "villagerbucket.capture"))) {
            sendMessage(player, plugin.getConfig().getString("messages.no-permission", 
                "&c你没有权限执行此操作，请尝试获得权限后重试！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查村民是否有效
        if (villager.isDead() || !villager.isValid()) {
            sendMessage(player, plugin.getConfig().getString("messages.invalid-villager", 
                "&c这个村民无效或已死亡，请尝试换一个重试！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(villager.getWorld())) {
            sendMessage(player, plugin.getConfig().getString("messages.world-disabled", 
                "&c这个世界禁止使用村民桶！"));
            event.setCancelled(true);
            return;
        }
        
        // 取消事件，防止默认行为
        event.setCancelled(true);
        
        // 捕获村民
        captureVillager(player, villager, itemInHand);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理右键事件
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && 
            event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        
        // 检查是否为主手交互
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 检查是否是村民桶
        if (!villagerManager.isVillagerBucket(item)) {
            return;
        }
        
        // 立即取消事件，防止所有默认交互（包括取水、取岩浆等）
        event.setCancelled(true);
        
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.release", "villagerbucket.release"))) {
            sendMessage(player, plugin.getConfig().getString("messages.no-permission-release", 
                "&c你没有权限释放村民！"));
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(player.getWorld())) {
            sendMessage(player, plugin.getConfig().getString("messages.world-disabled", 
                "&c这个世界禁止使用村民桶！"));
            return;
        }
        
        // 如果是右键空气，给出提示
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            sendMessage(player, "&e请右键方块来释放村民！");
            return;
        }
        
        // 获取点击的位置
        if (event.getClickedBlock() == null) return;
        
        Block clickedBlock = event.getClickedBlock();
        BlockFace blockFace = event.getBlockFace();
        
        // 检查点击的方块是否是流体或流体容器，如果是则直接拒绝
        if (isFluid(clickedBlock.getType()) || isFluidContainer(clickedBlock.getType())) {
            sendMessage(player, plugin.getConfig().getString("messages.cannot-place-in-fluid", 
                "&c村民桶不能用于收集流体！"));
            plugin.debug("阻止玩家 " + player.getName() + " 使用村民桶与流体交互");
            return;
        }
        
        // 计算村民生成位置 - 简化的位置计算，移除严格检测
        Location spawnLocation = calculateSpawnLocation(clickedBlock, blockFace);
        
        // 简化的安全性检查 - 只检查是否在流体中
        if (isInFluid(spawnLocation)) {
            sendMessage(player, plugin.getConfig().getString("messages.cannot-place-in-fluid", 
                "&c不能将村民放置在流体中！"));
            return;
        }
        
        // 移除严格的安全位置检查，允许在更多位置放置
        
        // 释放村民
        releaseVillager(player, item, spawnLocation);
    }
    
    private void handleVillagerCapture(Player player, Villager villager, ItemStack villagerBucket) {
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.capture", "villagerbucket.capture"))) {
            sendMessage(player, plugin.getConfig().getString("messages.no-permission", 
                "&c你没有权限执行此操作，请尝试获得权限后重试！"));
            return;
        }
        
        // 检查村民是否有效
        if (villager.isDead() || !villager.isValid()) {
            sendMessage(player, plugin.getConfig().getString("messages.invalid-villager", 
                "&c这个村民无效或已死亡，请尝试换一个重试！"));
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(villager.getWorld())) {
            sendMessage(player, plugin.getConfig().getString("messages.world-disabled", 
                "&c这个世界禁止使用村民桶！"));
            return;
        }
        
        // 玩家手持村民桶时右键村民，提示需要空桶
        sendMessage(player, "&e请使用空桶来捕获村民！");
    }
    
    private void handleOtherEntityInteraction(Player player, Entity entity) {
        // 根据实体类型给出适当的提示
        if (entity instanceof Cow || entity instanceof MushroomCow) {
            sendMessage(player, "&c村民桶不能用于收集牛奶，请使用空桶！");
        } else if (entity instanceof WaterMob) {
            sendMessage(player, "&e村民桶不能用于捕获水生生物。");
        } else {
            sendMessage(player, "&e村民桶只能用于释放村民。");
        }
    }
    
    /**
     * 发送消息的辅助方法
     */
    private void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    /**
     * 检查世界是否被禁用
     */
    private boolean isWorldDisabled(World world) {
        if (world == null) return false;
        List<String> disabledWorlds = plugin.getConfig().getStringList("settings.disabled-worlds");
        return disabledWorlds.contains(world.getName());
    }
    
    /**
     * 简化的位置安全性检查 - 移除严格检测
     */
    private boolean isSafeSpawnLocation(Location location) {
        Block spawnBlock = location.getBlock();
        Block blockBelow = spawnBlock.getRelative(BlockFace.DOWN);
        
        // 只检查下方是否是固体方块和当前位置是否不是流体
        return blockBelow.getType().isSolid() && !isFluid(spawnBlock.getType());
    }
    
    // 精确的流体检查 - 只检查水和岩浆
    private boolean isFluid(Material material) {
        return material == Material.WATER || 
               material == Material.LAVA ||
               material == Material.BUBBLE_COLUMN;
    }
    
    // 检查是否为流体容器
    private boolean isFluidContainer(Material material) {
        return material == Material.WATER_CAULDRON ||
               material == Material.LAVA_CAULDRON ||
               material == Material.POWDER_SNOW_CAULDRON;
    }
    
    // 检查位置是否处于流体中
    private boolean isInFluid(Location location) {
        Block spawnBlock = location.getBlock();
        return isFluid(spawnBlock.getType()) || spawnBlock.isLiquid();
    }
    
    private Location calculateSpawnLocation(Block clickedBlock, BlockFace blockFace) {
        // 简化的位置计算 - 直接在点击的方块面上生成
        return clickedBlock.getRelative(blockFace).getLocation().add(0.5, 0, 0.5);
    }
    
    private void captureVillager(Player player, Villager villager, ItemStack bucket) {
        try {
            // 再次检查村民是否仍然有效（防止并发修改）
            if (villager.isDead() || !villager.isValid()) {
                sendMessage(player, plugin.getConfig().getString("messages.invalid-villager", 
                    "&c这个村民无效或已死亡，请尝试换一个重试！"));
                return;
            }
            
            // 检查村民是否已经有主人（防止捕获其他插件的特殊村民）
            if (hasOwner(villager)) {
                sendMessage(player, plugin.getConfig().getString("messages.villager-owned", 
                    "&c这个村民已经有主人了！"));
                return;
            }
            
            // 创建村民桶
            ItemStack villagerBucket = villagerManager.createVillagerBucket(villager);
            
            if (villagerBucket == null) {
                sendMessage(player, "&c创建村民桶失败，数据可能已损坏!");
                return;
            }
            
            // 处理物品栏逻辑
            if (bucket.getAmount() > 1) {
                // 减少手中的空桶数量
                bucket.setAmount(bucket.getAmount() - 1);
                
                // 尝试添加村民桶到物品栏
                if (player.getInventory().addItem(villagerBucket).isEmpty()) {
                    // 成功添加
                } else {
                    // 物品栏满，掉落村民桶
                    player.getWorld().dropItemNaturally(player.getLocation(), villagerBucket);
                    sendMessage(player, plugin.getConfig().getString("messages.inventory-full", 
                        "&e物品栏已满，物品已掉落在地上！"));
                }
            } else {
                // 直接替换手中的桶
                player.getInventory().setItemInMainHand(villagerBucket);
            }
            
            // 移除村民
            villager.remove();
            
            // 播放效果
            playCaptureEffects(player.getLocation());
            
            // 获取中文职业名称
            String professionName = VillagerManager.getProfessionName(villager.getProfession());
            
            // 发送成功消息
            String messageTemplate = plugin.getConfig().getString("messages.captured", "&a成功捕获{0}村民！");
            String formattedMessage = MessageFormat.format(messageTemplate, professionName);
            sendMessage(player, formattedMessage);
            
            plugin.debug("玩家 " + player.getName() + " 成功捕获村民: " + professionName);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "捕获村民时发生错误", e);
            sendMessage(player, "&c捕获村民时发生错误!");
        }
    }
    
    /**
     * 检查村民是否已经有主人
     */
    private boolean hasOwner(Villager villager) {
        try {
            // 检查村民是否有自定义名称（可能表示有主人）
            if (villager.getCustomName() != null && !villager.getCustomName().isEmpty()) {
                return true;
            }
            
            // 检查是否有特定的PDC标签（其他插件可能使用）
            PersistentDataContainer pdc = villager.getPersistentDataContainer();
            for (NamespacedKey key : pdc.getKeys()) {
                if (key.getKey().contains("owner") || key.getKey().contains("own")) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            plugin.debug("检查村民所有权时出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 播放捕获效果
     */
    private void playCaptureEffects(Location location) {
        Sound sound = getSoundByName(plugin.getConfig().getString("sounds.capture", "ITEM_BUCKET_FILL"));
        if (sound != null) {
            try {
                location.getWorld().playSound(location, sound, 1.0f, 1.0f);
            } catch (Exception e) {
                plugin.debug("播放捕获音效失败: " + e.getMessage());
            }
        }
        
        // 播放粒子效果
        if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
            try {
                location.getWorld().spawnParticle(Particle.SMOKE, location, 10, 0.5, 0.5, 0.5, 0.02);
            } catch (Exception e) {
                // 忽略粒子错误
            }
        }
    }
    
    /**
     * 播放释放效果
     */
    private void playReleaseEffects(Location location) {
        Sound sound = getSoundByName(plugin.getConfig().getString("sounds.release", "ITEM_BUCKET_EMPTY"));
        if (sound != null) {
            try {
                location.getWorld().playSound(location, sound, 1.0f, 1.0f);
            } catch (Exception e) {
                plugin.debug("播放释放音效失败: " + e.getMessage());
            }
        }
        
        // 播放粒子效果
        if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
            try {
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 15, 1, 1, 1, 0.1);
            } catch (Exception e) {
                // 忽略粒子错误
            }
        }
    }
    
    /**
     * 通过名称获取音效
     */
    private Sound getSoundByName(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return Sound.ITEM_BUCKET_FILL;
        }
        
        try {
            String upperName = soundName.toUpperCase();
            Sound sound = SOUND_MAPPING.get(upperName);
            
            if (sound != null) {
                return sound;
            }
            
            plugin.debug("未知音效名称: " + soundName);
            return Sound.ITEM_BUCKET_FILL;
            
        } catch (Exception e) {
            plugin.debug("获取音效失败: " + soundName + " - " + e.getMessage());
            return Sound.ITEM_BUCKET_FILL;
        }
    }
    
    private void releaseVillager(Player player, ItemStack villagerBucket, Location location) {
        try {
            // 只检查是否处于流体方块
            if (isInFluid(location)) {
                sendMessage(player, plugin.getConfig().getString("messages.cannot-place-in-fluid", 
                    "&c不能将村民放置在流体中！"));
                return;
            }
            
            // 验证村民桶数据完整性
            if (!villagerManager.isValidVillagerBucket(villagerBucket)) {
                plugin.getLogger().warning("村民桶数据验证失败: " + villagerBucket.toString());
                sendMessage(player, "&c村民桶数据不完整或已损坏，无法释放村民!");
                return;
            }
            
            // 恢复村民
            Villager villager = villagerManager.restoreVillagerFromBucket(villagerBucket, location);
            
            if (villager == null || villager.isDead() || !villager.isValid()) {
                plugin.getLogger().severe("释放村民失败: 村民生成后无效或为空");
                sendMessage(player, "&c释放村民失败，请尝试在其他位置放置!");
                return;
            }
            
            // 处理物品栏逻辑
            ItemStack emptyBucket = new ItemStack(Material.BUCKET, 1);
            if (villagerBucket.getAmount() > 1) {
                // 减少村民桶数量
                villagerBucket.setAmount(villagerBucket.getAmount() - 1);
                
                // 尝试添加空桶到物品栏
                if (player.getInventory().addItem(emptyBucket).isEmpty()) {
                    // 成功添加
                } else {
                    // 物品栏满，掉落空桶
                    player.getWorld().dropItemNaturally(player.getLocation(), emptyBucket);
                    sendMessage(player, plugin.getConfig().getString("messages.inventory-full", 
                        "&e物品栏已满，物品已掉落在地上！"));
                }
            } else {
                // 直接替换手中的村民桶为空桶
                player.getInventory().setItemInMainHand(emptyBucket);
            }
            
            // 播放效果
            playReleaseEffects(location);
            
            // 使用final变量修复lambda表达式问题
            final Villager finalVillager = villager;
            final String finalProfessionName = VillagerManager.getProfessionName(villager.getProfession());
            
            // 延迟发送成功消息
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // 再次验证村民状态
                    if (finalVillager.isDead() || !finalVillager.isValid()) {
                        sendMessage(player, "&e村民已释放，但可能遇到了问题。");
                        return;
                    }
                    
                    // 发送成功消息
                    String messageTemplate = plugin.getConfig().getString("messages.released", "&a已成功释放{0}村民！");
                    String formattedMessage = MessageFormat.format(messageTemplate, finalProfessionName);
                    sendMessage(player, formattedMessage);
                    
                    plugin.debug("玩家 " + player.getName() + " 成功释放村民: " + finalProfessionName);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "发送释放消息时发生错误:", e);
                    sendMessage(player, "&a村民已成功释放!");
                }
            }, 5L);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "释放村民时发生严重错误", e);
            sendMessage(player, "&c释放村民时发生错误，请尝试重新放置或联系管理员!");
        }
    }
}