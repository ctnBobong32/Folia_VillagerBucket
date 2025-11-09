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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

public class VillagerInteractionListener implements Listener {
    private final VillagerBucketPlugin plugin;
    private final VillagerManager villagerManager;
    private final ClaimPluginManager claimManager;
    
    // 音效映射表
    private static final Map<String, Sound> SOUND_MAPPING = createSoundMapping();
    
    // 防止重复生成的标记 - 使用玩家UUID和物品UUID作为键
    private final Map<String, Long> releaseCooldowns = new HashMap<>();
    private final Map<UUID, Long> playerReleaseCooldowns = new HashMap<>();
    private final Map<String, Boolean> processingReleases = new HashMap<>();
    
    public VillagerInteractionListener(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
        this.claimManager = plugin.getClaimManager();
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
            // 立即取消所有实体交互，防止产生牛奶桶等
            event.setCancelled(true);
            
            // 修复重大错误：立即更新玩家手中的物品，防止客户端预测导致物品变化
            plugin.getScheduler().runAtEntity(player, () -> {
                // 强制更新玩家手中的物品
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (villagerManager.isVillagerBucket(currentItem)) {
                    // 如果仍然是村民桶，强制更新客户端显示
                    player.updateInventory();
                } else {
                    // 如果物品被改变，恢复为村民桶
                    player.getInventory().setItemInMainHand(itemInHand);
                    player.updateInventory();
                }
            });
            
            // 如果是村民，尝试捕获
            if (entity instanceof Villager villager) {
                handleVillagerCapture(player, villager, itemInHand);
            } else if (entity instanceof Cow || entity instanceof MushroomCow) {
                // 特别处理牛和哞菇，防止牛奶桶bug
                sendMessage(player, plugin.getMessage("interaction.cannot-use-on-animals", "&c村民桶不能用于收集牛奶！"));
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
            sendMessage(player, plugin.getMessage("bucket-already-used", "&c这个桶已经装了一个村民！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.capture", "villagerbucket.capture"))) {
            sendMessage(player, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查村民是否有效
        if (villager.isDead() || !villager.isValid()) {
            sendMessage(player, plugin.getMessage("invalid-villager", "&c这个村民无效或已死亡！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(villager.getWorld())) {
            sendMessage(player, plugin.getMessage("world-disabled", "&c这个世界禁止使用村民桶！"));
            event.setCancelled(true);
            return;
        }
        
        // 检查领地权限 - 捕获村民需要破坏权限
        if (!claimManager.canDestroy(player, villager.getLocation())) {
            sendMessage(player, plugin.getMessage("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！"));
            event.setCancelled(true);
            return;
        }
        
        // 取消事件，防止默认行为
        event.setCancelled(true);
        
        // 异步捕获村民
        captureVillagerAsync(player, villager, itemInHand);
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
        
        // 修复重大错误：立即更新玩家物品，防止流体交互
        plugin.getScheduler().runAtEntity(player, () -> {
            ItemStack currentItem = player.getInventory().getItemInMainHand();
            if (!villagerManager.isVillagerBucket(currentItem)) {
                // 如果物品被改变，恢复为村民桶
                player.getInventory().setItemInMainHand(item);
                player.updateInventory();
            }
        });
        
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.release", "villagerbucket.release"))) {
            sendMessage(player, plugin.getMessage("no-permission-release", "&c你没有权限释放村民！"));
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(player.getWorld())) {
            sendMessage(player, plugin.getMessage("world-disabled", "&c这个世界禁止使用村民桶！"));
            return;
        }
        
        // 如果是右键空气，给出提示
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            sendMessage(player, plugin.getMessage("interaction.click-block-to-release", "&e请右键方块来释放村民！"));
            return;
        }
        
        // 获取点击的位置
        if (event.getClickedBlock() == null) return;
        
        Block clickedBlock = event.getClickedBlock();
        BlockFace blockFace = event.getBlockFace();
        
        // 检查点击的方块是否是流体或流体容器，如果是则直接拒绝
        if (isFluid(clickedBlock.getType()) || isFluidContainer(clickedBlock.getType())) {
            sendMessage(player, plugin.getMessage("cannot-place-in-fluid", "&c村民桶不能用于收集流体！"));
            plugin.debug("阻止玩家 " + player.getName() + " 使用村民桶与流体交互");
            return;
        }
        
        // 计算村民生成位置
        Location spawnLocation = calculateSpawnLocation(clickedBlock, blockFace);
        
        // 检查领地权限 - 释放村民需要建造权限
        if (!claimManager.canBuild(player, spawnLocation)) {
            sendMessage(player, plugin.getMessage("no-claim-permission-release", "&c你没有权限在此领地内释放村民！"));
            return;
        }
        
        // 防止重复生成检查
        if (!canReleaseVillager(player, item, spawnLocation)) {
            return;
        }
        
        // 标记为处理中
        String processingKey = getProcessingKey(player, item);
        processingReleases.put(processingKey, true);
        
        // 异步释放村民
        releaseVillagerAsync(player, item, spawnLocation, processingKey);
    }
    
    /**
     * 检查是否可以释放村民（防重复生成）- 修复误报
     */
    private boolean canReleaseVillager(Player player, ItemStack item, Location location) {
        // 检查防重复生成是否启用
        if (!plugin.getConfig().getBoolean("settings.anti-duplicate.enabled", true)) {
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("settings.anti-duplicate.release-cooldown", 1000L);
        
        // 检查玩家冷却时间
        Long lastReleaseTime = playerReleaseCooldowns.get(player.getUniqueId());
        if (lastReleaseTime != null && currentTime - lastReleaseTime < cooldown) {
            sendMessage(player, plugin.getMessage("duplicate-release", "&c请勿重复释放村民！"));
            return false;
        }
        
        // 检查物品唯一标识
        String itemKey = getItemKey(player, item, location);
        Long lastItemTime = releaseCooldowns.get(itemKey);
        if (lastItemTime != null && currentTime - lastItemTime < cooldown) {
            sendMessage(player, plugin.getMessage("duplicate-release", "&c请勿重复释放村民！"));
            return false;
        }
        
        // 检查附近村民 - 放宽条件，减少误报
        double radius = plugin.getConfig().getDouble("settings.anti-duplicate.nearby-check-radius", 0.5);
        int maxNearbyVillagers = plugin.getConfig().getInt("settings.anti-duplicate.max-nearby-villagers", 2);
        
        if (hasTooManyVillagersNearby(location, radius, maxNearbyVillagers)) {
            sendMessage(player, plugin.getMessage("nearby-villager", "&c附近已存在太多村民，请换个位置释放！"));
            return false;
        }
        
        // 更新冷却时间
        playerReleaseCooldowns.put(player.getUniqueId(), currentTime);
        releaseCooldowns.put(itemKey, currentTime);
        
        return true;
    }

    /**
     * 检查附近是否有太多村民 - 更精确的检测
     */
    private boolean hasTooManyVillagersNearby(Location location, double radius, int maxCount) {
        try {
            int count = 0;
            for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
                if (entity instanceof Villager villager) {
                    if (!villager.isDead() && villager.isValid()) {
                        count++;
                        if (count >= maxCount) {
                            plugin.debug("位置 " + location + " 附近有 " + count + " 个村民，超过限制 " + maxCount);
                            return true;
                        }
                    }
                }
            }
            plugin.debug("位置 " + location + " 附近有 " + count + " 个村民，允许释放");
            return false;
        } catch (Exception e) {
            plugin.debug("检查附近村民数量时出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 生成处理键
     */
    private String getProcessingKey(Player player, ItemStack item) {
        return player.getUniqueId() + ":" + System.identityHashCode(item);
    }
    
    /**
     * 生成物品键
     */
    private String getItemKey(Player player, ItemStack item, Location location) {
        return player.getUniqueId() + ":" + 
               location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ":" +
               System.identityHashCode(item);
    }
    
    /**
     * 清理过期的冷却时间记录
     */
    private void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("settings.anti-duplicate.release-cooldown", 1000L) * 5;
        
        releaseCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > cooldown
        );
        
        playerReleaseCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > cooldown
        );
    }
    
    private void handleVillagerCapture(Player player, Villager villager, ItemStack villagerBucket) {
        // 检查权限
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) && 
            !player.hasPermission(plugin.getConfig().getString("permissions.capture", "villagerbucket.capture"))) {
            sendMessage(player, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return;
        }
        
        // 检查村民是否有效
        if (villager.isDead() || !villager.isValid()) {
            sendMessage(player, plugin.getMessage("invalid-villager", "&c这个村民无效或已死亡！"));
            return;
        }
        
        // 检查世界是否被禁止
        if (isWorldDisabled(villager.getWorld())) {
            sendMessage(player, plugin.getMessage("world-disabled", "&c这个世界禁止使用村民桶！"));
            return;
        }
        
        // 检查领地权限
        if (!claimManager.canDestroy(player, villager.getLocation())) {
            sendMessage(player, plugin.getMessage("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！"));
            return;
        }
        
        // 玩家手持村民桶时右键村民，提示需要空桶
        sendMessage(player, plugin.getMessage("interaction.use-empty-bucket", "&e请使用空桶来捕获村民！"));
    }
    
    private void handleOtherEntityInteraction(Player player, Entity entity) {
        // 根据实体类型给出适当的提示
        if (entity instanceof Cow || entity instanceof MushroomCow) {
            sendMessage(player, plugin.getMessage("interaction.cannot-use-on-animals", "&c村民桶不能用于收集牛奶，请使用空桶！"));
        } else if (entity instanceof WaterMob) {
            sendMessage(player, plugin.getMessage("interaction.cannot-use-on-water-mobs", "&e村民桶不能用于捕获水生生物。"));
        } else {
            sendMessage(player, plugin.getMessage("interaction.general-usage", "&e村民桶只能用于释放村民。"));
        }
    }
    
    /**
     * 发送消息的辅助方法
     */
    private void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        
        // 使用调度器在主线程发送消息
        plugin.getScheduler().runGlobal(() -> {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        });
    }
    
    /**
     * 检查世界是否被禁用
     */
    private boolean isWorldDisabled(World world) {
        if (world == null) return false;
        List<String> disabledWorlds = plugin.getConfig().getStringList("settings.disabled-worlds");
        return disabledWorlds.contains(world.getName());
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
    
    /**
     * 异步捕获村民
     */
    private void captureVillagerAsync(Player player, Villager villager, ItemStack bucket) {
        // 创建final变量供lambda使用
        final Player finalPlayer = player;
        final Villager finalVillager = villager;
        final ItemStack finalBucket = bucket;
        
        // 首先在主线程检查村民状态
        plugin.getScheduler().runAtEntity(finalVillager, () -> {
            // 再次检查村民是否仍然有效（防止并发修改）
            if (finalVillager.isDead() || !finalVillager.isValid()) {
                sendMessage(finalPlayer, plugin.getMessage("invalid-villager", "&c这个村民无效或已死亡！"));
                return;
            }
            
            // 检查村民是否已经有主人（防止捕获其他插件的特殊村民）
            if (hasOwner(finalVillager)) {
                sendMessage(finalPlayer, plugin.getMessage("villager-owned", "&c这个村民已经有主人了！"));
                return;
            }
            
            // 再次检查领地权限
            if (!claimManager.canDestroy(finalPlayer, finalVillager.getLocation())) {
                sendMessage(finalPlayer, plugin.getMessage("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！"));
                return;
            }
            
            // 异步创建村民桶 - 添加超时处理
            plugin.getScheduler().runAsync(() -> {
                try {
                    // 设置超时保护
                    CompletableFuture<ItemStack> future = CompletableFuture.supplyAsync(() -> {
                        return villagerManager.createVillagerBucket(finalVillager);
                    });
                    
                    ItemStack villagerBucket;
                    try {
                        int timeoutSeconds = plugin.getConfig().getInt("settings.folia.timeout-seconds", 10);
                        villagerBucket = future.get(timeoutSeconds, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        plugin.debug("创建村民桶超时");
                        future.cancel(true);
                        villagerBucket = null;
                    }
                    
                    if (villagerBucket == null) {
                        plugin.getScheduler().runAtEntity(finalPlayer, () -> {
                            sendMessage(finalPlayer, "&c创建村民桶失败，数据可能已损坏!");
                        });
                        return;
                    }
                    
                    // 创建final变量供lambda使用
                    final ItemStack finalVillagerBucket = villagerBucket;
                    
                    // 回到主线程处理物品和实体操作
                    plugin.getScheduler().runAtEntity(finalPlayer, () -> {
                        handleBucketCreationResult(finalPlayer, finalVillager, finalBucket, finalVillagerBucket);
                    });
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "异步创建村民桶时发生错误", e);
                    plugin.getScheduler().runAtEntity(finalPlayer, () -> {
                        sendMessage(finalPlayer, "&c创建村民桶时发生错误!");
                    });
                }
            });
        });
    }
    
    /**
     * 处理桶创建结果
     */
    private void handleBucketCreationResult(Player player, Villager villager, ItemStack originalBucket, ItemStack villagerBucket) {
        try {
            // 处理物品栏逻辑
            if (originalBucket.getAmount() > 1) {
                originalBucket.setAmount(originalBucket.getAmount() - 1);
                
                if (player.getInventory().addItem(villagerBucket).isEmpty()) {
                    // 成功添加
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), villagerBucket);
                    sendMessage(player, plugin.getMessage("inventory-full", "&e物品栏已满，物品已掉落在地上！"));
                }
            } else {
                player.getInventory().setItemInMainHand(villagerBucket);
            }
            
            // 移除村民
            villager.remove();
            
            // 播放效果
            playCaptureEffects(player.getLocation());
            
            // 获取中文职业名称
            String professionName = VillagerManager.getProfessionName(villager.getProfession());
            
            // 发送成功消息
            String messageTemplate = plugin.getMessage("captured", "&a成功捕获{0}村民！");
            String formattedMessage = MessageFormat.format(messageTemplate, professionName);
            sendMessage(player, formattedMessage);
            
            plugin.debug("玩家 " + player.getName() + " 成功捕获村民: " + professionName);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理村民桶创建结果时发生错误", e);
            sendMessage(player, "&c处理村民桶时发生错误!");
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
        // 创建final变量供lambda使用
        final Location finalLocation = location;
        
        // 使用调度器在正确的位置线程播放效果
        plugin.getScheduler().runAtLocation(finalLocation, () -> {
            Sound sound = getSoundByName(plugin.getConfig().getString("sounds.capture", "ITEM_BUCKET_FILL"));
            if (sound != null) {
                try {
                    finalLocation.getWorld().playSound(finalLocation, sound, 1.0f, 1.0f);
                } catch (Exception e) {
                    plugin.debug("播放捕获音效失败: " + e.getMessage());
                }
            }
            
            // 播放粒子效果
            if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
                try {
                    finalLocation.getWorld().spawnParticle(Particle.SMOKE, finalLocation, 10, 0.5, 0.5, 0.5, 0.02);
                } catch (Exception e) {
                    // 忽略粒子错误
                }
            }
        });
    }
    
    /**
     * 播放释放效果
     */
    private void playReleaseEffects(Location location) {
        // 创建final变量供lambda使用
        final Location finalLocation = location;
        
        // 使用调度器在正确的位置线程播放效果
        plugin.getScheduler().runAtLocation(finalLocation, () -> {
            Sound sound = getSoundByName(plugin.getConfig().getString("sounds.release", "ITEM_BUCKET_EMPTY"));
            if (sound != null) {
                try {
                    finalLocation.getWorld().playSound(finalLocation, sound, 1.0f, 1.0f);
                } catch (Exception e) {
                    plugin.debug("播放释放音效失败: " + e.getMessage());
                }
            }
            
            // 播放粒子效果
            if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
                try {
                    finalLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, finalLocation, 15, 1, 1, 1, 0.1);
                } catch (Exception e) {
                    // 忽略粒子错误
                }
            }
        });
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
    
    /**
     * 统一的异步释放村民方法
     */
    private void releaseVillagerAsync(Player player, ItemStack villagerBucket, Location location, String processingKey) {
        // 创建final变量供lambda使用
        final Player finalPlayer = player;
        final ItemStack finalVillagerBucket = villagerBucket;
        final Location finalLocation = location;
        final String finalProcessingKey = processingKey;
        
        // 首先验证村民桶数据完整性（异步）
        villagerManager.isValidVillagerBucketAsync(finalVillagerBucket, isValid -> {
            if (!isValid) {
                plugin.getLogger().warning("村民桶数据验证失败: " + finalVillagerBucket.toString());
                sendMessage(finalPlayer, "&c村民桶数据不完整或已损坏，无法释放村民!");
                processingReleases.remove(finalProcessingKey);
                return;
            }
            
            // 在主线程恢复村民（实体操作必须在主线程）
            plugin.getScheduler().runAtLocation(finalLocation, () -> {
                try {
                    // 再次检查是否处于流体方块
                    if (isInFluid(finalLocation)) {
                        sendMessage(finalPlayer, plugin.getMessage("cannot-place-in-fluid", "&c不能将村民放置在流体中！"));
                        processingReleases.remove(finalProcessingKey);
                        return;
                    }
                    
                    // 再次检查领地权限
                    if (!claimManager.canBuild(finalPlayer, finalLocation)) {
                        sendMessage(finalPlayer, plugin.getMessage("no-claim-permission-release", "&c你没有权限在此领地内释放村民！"));
                        processingReleases.remove(finalProcessingKey);
                        return;
                    }
                    
                    // 检查周围是否已经有村民（防止重复生成）- 使用修复后的方法
                    double radius = plugin.getConfig().getDouble("settings.anti-duplicate.nearby-check-radius", 0.5);
                    int maxNearbyVillagers = plugin.getConfig().getInt("settings.anti-duplicate.max-nearby-villagers", 2);
                    
                    if (hasTooManyVillagersNearby(finalLocation, radius, maxNearbyVillagers)) {
                        sendMessage(finalPlayer, plugin.getMessage("nearby-villager", "&c附近已存在太多村民，请换个位置释放！"));
                        processingReleases.remove(finalProcessingKey);
                        return;
                    }
                    
                    // 恢复村民
                    Villager villager = villagerManager.restoreVillagerFromBucket(finalVillagerBucket, finalLocation);
                    
                    if (villager == null || villager.isDead() || !villager.isValid()) {
                        plugin.getLogger().severe("释放村民失败: 村民生成后无效或为空");
                        sendMessage(finalPlayer, "&c释放村民失败，请尝试在其他位置放置!");
                        processingReleases.remove(finalProcessingKey);
                        return;
                    }
                    
                    // 处理物品栏逻辑 - 先处理物品，再播放效果
                    boolean success = handleBucketRemoval(finalPlayer, finalVillagerBucket);
                    
                    if (!success) {
                        // 如果物品处理失败，移除生成的村民
                        villager.remove();
                        sendMessage(finalPlayer, "&c释放村民失败，物品处理出错!");
                        processingReleases.remove(finalProcessingKey);
                        return;
                    }
                    
                    // 播放效果
                    playReleaseEffects(finalLocation);
                    
                    // 使用final变量
                    final Villager finalVillager = villager;
                    final String finalProfessionName = VillagerManager.getProfessionName(villager.getProfession());
                    
                    // 延迟发送成功消息
                    plugin.getScheduler().runAtEntityLater(finalVillager, () -> {
                        try {
                            // 再次验证村民状态
                            if (finalVillager.isDead() || !finalVillager.isValid()) {
                                sendMessage(finalPlayer, "&e村民已释放，但可能遇到了问题。");
                            } else {
                                // 发送成功消息
                                String messageTemplate = plugin.getMessage("released", "&a已成功释放{0}村民！");
                                String formattedMessage = MessageFormat.format(messageTemplate, finalProfessionName);
                                sendMessage(finalPlayer, formattedMessage);
                                
                                plugin.debug("玩家 " + finalPlayer.getName() + " 成功释放村民: " + finalProfessionName);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "发送释放消息时发生错误:", e);
                            sendMessage(finalPlayer, "&a村民已成功释放!");
                        } finally {
                            // 最终清理处理标记
                            processingReleases.remove(finalProcessingKey);
                            cleanupExpiredCooldowns();
                        }
                    }, 5L);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "释放村民时发生严重错误", e);
                    sendMessage(finalPlayer, "&c释放村民时发生错误，请尝试重新放置或联系管理员!");
                    processingReleases.remove(finalProcessingKey);
                }
            });
        });
    }
    
    /**
     * 处理桶移除逻辑 - 修复空桶问题
     */
    private boolean handleBucketRemoval(Player player, ItemStack villagerBucket) {
        try {
            // 立即强制更新玩家手中的物品，防止客户端预测
            plugin.getScheduler().runAtEntity(player, () -> {
                try {
                    ItemStack emptyBucket = new ItemStack(Material.BUCKET, 1);
                    
                    if (villagerBucket.getAmount() > 1) {
                        // 减少村民桶数量
                        villagerBucket.setAmount(villagerBucket.getAmount() - 1);
                        
                        // 尝试添加空桶到物品栏
                        if (player.getInventory().addItem(emptyBucket).isEmpty()) {
                            plugin.debug("成功减少村民桶数量并添加空桶");
                        } else {
                            // 物品栏满，掉落空桶
                            player.getWorld().dropItemNaturally(player.getLocation(), emptyBucket);
                            sendMessage(player, plugin.getMessage("inventory-full", "&e物品栏已满，空桶已掉落在地上！"));
                            plugin.debug("物品栏已满，掉落空桶");
                        }
                    } else {
                        // 直接替换手中的村民桶为空桶
                        player.getInventory().setItemInMainHand(emptyBucket);
                        plugin.debug("成功替换村民桶为空桶");
                    }
                    
                    // 强制更新客户端显示
                    player.updateInventory();
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "处理桶移除时发生错误", e);
                    // 如果出错，确保至少移除村民桶
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    player.updateInventory();
                }
            });
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理桶移除时发生严重错误", e);
            return false;
        }
    }
}