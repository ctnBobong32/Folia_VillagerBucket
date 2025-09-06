package com.ctn.Villager;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class VillagerManager {
    private final VillagerBucketPlugin plugin;
    private final NamespacedKey villagerDataKey;
    private final int customModelData;
    private final Gson gson;
    private final Set<UUID> recentlySpawnedVillagers;
    
    // 职业名称映射表
    public static final Map<Villager.Profession, String> PROFESSION_NAMES = new HashMap<>();
    // 村民类型映射表
    public static final Map<Villager.Type, String> VILLAGER_TYPE_NAMES = new HashMap<>();
    
    // 使用静态初始化块来初始化映射表
    static {
        // 初始化职业名称映射
        PROFESSION_NAMES.put(Villager.Profession.NONE, "无职业");
        PROFESSION_NAMES.put(Villager.Profession.ARMORER, "盔甲匠");
        PROFESSION_NAMES.put(Villager.Profession.BUTCHER, "屠夫");
        PROFESSION_NAMES.put(Villager.Profession.CARTOGRAPHER, "制图师");
        PROFESSION_NAMES.put(Villager.Profession.CLERIC, "牧师");
        PROFESSION_NAMES.put(Villager.Profession.FARMER, "农民");
        PROFESSION_NAMES.put(Villager.Profession.FISHERMAN, "渔夫");
        PROFESSION_NAMES.put(Villager.Profession.FLETCHER, "制箭师");
        PROFESSION_NAMES.put(Villager.Profession.LEATHERWORKER, "皮匠");
        PROFESSION_NAMES.put(Villager.Profession.LIBRARIAN, "图书管理员");
        PROFESSION_NAMES.put(Villager.Profession.MASON, "石匠");
        PROFESSION_NAMES.put(Villager.Profession.NITWIT, "傻子");
        PROFESSION_NAMES.put(Villager.Profession.SHEPHERD, "牧羊人");
        PROFESSION_NAMES.put(Villager.Profession.TOOLSMITH, "工具匠");
        PROFESSION_NAMES.put(Villager.Profession.WEAPONSMITH, "武器匠");
        
        // 初始化村民类型映射
        VILLAGER_TYPE_NAMES.put(Villager.Type.DESERT, "沙漠");
        VILLAGER_TYPE_NAMES.put(Villager.Type.JUNGLE, "丛林");
        VILLAGER_TYPE_NAMES.put(Villager.Type.PLAINS, "平原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SAVANNA, "热带草原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SNOW, "雪原");
        VILLAGER_TYPE_NAMES.put(Villager.Type.SWAMP, "沼泽");
        VILLAGER_TYPE_NAMES.put(Villager.Type.TAIGA, "针叶林");
    }
    
    public static String getProfessionName(Villager.Profession profession) {
        return PROFESSION_NAMES.getOrDefault(profession, profession.toString());
    }
    
    public static String getVillagerTypeName(Villager.Type type) {
        return VILLAGER_TYPE_NAMES.getOrDefault(type, type.toString());
    }
    
    public VillagerManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerDataKey = new NamespacedKey(plugin, "villager_data");
        this.customModelData = plugin.getConfig().getInt("custom-model-data", 1000);
        this.gson = new Gson();
        this.recentlySpawnedVillagers = ConcurrentHashMap.newKeySet();
    }
    
    public ItemStack createVillagerBucket(Villager villager) {
        try {
            ItemStack bucket = new ItemStack(Material.BUCKET);
            ItemMeta meta = bucket.getItemMeta();
            
            if (meta != null) {
                // 设置显示名称
                String displayName = plugin.getConfig().getString("bucket-name", "&6村民桶")
                        .replace("&", "§");
                meta.setDisplayName(displayName);
                
                // 获取中文职业名称和类型名称
                String professionName = getProfessionName(villager.getProfession());
                String typeName = getVillagerTypeName(villager.getVillagerType());
                
                // 设置Lore信息
                List<String> lore = new ArrayList<>();
                lore.add("§7职业: " + professionName);
                lore.add("§7等级: " + villager.getVillagerLevel() + "级");
                lore.add("§7类型: " + typeName + "村民");
                
                // 添加年龄状态信息
                String ageStatus = villager.isAdult() ? "成年" : "幼年";
                lore.add("§7年龄: " + ageStatus);
                
                lore.add("§7经验: " + villager.getVillagerExperience());
                lore.add("§7交易数量: " + villager.getRecipes().size() + "个");
                lore.add("");
                lore.add("§5右键放置村民");
                
                meta.setLore(lore);
                
                // 设置自定义模型数据
                meta.setCustomModelData(customModelData);
                
                // 使用PDC保存村民数据
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String serializedData = serializeVillagerData(villager);
                if (serializedData != null && !serializedData.isEmpty()) {
                    pdc.set(villagerDataKey, PersistentDataType.STRING, serializedData);
                    bucket.setItemMeta(meta);
                    return bucket;
                } else {
                    plugin.getLogger().severe("Failed to serialize villager data");
                    return null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create villager bucket", e);
        }
        
        return null;
    }
    
    public boolean isVillagerBucket(ItemStack item) {
        if (item == null || item.getType() != Material.BUCKET || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // 检查PDC中是否有村民数据
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(villagerDataKey, PersistentDataType.STRING);
    }
    
    public Villager restoreVillagerFromBucket(ItemStack bucket, Location location) {
        if (!isVillagerBucket(bucket)) {
            return null;
        }
        
        try {
            ItemMeta meta = bucket.getItemMeta();
            if (meta == null) return null;
            
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String villagerData = pdc.get(villagerDataKey, PersistentDataType.STRING);
            
            if (villagerData == null || villagerData.isEmpty()) {
                return null;
            }
            
            return deserializeVillagerData(villagerData, location);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore villager from bucket", e);
            return null;
        }
    }
    
    /**
     * 序列化村民数据到字符串
     */
    private String serializeVillagerData(Villager villager) {
        try {
            // 使用JSON格式序列化数据
            Map<String, Object> data = new HashMap<>();
            
            // 基本属性
            data.put("profession", villager.getProfession().name());
            data.put("level", villager.getVillagerLevel());
            data.put("type", villager.getVillagerType().name());
            data.put("experience", villager.getVillagerExperience());
            data.put("ageLock", villager.getAgeLock());
            data.put("uuid", villager.getUniqueId().toString());
            data.put("customName", villager.getCustomName());
            data.put("villagerHealth", villager.getHealth());
            data.put("maxHealth", villager.getMaxHealth());
            
            // 保存年龄信息
            data.put("age", villager.getAge());
            data.put("isAdult", villager.isAdult());
            
            // 补货信息
            try {
                java.lang.reflect.Method getRestocksTodayMethod = villager.getClass().getMethod("getRestocksToday");
                java.lang.reflect.Method getLastRestockMethod = villager.getClass().getMethod("getLastRestock");
                
                int restocksToday = (Integer) getRestocksTodayMethod.invoke(villager);
                long lastRestock = (Long) getLastRestockMethod.invoke(villager);
                
                data.put("restocksToday", restocksToday);
                data.put("lastRestock", lastRestock); 
            } catch (Exception e) {
                data.put("restocksToday", 0);
                data.put("lastRestock", 0L);
            }
            
            // 序列化交易列表
            List<Map<String, Object>> recipesData = new ArrayList<>();
            for (MerchantRecipe recipe : villager.getRecipes()) {
                Map<String, Object> recipeData = serializeRecipe(recipe);
                if (recipeData != null) {
                    recipesData.add(recipeData);
                }
            }
            data.put("recipes", recipesData);
            
            // 转换为JSON字符串并Base64编码
            String jsonData = gson.toJson(data);
            return Base64.getEncoder().encodeToString(jsonData.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize villager data", e);
            return "";
        }
    }
    
    /**
     * 序列化单个交易
     */
    private Map<String, Object> serializeRecipe(MerchantRecipe recipe) {
        try {
            Map<String, Object> recipeData = new HashMap<>();
            
            // 基础信息
            recipeData.put("uses", recipe.getUses());
            recipeData.put("maxUses", recipe.getMaxUses());
            recipeData.put("experienceReward", recipe.hasExperienceReward());
            recipeData.put("villagerExperience", recipe.getVillagerExperience());
            
            // 价格信息
            try {
                java.lang.reflect.Method getPriceMultiplierMethod = recipe.getClass().getMethod("getPriceMultiplier");
                java.lang.reflect.Method getDemandMethod = recipe.getClass().getMethod("getDemand");
                java.lang.reflect.Method getSpecialPriceMethod = recipe.getClass().getMethod("getSpecialPrice");
                
                float priceMultiplier = (Float) getPriceMultiplierMethod.invoke(recipe);
                int demand = (Integer) getDemandMethod.invoke(recipe);
                int specialPrice = (Integer) getSpecialPriceMethod.invoke(recipe);
                
                recipeData.put("priceMultiplier", priceMultiplier);
                recipeData.put("demand", demand);
                recipeData.put("specialPrice", specialPrice);
            } catch (Exception e) {
                recipeData.put("priceMultiplier", 0.0f);
                recipeData.put("demand", 0);
                recipeData.put("specialPrice", 0);
            }
            
            // 序列化材料
            List<Map<String, Object>> ingredientsData = new ArrayList<>();
            for (ItemStack ingredient : recipe.getIngredients()) {
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    Map<String, Object> ingredientData = serializeItemStack(ingredient);
                    ingredientsData.add(ingredientData);
                }
            }
            recipeData.put("ingredients", ingredientsData);
            
            // 序列化结果
            ItemStack result = recipe.getResult();
            if (result != null) {
                Map<String, Object> resultData = serializeItemStack(result);
                recipeData.put("result", resultData);
            }
            
            return recipeData;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to serialize recipe", e);
            return null;
        }
    }
    
    /**
     * 序列化物品堆栈
     */
    private Map<String, Object> serializeItemStack(ItemStack item) {
        Map<String, Object> itemData = new HashMap<>();
        
        if (item == null || item.getType() == Material.AIR) {
            return itemData;
        }
        
        // 基础信息
        itemData.put("type", item.getType().name());
        itemData.put("amount", item.getAmount());
        
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            Map<String, Object> metaData = new HashMap<>();
            
            // 保存显示名称
            if (meta.hasDisplayName()) {
                metaData.put("displayName", meta.getDisplayName());
            }
            
            // 保存Lore
            if (meta.hasLore()) {
                metaData.put("lore", meta.getLore());
            }
            
            // 保存附魔信息（普通物品）
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                    // 使用完整的命名空间键，确保跨版本兼容性
                    enchants.put(enchant.getKey().getKey().toString(), enchant.getValue());
                }
                metaData.put("enchants", enchants);
            }
            
            // 保存附魔书特定信息
            if (meta instanceof EnchantmentStorageMeta) {
                EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
                if (enchantMeta.hasStoredEnchants()) {
                    Map<String, Integer> storedEnchants = new HashMap<>();
                    for (Map.Entry<Enchantment, Integer> enchant : enchantMeta.getStoredEnchants().entrySet()) {
                        // 使用完整的命名空间键，确保跨版本兼容性
                        storedEnchants.put(enchant.getKey().getKey().toString(), enchant.getValue());
                    }
                    metaData.put("storedEnchants", storedEnchants);
                    
                    // 确保附魔书元数据被正确保存
                    meta = enchantMeta;
                }
            }
            
            // 保存自定义模型数据
            if (meta.hasCustomModelData()) {
                metaData.put("customModelData", meta.getCustomModelData());
            }
            
            // 保存物品标志
            if (!meta.getItemFlags().isEmpty()) {
                List<String> itemFlags = new ArrayList<>();
                for (org.bukkit.inventory.ItemFlag flag : meta.getItemFlags()) {
                    itemFlags.add(flag.name());
                }
                metaData.put("itemFlags", itemFlags);
            }
            
            // 保存不可破坏标志（1.20.5+）
            try {
                java.lang.reflect.Method isUnbreakableMethod = meta.getClass().getMethod("isUnbreakable");
                Boolean isUnbreakable = (Boolean) isUnbreakableMethod.invoke(meta);
                if (isUnbreakable != null && isUnbreakable) {
                    metaData.put("unbreakable", true);
                }
            } catch (Exception e) {
            }
            
            itemData.put("meta", metaData);
        }
        
        return itemData;
    }
    
    /**
     * 反序列化村民数据
     */
    private Villager deserializeVillagerData(String data, Location location) {
        try {
            // Base64解码并解析JSON
            String decoded = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> villagerData = gson.fromJson(decoded, type);
            
            if (villagerData == null) {
                plugin.getLogger().warning("Failed to parse villager data: null");
                return null;
            }
            
            // 检查是否已经生成了这个村民（通过UUID）
            String uuidStr = (String) villagerData.get("uuid");
            UUID uuid = null;
            if (uuidStr != null) {
                try {
                    uuid = UUID.fromString(uuidStr);
                    if (recentlySpawnedVillagers.contains(uuid)) {
                        plugin.getLogger().warning("Villager with UUID " + uuid + " already spawned, skipping duplicate");
                        return null;
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format: " + uuidStr);
                    // 生成新的UUID
                    uuid = UUID.randomUUID();
                }
            } else {
                // 生成新的UUID
                uuid = UUID.randomUUID();
            }
            
            // 生成村民 - 使用EntityType直接生成，确保只生成一个
            Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            
            // 记录已生成的村民UUID
            recentlySpawnedVillagers.add(uuid);
            if (recentlySpawnedVillagers.size() > 1000) {
                recentlySpawnedVillagers.clear();
            }
            
            // 设置基本属性
            String professionStr = (String) villagerData.get("profession");
            if (professionStr != null) {
                try {
                    villager.setProfession(Villager.Profession.valueOf(professionStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid profession: " + professionStr);
                }
            }
            
            // 修复数字类型转换问题
            Number level = (Number) villagerData.get("level");
            if (level != null) {
                villager.setVillagerLevel(level.intValue());
            }
            
            String typeStr = (String) villagerData.get("type");
            if (typeStr != null) {
                try {
                    villager.setVillagerType(Villager.Type.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid villager type: " + typeStr);
                }
            }
            
            Number experience = (Number) villagerData.get("experience");
            if (experience != null) {
                villager.setVillagerExperience(experience.intValue());
            }
            
            Boolean ageLock = (Boolean) villagerData.get("ageLock");
            if (ageLock != null) {
                villager.setAgeLock(ageLock);
            }
            
            // 设置年龄信息
            Number age = (Number) villagerData.get("age");
            if (age != null) {
                villager.setAge(age.intValue());
            }
            
            // 设置自定义名称
            String customName = (String) villagerData.get("customName");
            if (customName != null) {
                villager.setCustomName(customName);
                villager.setCustomNameVisible(true);
            }
            
            // 设置生命值
            Number health = (Number) villagerData.get("villagerHealth");
            Number maxHealth = (Number) villagerData.get("maxHealth");
            if (health != null && maxHealth != null) {
                villager.setMaxHealth(maxHealth.doubleValue());
                villager.setHealth(health.doubleValue());
            }
            
            // 设置补货信息
            try {
                java.lang.reflect.Method setRestocksTodayMethod = villager.getClass().getMethod("setRestocksToday", int.class);
                java.lang.reflect.Method setLastRestockMethod = villager.getClass().getMethod("setLastRestock", long.class);
                
                Number restocksToday = (Number) villagerData.get("restocksToday");
                Number lastRestock = (Number) villagerData.get("lastRestock");
                
                if (restocksToday != null) {
                    setRestocksTodayMethod.invoke(villager, restocksToday.intValue());
                }
                if (lastRestock != null) {
                    setLastRestockMethod.invoke(villager, lastRestock.longValue());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set restock info", e);
            }
            
            // 恢复交易列表
            List<Map<String, Object>> recipesData = (List<Map<String, Object>>) villagerData.get("recipes");
            if (recipesData != null && !recipesData.isEmpty()) {
                List<MerchantRecipe> recipes = new ArrayList<>();
                
                for (Map<String, Object> recipeData : recipesData) {
                    MerchantRecipe recipe = deserializeRecipe(recipeData);
                    if (recipe != null) {
                        recipes.add(recipe);
                    }
                }
                
                // 设置交易列表
                if (!recipes.isEmpty()) {
                    try {
                        villager.setRecipes(recipes);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to set recipes", e);
                    }
                }
            }
            
            // 确保村民是成人（如果年龄锁定且是成人状态）
            Boolean isAdult = (Boolean) villagerData.get("isAdult");
            if (isAdult != null && isAdult) {
                villager.setAdult();
            }
            
            return villager;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize villager data", e);
            return null;
        }
    }
    
    /**
     * 反序列化单个交易
     */
    private MerchantRecipe deserializeRecipe(Map<String, Object> recipeData) {
        try {
            Number usesNum = (Number) recipeData.get("uses");
            Number maxUsesNum = (Number) recipeData.get("maxUses");
            Boolean experienceReward = (Boolean) recipeData.get("experienceReward");
            Number villagerExperienceNum = (Number) recipeData.get("villagerExperience");
            
            int uses = usesNum != null ? usesNum.intValue() : 0;
            int maxUses = maxUsesNum != null ? maxUsesNum.intValue() : 10;
            boolean expReward = experienceReward != null ? experienceReward : true;
            int villagerExperience = villagerExperienceNum != null ? villagerExperienceNum.intValue() : 1;
            
            Number priceMultiplierNum = (Number) recipeData.get("priceMultiplier");
            Number demandNum = (Number) recipeData.get("demand");
            Number specialPriceNum = (Number) recipeData.get("specialPrice");
            
            float priceMultiplier = priceMultiplierNum != null ? priceMultiplierNum.floatValue() : 0.0f;
            int demand = demandNum != null ? demandNum.intValue() : 0;
            int specialPrice = specialPriceNum != null ? specialPriceNum.intValue() : 0;
            
            // 反序列化材料
            List<ItemStack> ingredients = new ArrayList<>();
            List<Map<String, Object>> ingredientsData = (List<Map<String, Object>>) recipeData.get("ingredients");
            
            if (ingredientsData != null) {
                for (Map<String, Object> ingredientData : ingredientsData) {
                    ItemStack ingredient = deserializeItemStack(ingredientData);
                    if (ingredient != null && ingredient.getType() != Material.AIR) {
                        ingredients.add(ingredient);
                    }
                }
            }
            
            // 反序列化结果
            Map<String, Object> resultData = (Map<String, Object>) recipeData.get("result");
            ItemStack result = deserializeItemStack(resultData);
            
            if (result == null || result.getType() == Material.AIR) {
                plugin.getLogger().warning("Recipe result is null or air, using stone as fallback");
                result = new ItemStack(Material.STONE);
            }
            
            // 创建交易
            MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
            recipe.setIngredients(ingredients);
            recipe.setUses(uses);
            recipe.setExperienceReward(expReward);
            recipe.setVillagerExperience(villagerExperience);
            
            // 设置价格信息
            try {
                java.lang.reflect.Method setPriceMultiplierMethod = recipe.getClass().getMethod("setPriceMultiplier", float.class);
                java.lang.reflect.Method setDemandMethod = recipe.getClass().getMethod("setDemand", int.class);
                java.lang.reflect.Method setSpecialPriceMethod = recipe.getClass().getMethod("setSpecialPrice", int.class);
                
                setPriceMultiplierMethod.invoke(recipe, priceMultiplier);
                setDemandMethod.invoke(recipe, demand);
                setSpecialPriceMethod.invoke(recipe, specialPrice);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set recipe properties", e);
            }
            
            return recipe;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize recipe", e);
            return null;
        }
    }
    
    /**
     * 反序列化物品堆栈
     */
    private ItemStack deserializeItemStack(Map<String, Object> itemData) {
        try {
            if (itemData == null || itemData.isEmpty()) {
                return null;
            }
            
            String typeStr = (String) itemData.get("type");
            if (typeStr == null) {
                return null;
            }
            
            Material material;
            try {
                material = Material.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material type: " + typeStr);
                return null;
            }
            
            Number amountNum = (Number) itemData.get("amount");
            int amount = amountNum != null ? amountNum.intValue() : 1;
            
            ItemStack item = new ItemStack(material, amount);
            
            if (itemData.containsKey("meta")) {
                Map<String, Object> metaData = (Map<String, Object>) itemData.get("meta");
                ItemMeta meta = item.getItemMeta();
                
                if (meta != null) {
                    // 恢复显示名称
                    if (metaData.containsKey("displayName")) {
                        meta.setDisplayName((String) metaData.get("displayName"));
                    }
                    
                    // 恢复Lore
                    if (metaData.containsKey("lore")) {
                        List<String> lore = (List<String>) metaData.get("lore");
                        meta.setLore(lore);
                    }
                    
                    // 恢复附魔信息（普通物品）
                    if (metaData.containsKey("enchants")) {
                        Map<String, Number> enchants = (Map<String, Number>) metaData.get("enchants");
                        for (Map.Entry<String, Number> enchant : enchants.entrySet()) {
                            try {
                                // 尝试解析附魔键
                                Enchantment enchantment = null;
                                
                                // 首先尝试使用完整命名空间键
                                try {
                                    String[] parts = enchant.getKey().split(":");
                                    if (parts.length == 2) {
                                        enchantment = Enchantment.getByKey(NamespacedKey.fromString(enchant.getKey()));
                                    } else {
                                        // 如果没有命名空间，使用默认的minecraft命名空间
                                        enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchant.getKey()));
                                    }
                                } catch (Exception e) {
                                    // 如果解析失败，尝试使用旧方法
                                    try {
                                        enchantment = Enchantment.getByName(enchant.getKey().toUpperCase());
                                    } catch (Exception ex) {
                                        plugin.getLogger().warning("Failed to parse enchantment key: " + enchant.getKey());
                                    }
                                }
                                
                                if (enchantment != null) {
                                    meta.addEnchant(enchantment, enchant.getValue().intValue(), true);
                                } else {
                                    plugin.getLogger().warning("Unknown enchantment: " + enchant.getKey());
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to add enchantment: " + enchant.getKey());
                            }
                        }
                    }
                    
                    // 恢复附魔书特定信息
                    if (metaData.containsKey("storedEnchants") && meta instanceof EnchantmentStorageMeta) {
                        EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
                        Map<String, Number> storedEnchants = (Map<String, Number>) metaData.get("storedEnchants");
                        
                        for (Map.Entry<String, Number> enchant : storedEnchants.entrySet()) {
                            try {
                                // 尝试解析附魔键
                                Enchantment enchantment = null;
                                
                                // 首先尝试使用完整命名空间键
                                try {
                                    String[] parts = enchant.getKey().split(":");
                                    if (parts.length == 2) {
                                        enchantment = Enchantment.getByKey(NamespacedKey.fromString(enchant.getKey()));
                                    } else {
                                        // 如果没有命名空间，使用默认的minecraft命名空间
                                        enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchant.getKey()));
                                    }
                                } catch (Exception e) {
                                    // 如果解析失败，尝试使用旧方法
                                    try {
                                        enchantment = Enchantment.getByName(enchant.getKey().toUpperCase());
                                    } catch (Exception ex) {
                                        plugin.getLogger().warning("Failed to parse stored enchantment key: " + enchant.getKey());
                                    }
                                }
                                
                                if (enchantment != null) {
                                    enchantMeta.addStoredEnchant(enchantment, enchant.getValue().intValue(), true);
                                } else {
                                    plugin.getLogger().warning("Unknown stored enchantment: " + enchant.getKey());
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to add stored enchantment: " + enchant.getKey());
                            }
                        }
                        meta = enchantMeta;
                    }
                    
                    // 恢复自定义模型数据
                    if (metaData.containsKey("customModelData")) {
                        Number customModelDataNum = (Number) metaData.get("customModelData");
                        if (customModelDataNum != null) {
                            meta.setCustomModelData(customModelDataNum.intValue());
                        }
                    }
                    
                    // 恢复物品标志
                    if (metaData.containsKey("itemFlags")) {
                        List<String> itemFlags = (List<String>) metaData.get("itemFlags");
                        for (String flagName : itemFlags) {
                            try {
                                org.bukkit.inventory.ItemFlag flag = org.bukkit.inventory.ItemFlag.valueOf(flagName);
                                meta.addItemFlags(flag);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Unknown item flag: " + flagName);
                            }
                        }
                    }
                    
                    // 恢复不可破坏标志（1.20.5+）
                    if (metaData.containsKey("unbreakable")) {
                        Boolean unbreakable = (Boolean) metaData.get("unbreakable");
                        if (unbreakable != null && unbreakable) {
                            try {
                                java.lang.reflect.Method setUnbreakableMethod = meta.getClass().getMethod("setUnbreakable", boolean.class);
                                setUnbreakableMethod.invoke(meta, true);
                            } catch (Exception e) {
                            }
                        }
                    }
                    
                    item.setItemMeta(meta);
                }
            }
            
            return item;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize item", e);
            return null;
        }
    }
    
    public NamespacedKey getVillagerDataKey() {
        return villagerDataKey;
    }
    
    public int getCustomModelData() {
        return customModelData;
    }
}