package com.ctn.Villager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerInteractionListener implements Listener {

    private final VillagerBucketPlugin plugin;
    private final VillagerManager villagerManager;
    private final ClaimPluginManager claimManager;
    private final Map<UUID, Long> releaseCooldown = new ConcurrentHashMap<>();
    private final Map<String, Long> releaseSpotCooldown = new ConcurrentHashMap<>();
    private final Map<String, Boolean> processing = new ConcurrentHashMap<>();

    public VillagerInteractionListener(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
        this.claimManager = plugin.getClaimManager();
        startCleanupTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        final ItemStack main = player.getInventory().getItemInMainHand();

        if (villagerManager.isVillagerBucket(main)) {
            if (entity instanceof Villager) {
                event.setCancelled(true);
                send(player, plugin.getMessage("interaction.use-empty-bucket",
                        "&e村民桶只能用于释放村民，请使用空桶捕获村民。"));
                return;
            }
            if (entity instanceof Cow || entity instanceof MushroomCow) {
                event.setCancelled(true);
                send(player, plugin.getMessage("interaction.cannot-use-on-animals",
                        "&c村民桶不能用于收集牛奶，请使用空桶！"));
                return;
            }
            return;
        }

        if (!(entity instanceof Villager villager)) return;
        if (main == null || main.getType() != Material.BUCKET || main.getAmount() <= 0) return;
        if (plugin.getConfig().getBoolean("settings.check-permissions", true) &&
                !player.hasPermission(plugin.getConfig().getString("permissions.capture", "villagerbucket.capture"))) {
            event.setCancelled(true);
            send(player, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return;
        }

        if (isWorldDisabled(villager.getWorld())) {
            event.setCancelled(true);
            send(player, plugin.getMessage("world-disabled", "&c这个世界禁止使用村民桶！"));
            return;
        }

        if (!claimManager.canCaptureVillager(player, villager.getLocation())) {
            event.setCancelled(true);
            send(player, plugin.getMessage("no-claim-permission-capture", "&c你没有权限在此领地内捕获村民！"));
            return;
        }

        event.setCancelled(true);
        plugin.getScheduler().runAtEntity(villager, () -> doCapture(player, villager, main));
    }

    private void doCapture(final Player player, final Villager villager, final ItemStack bucketInHand) {
        if (villager.isDead() || !villager.isValid()) {
            send(player, plugin.getMessage("invalid-villager", "&c这个村民无效或已死亡！"));
            return;
        }

        ItemStack villagerBucket;
        try {
            villagerBucket = villagerManager.createVillagerBucket(villager);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "创建村民桶失败", e);
            send(player, "&c创建村民桶失败!");
            return;
        }

        final ItemStack finalVillagerBucket = villagerBucket;

        plugin.getScheduler().runAtEntity(player, () -> {
            try {
                if (bucketInHand.getAmount() > 1) {
                    bucketInHand.setAmount(bucketInHand.getAmount() - 1);
                    if (!player.getInventory().addItem(finalVillagerBucket).isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), finalVillagerBucket);
                        send(player, plugin.getMessage("inventory-full", "&e物品栏已满，村民桶已掉落在地上！"));
                    }
                } else {
                    player.getInventory().setItemInMainHand(finalVillagerBucket);
                }
                player.updateInventory();

                plugin.getScheduler().runAtEntity(villager, villager::remove);

                final Location loc = player.getLocation();
                plugin.getScheduler().runAtLocation(loc, () -> {
                    try {
                        loc.getWorld().playSound(loc, Sound.ITEM_BUCKET_FILL, 1.0f, 1.0f);
                        if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
                            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.02);
                        }
                    } catch (Exception ignored) {}
                });

                String display = villagerManager.getFullVillagerDisplayName(villager);
                String tpl = plugin.getMessage("captured", "&a成功捕获{0}！");
                send(player, MessageFormat.format(tpl, display));

            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "捕获流程异常", e);
                send(player, "&c捕获时发生错误!");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!villagerManager.isVillagerBucket(item)) return;
        event.setCancelled(true);

        if (plugin.getConfig().getBoolean("settings.check-permissions", true) &&
                !player.hasPermission(plugin.getConfig().getString("permissions.release", "villagerbucket.release"))) {
            send(player, plugin.getMessage("no-permission-release", "&c你没有权限释放村民！"));
            return;
        }

        if (isWorldDisabled(player.getWorld())) {
            send(player, plugin.getMessage("world-disabled", "&c这个世界禁止使用村民桶！"));
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR) {
            send(player, plugin.getMessage("interaction.click-block-to-release", "&e请右键方块来释放村民！"));
            return;
        }

        final Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        final Location spawnLoc = calculateSpawnLocation(clicked, event.getBlockFace());
        if (isInFluid(spawnLoc)) {
            send(player, plugin.getMessage("cannot-place-in-fluid", "&c不能在流体中释放村民！"));
            return;
        }

        if (!claimManager.canBuild(player, spawnLoc)) {
            send(player, plugin.getMessage("no-claim-permission-release", "&c你没有权限在此领地内释放村民！"));
            return;
        }

        if (!passReleaseCooldown(player, item, spawnLoc)) {
            send(player, plugin.getMessage("duplicate-release", "&c操作过快，请稍等后再释放。"));
            return;
        }

        final String processingKey = player.getUniqueId() + ":" + System.identityHashCode(item);
        if (processing.putIfAbsent(processingKey, Boolean.TRUE) != null) return;

        validateBucketAsync(item, isValid -> {
            if (!isValid) {
                processing.remove(processingKey);
                send(player, "&c村民桶数据不完整或已损坏，无法释放村民!");
                return;
            }

            plugin.getScheduler().runAtLocation(spawnLoc, () -> {
                try {
                    if (isInFluid(spawnLoc)) {
                        processing.remove(processingKey);
                        send(player, plugin.getMessage("cannot-place-in-fluid", "&c不能在流体中释放村民！"));
                        return;
                    }
                    if (!claimManager.canBuild(player, spawnLoc)) {
                        processing.remove(processingKey);
                        send(player, plugin.getMessage("no-claim-permission-release", "&c你没有权限在此领地内释放村民！"));
                        return;
                    }

                    Villager v = villagerManager.restoreVillagerFromBucket(item, spawnLoc);
                    if (v == null || v.isDead() || !v.isValid()) {
                        processing.remove(processingKey);
                        send(player, "&c释放村民失败，请换个位置再试!");
                        return;
                    }

                    plugin.getScheduler().runAtEntity(player, () -> {
                        try {
                            removeOneVillagerBucketGiveEmpty(player, item);
                            player.updateInventory();
                        } catch (Exception e) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "处理释放后物品失败", e);
                        }
                    });

                    playReleaseEffects(spawnLoc);
                    String display = villagerManager.getFullVillagerDisplayName(v);
                    String tpl = plugin.getMessage("released", "&a已成功释放{0}！");
                    send(player, MessageFormat.format(tpl, display));

                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "释放村民异常", e);
                    send(player, "&c释放村民时发生错误!");
                } finally {
                    processing.remove(processingKey);
                }
            });
        });
    }

    private interface BoolCallback {
        void accept(boolean b);
    }

    private void validateBucketAsync(final ItemStack bucket, final BoolCallback cb) {
        plugin.getScheduler().runAsync(() -> {
            boolean temp;
            try {
                temp = villagerManager.isValidVillagerBucket(bucket);
            } catch (Exception e) {
                temp = false;
            }
            final boolean ok = temp;
            plugin.getScheduler().runGlobal(() -> cb.accept(ok));
        });
    }

    private void removeOneVillagerBucketGiveEmpty(final Player player, final ItemStack villagerBucket) {
        final ItemStack empty = new ItemStack(Material.BUCKET, 1);
        if (villagerBucket.getAmount() > 1) {
            villagerBucket.setAmount(villagerBucket.getAmount() - 1);
            if (!player.getInventory().addItem(empty).isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), empty);
                send(player, plugin.getMessage("inventory-full", "&e物品栏已满，空桶已掉落在地上！"));
            }
        } else {
            player.getInventory().setItemInMainHand(empty);
        }
    }

    private boolean passReleaseCooldown(final Player player, final ItemStack item, final Location loc) {
        final long now = System.currentTimeMillis();
        final long cd = plugin.getConfig().getLong("settings.anti-duplicate.release-cooldown", 800L);

        final UUID u = player.getUniqueId();
        final Long last = releaseCooldown.get(u);
        if (last != null && now - last < cd) return false;

        final String spot = u + ":" + loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + ":" + System.identityHashCode(item);
        final Long lastSpot = releaseSpotCooldown.get(spot);
        if (lastSpot != null && now - lastSpot < cd) return false;

        releaseCooldown.put(u, now);
        releaseSpotCooldown.put(spot, now);
        return true;
    }

    private void startCleanupTask() {
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                cleanupCooldowns();
                plugin.getScheduler().runAsyncLater(this, 1200L);
            }
        };
        plugin.getScheduler().runAsyncLater(task, 1200L);
    }

    private void cleanupCooldowns() {
        final long now = System.currentTimeMillis();
        final long keep = plugin.getConfig().getLong("settings.anti-duplicate.release-cooldown", 800L) * 10;

        releaseCooldown.entrySet().removeIf(e -> now - e.getValue() > keep);
        releaseSpotCooldown.entrySet().removeIf(e -> now - e.getValue() > keep);
    }

    private boolean isWorldDisabled(final World world) {
        if (world == null) return false;
        return plugin.getConfig().getStringList("settings.disabled-worlds").contains(world.getName());
    }

    private Location calculateSpawnLocation(final Block clicked, final BlockFace face) {
        final Location loc = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);
        loc.setY(Math.floor(loc.getY()));
        if (face == BlockFace.UP) loc.setY(loc.getY() + 1);
        return loc;
    }

    private boolean isInFluid(final Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        final Material m = loc.getBlock().getType();
        return m == Material.WATER || m == Material.LAVA || loc.getBlock().isLiquid() || m == Material.BUBBLE_COLUMN
                || m == Material.WATER_CAULDRON || m == Material.LAVA_CAULDRON || m == Material.POWDER_SNOW_CAULDRON;
    }

    private void playReleaseEffects(final Location loc) {
        plugin.getScheduler().runAtLocation(loc, () -> {
            try {
                loc.getWorld().playSound(loc, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
                if (plugin.getConfig().getBoolean("settings.enable-particles", true)) {
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 1, 1, 1, 0.1);
                }
            } catch (Exception ignored) {}
        });
    }

    private void send(final Player player, final String msg) {
        if (msg == null || msg.isEmpty()) return;
        plugin.getScheduler().runAtEntity(player, () ->
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
        );
    }

    public void cleanup() {
        releaseCooldown.clear();
        releaseSpotCooldown.clear();
        processing.clear();
    }
}