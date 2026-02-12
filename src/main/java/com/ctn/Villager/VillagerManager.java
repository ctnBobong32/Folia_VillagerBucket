package com.ctn.Villager;

import com.google.gson.Gson;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class VillagerManager {

    private final VillagerBucketPlugin plugin;
    private final Gson gson = new Gson();
    private final NamespacedKey villagerDataKey;
    private final NamespacedKey capturedKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey customDiscountKey;
    private final NamespacedKey creationSourceKey;
    private final ConcurrentHashMap<UUID, VillagerSnapshot> villagerCache = new ConcurrentHashMap<>();

    private static final Map<Villager.Type, String> VILLAGER_TYPE_NAMES = new HashMap<>();
    private static final Map<Villager.Profession, String> VILLAGER_PROFESSION_NAMES = new HashMap<>();
    private static final Map<Villager.Type, ChatColor> VILLAGER_TYPE_COLORS = new HashMap<>();

    static {
        VILLAGER_TYPE_NAMES.put(Villager.Type.DESERT, "沙漠");
        VILLAGER_TYPE_NAMES.put(Villager.Type.JUNGLE, "丛林");
        VILLAGER_TYPE_NAMES.put(Villager.Type.PLAINS, "平原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SAVANNA, "热带草原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SNOW, "雪原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SWAMP, "沼泽");
        VILLAGER_TYPE_NAMES.put(Villager.Type.TAIGA, "针叶林");

        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.NONE, "失业");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.ARMORER, "盔甲匠");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.BUTCHER, "屠夫");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.CARTOGRAPHER, "制图师");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.CLERIC, "牧师");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.FARMER, "农民");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.FISHERMAN, "渔夫");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.FLETCHER, "制箭师");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.LEATHERWORKER, "皮匠");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.LIBRARIAN, "图书管理员");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.MASON, "石匠");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.NITWIT, "傻子");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.SHEPHERD, "牧羊人");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.TOOLSMITH, "工具匠");
        VILLAGER_PROFESSION_NAMES.put(Villager.Profession.WEAPONSMITH, "武器匠");

        VILLAGER_TYPE_COLORS.put(Villager.Type.DESERT, ChatColor.GOLD);
        VILLAGER_TYPE_COLORS.put(Villager.Type.JUNGLE, ChatColor.DARK_GREEN);
        VILLAGER_TYPE_COLORS.put(Villager.Type.PLAINS, ChatColor.GREEN);
        VILLAGER_TYPE_COLORS.put(Villager.Type.SAVANNA, ChatColor.YELLOW);
        VILLAGER_TYPE_COLORS.put(Villager.Type.SNOW, ChatColor.AQUA);
        VILLAGER_TYPE_COLORS.put(Villager.Type.SWAMP, ChatColor.DARK_PURPLE);
        VILLAGER_TYPE_COLORS.put(Villager.Type.TAIGA, ChatColor.DARK_GREEN);
    }

    public VillagerManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;

        this.villagerDataKey = new NamespacedKey(plugin, "villager_data");
        this.capturedKey = new NamespacedKey(plugin, "captured");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.customDiscountKey = new NamespacedKey(plugin, "discount");
        this.creationSourceKey = new NamespacedKey(plugin, "creation_source");

        Bukkit.getPluginManager().registerEvents(new VillagerEventListener(), plugin);

        startCacheCleanupLoop();
    }

    public static class VillagerSnapshot {
        public String profession;
        public String type;
        public int level;
        public int experience;
        public boolean adult;
        public int age;
        public boolean ageLock;
        public double health;
        public double maxHealth;
        public String customName;
        public List<MerchantRecipeData> recipes;
        public boolean captured = false;
        public UUID owner = null;
        public double customDiscount = 1.0;
        public String creationSource = "unknown";
        
        public long timestamp = System.currentTimeMillis();
    }

    public static class MerchantRecipeData {
        public List<ItemData> ingredients;
        public ItemData result;
        public int uses;
        public int maxUses;
        public boolean experienceReward;
        public int villagerExperience;
        public float priceMultiplier = 1.0f;
        public int demand = 0;
        public int specialPrice = 0;

        public static MerchantRecipeData fromRecipe(MerchantRecipe recipe) {
            MerchantRecipeData data = new MerchantRecipeData();
            data.uses = recipe.getUses();
            data.maxUses = recipe.getMaxUses();
            data.experienceReward = recipe.hasExperienceReward();
            data.villagerExperience = recipe.getVillagerExperience();

            data.ingredients = new ArrayList<>();
            for (ItemStack ing : recipe.getIngredients()) {
                ItemData id = ItemData.fromItemStack(ing);
                if (id != null) data.ingredients.add(id);
            }
            data.result = ItemData.fromItemStack(recipe.getResult());

            try {
                data.priceMultiplier = (Float) recipe.getClass().getMethod("getPriceMultiplier").invoke(recipe);
                data.demand = (Integer) recipe.getClass().getMethod("getDemand").invoke(recipe);
                data.specialPrice = (Integer) recipe.getClass().getMethod("getSpecialPrice").invoke(recipe);
            } catch (Exception ignored) {}

            return data;
        }

        public MerchantRecipe toRecipe() {
            ItemStack resultItem = (result != null ? result.toItemStack() : new ItemStack(Material.STONE, 1));
            MerchantRecipe r = new MerchantRecipe(resultItem, Math.max(1, maxUses));

            List<ItemStack> ings = new ArrayList<>();
            if (ingredients != null) {
                for (ItemData id : ingredients) {
                    ItemStack it = id.toItemStack();
                    if (it != null && it.getType() != Material.AIR) ings.add(it);
                }
            }
            if (ings.isEmpty()) ings.add(new ItemStack(Material.EMERALD, 1));

            r.setIngredients(ings);
            r.setUses(Math.max(0, uses));
            r.setExperienceReward(experienceReward);
            r.setVillagerExperience(Math.max(0, villagerExperience));

            try {
                r.getClass().getMethod("setPriceMultiplier", float.class).invoke(r, priceMultiplier);
                r.getClass().getMethod("setDemand", int.class).invoke(r, demand);
                r.getClass().getMethod("setSpecialPrice", int.class).invoke(r, specialPrice);
            } catch (Exception ignored) {}

            return r;
        }
    }

    public static class ItemData {
        public String type;
        public int amount;
        public String displayName;
        public List<String> lore;
        public Map<String, Integer> enchants;
        public Map<String, Integer> storedEnchants;
        public Integer customModelData;

        public static ItemData fromItemStack(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) return null;

            ItemData d = new ItemData();
            d.type = item.getType().name();
            d.amount = Math.max(1, item.getAmount());

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return d;

            if (meta.hasDisplayName()) d.displayName = meta.getDisplayName();
            if (meta.hasLore()) d.lore = new ArrayList<>(meta.getLore());

            if (meta.hasEnchants()) {
                d.enchants = new HashMap<>();
                meta.getEnchants().forEach((ench, lvl) -> d.enchants.put(ench.getKey().toString(), lvl));
            }

            if (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants()) {
                d.storedEnchants = new HashMap<>();
                esm.getStoredEnchants().forEach((ench, lvl) -> d.storedEnchants.put(ench.getKey().toString(), lvl));
            }

            if (meta.hasCustomModelData()) d.customModelData = meta.getCustomModelData();
            return d;
        }

        public ItemStack toItemStack() {
            Material m;
            try {
                m = Material.valueOf(type);
            } catch (Exception e) {
                return new ItemStack(Material.STONE, 1);
            }

            ItemStack item = new ItemStack(m, Math.max(1, amount));
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (displayName != null) meta.setDisplayName(displayName);
            if (lore != null) meta.setLore(lore);

            if (enchants != null) {
                for (Map.Entry<String, Integer> e : enchants.entrySet()) {
                    Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(e.getKey()));
                    if (ench != null) meta.addEnchant(ench, e.getValue(), true);
                }
            }

            if (storedEnchants != null && meta instanceof EnchantmentStorageMeta esm) {
                for (Map.Entry<String, Integer> e : storedEnchants.entrySet()) {
                    Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(e.getKey()));
                    if (ench != null) esm.addStoredEnchant(ench, e.getValue(), true);
                }
                meta = esm;
            }

            if (customModelData != null) meta.setCustomModelData(customModelData);

            item.setItemMeta(meta);
            return item;
        }
    }

    private class VillagerEventListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onVillagerSpawn(CreatureSpawnEvent event) {
            if (!(event.getEntity() instanceof Villager villager)) return;
            String src = String.valueOf(event.getSpawnReason());

            plugin.getScheduler().runAtEntity(villager, () -> {
                VillagerSnapshot snap = snapshotFromVillager(villager);
                if (snap != null) {
                    snap.creationSource = src;
                    villagerCache.put(villager.getUniqueId(), snap);
                }
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onZombieCure(EntityTransformEvent event) {
            if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) return;

            Entity transformed = event.getTransformedEntity();
            if (!(transformed instanceof Villager villager)) return;

            plugin.getScheduler().runAtEntity(villager, () -> {
                PersistentDataContainer pdc = villager.getPersistentDataContainer();
                pdc.set(creationSourceKey, PersistentDataType.STRING, "cured");
                pdc.set(customDiscountKey, PersistentDataType.DOUBLE, 0.75);

                VillagerSnapshot snap = snapshotFromVillager(villager);
                if (snap != null) {
                    snap.creationSource = "cured";
                    snap.customDiscount = 0.75;
                    villagerCache.put(villager.getUniqueId(), snap);
                }
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onVillagerInteract(PlayerInteractEntityEvent event) {
            if (!(event.getRightClicked() instanceof Villager villager)) return;
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand == null || hand.getType() != Material.BUCKET) return;

            plugin.getScheduler().runAtEntity(villager, () -> {
                villager.getPersistentDataContainer().set(capturedKey, PersistentDataType.BOOLEAN, true);
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onVillagerDeath(EntityDeathEvent event) {
            if (!(event.getEntity() instanceof Villager)) return;
            villagerCache.remove(event.getEntity().getUniqueId());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onChunkUnload(ChunkUnloadEvent event) {
            for (Entity e : event.getChunk().getEntities()) {
                if (e instanceof Villager villager) {
                    plugin.getScheduler().runAtEntity(villager, () -> {
                        VillagerSnapshot snap = snapshotFromVillager(villager);
                        if (snap != null) villagerCache.put(villager.getUniqueId(), snap);
                    });
                }
            }
        }
    }

    private VillagerSnapshot snapshotFromVillager(Villager villager) {
        try {
            VillagerSnapshot s = new VillagerSnapshot();

            s.profession = villager.getProfession().name();
            s.type = villager.getVillagerType().name();
            s.level = Math.max(1, villager.getVillagerLevel());
            s.experience = Math.max(0, villager.getVillagerExperience());
            s.adult = villager.isAdult();
            s.age = villager.getAge();
            s.ageLock = villager.getAgeLock();

            double max = Math.max(1.0, villager.getMaxHealth());
            double hp = Math.max(1.0, Math.min(villager.getHealth(), max));
            s.maxHealth = max;
            s.health = hp;

            s.customName = villager.getCustomName();

            PersistentDataContainer pdc = villager.getPersistentDataContainer();
            if (pdc.has(capturedKey, PersistentDataType.BOOLEAN)) {
                Boolean b = pdc.get(capturedKey, PersistentDataType.BOOLEAN);
                s.captured = (b != null && b);
            }
            if (pdc.has(ownerKey, PersistentDataType.STRING)) {
                try { s.owner = UUID.fromString(pdc.get(ownerKey, PersistentDataType.STRING)); } catch (Exception ignored) {}
            }
            if (pdc.has(customDiscountKey, PersistentDataType.DOUBLE)) {
                Double d = pdc.get(customDiscountKey, PersistentDataType.DOUBLE);
                if (d != null) s.customDiscount = d;
            }
            if (pdc.has(creationSourceKey, PersistentDataType.STRING)) {
                String src = pdc.get(creationSourceKey, PersistentDataType.STRING);
                if (src != null) s.creationSource = src;
            }

            if (plugin.getConfig().getBoolean("settings.save-trades", true)) {
                s.recipes = new ArrayList<>();
                for (MerchantRecipe r : villager.getRecipes()) {
                    s.recipes.add(MerchantRecipeData.fromRecipe(r));
                }
            }

            s.timestamp = System.currentTimeMillis();
            return s;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "创建村民快照失败: " + t.getMessage());
            return null;
        }
    }

    public void createVillagerBucketAsync(final Villager villager, final java.util.function.Consumer<ItemStack> callback) {
        plugin.getScheduler().runAtEntity(villager, () -> {
            if (villager.isDead() || !villager.isValid()) {
                callback.accept(null);
                return;
            }

            VillagerSnapshot snap = snapshotFromVillager(villager);
            if (snap == null) {
                callback.accept(null);
                return;
            }

            villager.getPersistentDataContainer().set(capturedKey, PersistentDataType.BOOLEAN, true);
            snap.captured = true;

            plugin.getScheduler().runAsync(() -> {
                String encoded = serializeSnapshot(snap);
                if (encoded == null || encoded.isEmpty()) {
                    plugin.getScheduler().runGlobal(() -> callback.accept(null));
                    return;
                }

                plugin.getScheduler().runGlobal(() -> {
                    ItemStack bucket = buildBucketItem(encoded, snap, villager.getVillagerType(), villager.getProfession(), villager.getCustomName());
                    callback.accept(bucket);
                });
            });
        });
    }

    public ItemStack createVillagerBucket(Villager villager) {
        if (Bukkit.isPrimaryThread()) {
            VillagerSnapshot snap = snapshotFromVillager(villager);
            if (snap == null) return null;
            String encoded = serializeSnapshot(snap);
            if (encoded == null) return null;
            return buildBucketItem(encoded, snap, villager.getVillagerType(), villager.getProfession(), villager.getCustomName());
        }

        final ItemStack[] out = {null};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        plugin.getScheduler().runAtEntity(villager, () -> {
            try {
                VillagerSnapshot snap = snapshotFromVillager(villager);
                String encoded = (snap == null ? null : serializeSnapshot(snap));
                out[0] = (encoded == null ? null : buildBucketItem(encoded, snap, villager.getVillagerType(), villager.getProfession(), villager.getCustomName()));
            } finally {
                latch.countDown();
            }
        });

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return out[0];
    }

    private ItemStack buildBucketItem(String encoded, VillagerSnapshot snap, Villager.Type type, Villager.Profession prof, String customName) {
        ItemStack bucket = new ItemStack(Material.BUCKET);
        ItemMeta meta = bucket.getItemMeta();
        if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(Material.BUCKET);
        if (meta == null) return null;

        String displayName = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("bucket.name", "&6村民桶"));
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add("§7右键方块释放村民");
        lore.add("");

        String typeName = getTypeName(type);
        String profName = getProfessionName(prof);

        lore.add("§e类型: §f" + typeName);
        lore.add("§e职业: §f" + profName);
        lore.add("§e等级: §f" + snap.level);
        lore.add("§e经验: §f" + snap.experience);
        lore.add("§e状态: §f" + (snap.adult ? "成年" : "幼年"));
        if (customName != null && !customName.isEmpty()) {
            lore.add("§e名称: §f" + customName);
        }
        if (snap.recipes != null && !snap.recipes.isEmpty()) {
            lore.add("§e交易: §f已保存 " + snap.recipes.size() + " 个");
        }

        meta.setLore(lore);

        int cmd = plugin.getConfig().getInt("settings.custom-model-data", 1000);
        try { meta.setCustomModelData(cmd); } catch (Exception ignored) {}

        meta.getPersistentDataContainer().set(villagerDataKey, PersistentDataType.STRING, encoded);
        bucket.setItemMeta(meta);

        return bucket;
    }

    public void restoreVillagerFromBucketAsync(final ItemStack bucket, final Location loc, final java.util.function.Consumer<Villager> callback) {
        if (!isVillagerBucket(bucket)) {
            callback.accept(null);
            return;
        }

        plugin.getScheduler().runAsync(() -> {
            VillagerSnapshot snap = null;
            try {
                ItemMeta meta = bucket.getItemMeta();
                if (meta != null) {
                    String data = meta.getPersistentDataContainer().get(villagerDataKey, PersistentDataType.STRING);
                    if (data != null && !data.isEmpty()) snap = deserializeSnapshot(data);
                }
            } catch (Exception ignored) {}

            final VillagerSnapshot finalSnap = snap;
            plugin.getScheduler().runAtLocation(loc, () -> {
                if (finalSnap == null) {
                    callback.accept(null);
                    return;
                }
                try {
                    Villager v = restoreVillagerFromSnapshot(finalSnap, loc);
                    callback.accept(v);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "恢复村民失败", e);
                    callback.accept(null);
                }
            });
        });
    }

    public Villager restoreVillagerFromBucket(ItemStack bucket, Location loc) {
        if (!isVillagerBucket(bucket)) return null;

        ItemMeta meta = bucket.getItemMeta();
        if (meta == null) return null;

        String data = meta.getPersistentDataContainer().get(villagerDataKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return null;

        VillagerSnapshot snap = deserializeSnapshot(data);
        if (snap == null) return null;

        if (Bukkit.isPrimaryThread()) {
            return restoreVillagerFromSnapshot(snap, loc);
        }

        final Villager[] out = {null};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        plugin.getScheduler().runAtLocation(loc, () -> {
            try { out[0] = restoreVillagerFromSnapshot(snap, loc); }
            finally { latch.countDown(); }
        });

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return out[0];
    }

    private Villager restoreVillagerFromSnapshot(VillagerSnapshot s, Location loc) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);

        v.setProfession(getProfessionFromString(s.profession));
        v.setVillagerType(getTypeFromString(s.type));

        v.setVillagerLevel(Math.max(1, Math.min(5, s.level)));
        v.setVillagerExperience(Math.max(0, s.experience));

        v.setAgeLock(s.ageLock);

        if (s.customName != null && !s.customName.isEmpty()) {
            v.setCustomName(s.customName);
            v.setCustomNameVisible(true);
        }

        double max = Math.max(1.0, s.maxHealth);
        v.setMaxHealth(max);
        v.setHealth(Math.min(Math.max(1.0, s.health), max));

        v.setAge(s.age);
        if (s.adult) v.setAdult(); else v.setBaby();

        if (plugin.getConfig().getBoolean("settings.save-trades", true) && s.recipes != null && !s.recipes.isEmpty()) {
            List<MerchantRecipe> rs = new ArrayList<>();
            for (MerchantRecipeData rd : s.recipes) rs.add(rd.toRecipe());
            try { v.setRecipes(rs); } catch (Exception e) { plugin.debug("设置交易失败: " + e.getMessage()); }
        }

        PersistentDataContainer pdc = v.getPersistentDataContainer();
        pdc.set(capturedKey, PersistentDataType.BOOLEAN, s.captured);
        if (s.owner != null) pdc.set(ownerKey, PersistentDataType.STRING, s.owner.toString());
        pdc.set(customDiscountKey, PersistentDataType.DOUBLE, s.customDiscount);
        pdc.set(creationSourceKey, PersistentDataType.STRING, s.creationSource);

        v.setRemoveWhenFarAway(false);
        v.setAI(true);
        v.setAware(true);

        s.timestamp = System.currentTimeMillis();
        villagerCache.put(v.getUniqueId(), s);

        return v;
    }

    private String serializeSnapshot(VillagerSnapshot snap) {
        try {
            String json = gson.toJson(snap);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "序列化失败", e);
            return null;
        }
    }

    private VillagerSnapshot deserializeSnapshot(String data) {
        try {
            String decoded = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            return gson.fromJson(decoded, VillagerSnapshot.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isVillagerBucket(ItemStack item) {
        if (item == null || item.getType() != Material.BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(villagerDataKey, PersistentDataType.STRING);
    }

    public boolean isValidVillagerBucket(ItemStack bucket) {
        if (!isVillagerBucket(bucket)) return false;
        try {
            ItemMeta meta = bucket.getItemMeta();
            if (meta == null) return false;
            String data = meta.getPersistentDataContainer().get(villagerDataKey, PersistentDataType.STRING);
            if (data == null || data.isEmpty()) return false;
            VillagerSnapshot s = deserializeSnapshot(data);
            return s != null && s.profession != null && s.type != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getTypeName(Villager.Type type) {
        if (type == null) return "平原";
        return VILLAGER_TYPE_NAMES.getOrDefault(type, type.name());
    }

    public static String getProfessionName(Villager.Profession profession) {
        if (profession == null) return "失业";
        return VILLAGER_PROFESSION_NAMES.getOrDefault(profession, profession.name());
    }

    public static String getFullVillagerDisplayName(Villager.Type type, Villager.Profession profession) {
        String tn = getTypeName(type);
        if (profession == Villager.Profession.NONE) return tn + "村民";
        return tn + getProfessionName(profession);
    }

    public static String getFullVillagerDisplayName(Villager villager) {
        if (villager == null) return "未知村民";
        return getFullVillagerDisplayName(villager.getVillagerType(), villager.getProfession());
    }

    public static String getColoredVillagerDisplayName(Villager villager) {
        if (villager == null) return ChatColor.GRAY + "未知村民";
        ChatColor c = VILLAGER_TYPE_COLORS.getOrDefault(villager.getVillagerType(), ChatColor.WHITE);
        String name = getFullVillagerDisplayName(villager);
        return c + name;
    }

    private Villager.Profession getProfessionFromString(String s) {
        try { return Villager.Profession.valueOf(s.toUpperCase()); }
        catch (Exception e) { return Villager.Profession.NONE; }
    }

    private Villager.Type getTypeFromString(String s) {
        try { return Villager.Type.valueOf(s.toUpperCase()); }
        catch (Exception e) { return Villager.Type.PLAINS; }
    }

    private void startCacheCleanupLoop() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                cleanExpiredCache();
                plugin.getScheduler().runAsyncLater(this, 20L * 60L * 30L);
            }
        };
        plugin.getScheduler().runAsyncLater(task, 20L * 60L * 30L);
    }

    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        long expire = TimeUnit.MINUTES.toMillis(30);

        villagerCache.entrySet().removeIf(e -> (now - e.getValue().timestamp) > expire);
        plugin.debug("清理村民缓存，剩余: " + villagerCache.size());
    }

    public void cleanup() {
        villagerCache.clear();
        plugin.debug("VillagerManager 已清理");
    }

    public NamespacedKey getVillagerDataKey() { return villagerDataKey; }
    public NamespacedKey getCapturedKey() { return capturedKey; }
    public NamespacedKey getOwnerKey() { return ownerKey; }
    public NamespacedKey getCustomDiscountKey() { return customDiscountKey; }
    public NamespacedKey getCreationSourceKey() { return creationSourceKey; }
    public int getCacheSize() { return villagerCache.size(); }
}