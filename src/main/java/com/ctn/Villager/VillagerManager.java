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
    private final Gson gson;
    private final Set<UUID> recentlySpawnedVillagers;
    
    // 职业名称映射表
    public static final Map<String, String> PROFESSION_NAMES = new HashMap<>();
    // 村民类型映射表
    public static final Map<String, String> VILLAGER_TYPE_NAMES = new HashMap<>();
    
    // 使用静态初始化块来初始化映射表
    static {
        // 初始化职业名称映射 - 使用key值而不是枚举
        PROFESSION_NAMES.put("none", "无职业");
        PROFESSION_NAMES.put("armorer", "盔甲匠");
        PROFESSION_NAMES.put("butcher", "屠夫");
        PROFESSION_NAMES.put("cartographer", "制图师");
        PROFESSION_NAMES.put("cleric", "牧师");
        PROFESSION_NAMES.put("farmer", "农民");
        PROFESSION_NAMES.put("fisherman", "渔夫");
        PROFESSION_NAMES.put("fletcher", "制箭师");
        PROFESSION_NAMES.put("leatherworker", "皮匠");
        PROFESSION_NAMES.put("librarian", "图书管理员");
        PROFESSION_NAMES.put("mason", "石匠");
        PROFESSION_NAMES.put("nitwit", "傻子");
        PROFESSION_NAMES.put("shepherd", "牧羊人");
        PROFESSION_NAMES.put("toolsmith", "工具匠");
        PROFESSION_NAMES.put("weaponsmith", "武器匠");
        
        // 初始化村民类型映射 - 使用key值而不是枚举
        VILLAGER_TYPE_NAMES.put("desert", "沙漠");
        VILLAGER_TYPE_NAMES.put("jungle", "丛林");
        VILLAGER_TYPE_NAMES.put("plains", "平原");
        VILLAGER_TYPE_NAMES.put("savanna", "热带草原");
        VILLAGER_TYPE_NAMES.put("snow", "雪原");
        VILLAGER_TYPE_NAMES.put("swamp", "沼泽");
        VILLAGER_TYPE_NAMES.put("taiga", "针叶林");
    }
    
    public static String getProfessionName(Villager.Profession profession) {
        String key = profession.key().value();
        return PROFESSION_NAMES.getOrDefault(key, key);
    }
    
    public static String getProfessionName(String professionKey) {
        return PROFESSION_NAMES.getOrDefault(professionKey, professionKey);
    }
    
    public static String getVillagerTypeName(Villager.Type type) {
        String key = type.key().value();
        return VILLAGER_TYPE_NAMES.getOrDefault(key, key);
    }
    
    public static String getVillagerTypeName(String typeKey) {
        return VILLAGER_TYPE_NAMES.getOrDefault(typeKey, typeKey);
    }
    
    public VillagerManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerDataKey = new NamespacedKey(plugin, "villager_data");
        this.gson = new Gson();
        this.recentlySpawnedVillagers = ConcurrentHashMap.newKeySet();
    }
    
    public ItemStack createVillagerBucket(Villager villager) {
        try {
            ItemStack bucket = new ItemStack(Material.BUCKET);
            
            ItemMeta meta = bucket.getItemMeta();
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(Material.BUCKET);
                if (meta == null) {
                    plugin.getLogger().warning("无法创建ItemMeta，使用默认桶");
                    return new ItemStack(Material.BUCKET);
                }
            }
            
            // 设置显示名称
            String displayName = ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("bucket.name", "&6村民桶"));
            meta.setDisplayName(displayName);
            
            // 获取中文职业名称和类型名称
            String professionName = getProfessionName(villager.getProfession());
            String typeName = getVillagerTypeName(villager.getVillagerType());
            String ageStatus = villager.isAdult() ? "成年" : "幼年";
            
            // 设置Lore信息
            List<String> lore = new ArrayList<>();
            List<String> loreTemplate = plugin.getConfig().getStringList("bucket.lore");
            
            if (loreTemplate.isEmpty()) {
                // 默认Lore
                lore.add("§7包含一个被捕捉的村民");
                lore.add("§7右键放置村民");
                lore.add("");
                lore.add("§e职业: " + professionName);
                lore.add("§e等级: " + villager.getVillagerLevel());
                lore.add("§e类型: " + typeName);
                lore.add("§e年龄: " + ageStatus);
                lore.add("§e经验: " + villager.getVillagerExperience());
            } else {
                for (String line : loreTemplate) {
                    String processedLine = line
                        .replace("{profession}", professionName)
                        .replace("{level}", String.valueOf(villager.getVillagerLevel()))
                        .replace("{type}", typeName)
                        .replace("{age}", ageStatus)
                        .replace("{experience}", String.valueOf(villager.getVillagerExperience()))
                        .replace("{recipe-count}", String.valueOf(villager.getRecipes().size()));
                    lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
                }
            }
            
            meta.setLore(lore);
            
            // 设置自定义模型数据 - 所有村民桶使用同一个值
            int customModelData = plugin.getConfig().getInt("settings.custom-model-data", 1000);
            meta.setCustomModelData(customModelData);
            
            // 使用PDC保存村民数据
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String serializedData = serializeVillagerData(villager);
            if (serializedData != null && !serializedData.isEmpty()) {
                pdc.set(villagerDataKey, PersistentDataType.STRING, serializedData);
                bucket.setItemMeta(meta);
                
                plugin.debug("成功创建村民桶: " + professionName + " " + typeName);
                return bucket;
            } else {
                plugin.getLogger().severe("序列化村民数据失败");
                return null;
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建村民桶时发生错误", e);
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
    
    /**
     * 验证村民桶数据的完整性
     */
    public boolean isValidVillagerBucket(ItemStack bucket) {
        if (!isVillagerBucket(bucket)) {
            return false;
        }
        
        try {
            ItemMeta meta = bucket.getItemMeta();
            if (meta == null) return false;
            
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String villagerData = pdc.get(villagerDataKey, PersistentDataType.STRING);
            
            if (villagerData == null || villagerData.isEmpty()) {
                return false;
            }
            
            // 尝试解码和解析数据
            String decoded = new String(Base64.getDecoder().decode(villagerData), StandardCharsets.UTF_8);
            Type typeTokenType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = gson.fromJson(decoded, typeTokenType);
            
            // 检查必需字段
            return data != null && 
                   data.containsKey("profession") && 
                   data.containsKey("level") && 
                   data.containsKey("type");
        } catch (Exception e) {
            plugin.debug("村民桶数据验证失败: " + e.getMessage());
            return false;
        }
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
            
            Villager villager = deserializeVillagerData(villagerData, location);
            if (villager != null) {
                plugin.debug("成功从村民桶恢复村民: " + getProfessionName(villager.getProfession()));
                
                // 标记为最近生成的村民，防止立即消失
                recentlySpawnedVillagers.add(villager.getUniqueId());
                
                // 延迟清理标记
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    recentlySpawnedVillagers.remove(villager.getUniqueId());
                }, 100L); // 5秒后移除标记
            }
            return villager;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从村民桶恢复村民时发生错误", e);
            return null;
        }
    }
    
    /**
     * 检查是否是最近生成的村民
     */
    public boolean isRecentlySpawnedVillager(UUID villagerId) {
        return recentlySpawnedVillagers.contains(villagerId);
    }
    
    /**
     * 序列化村民数据到字符串
     */
    private String serializeVillagerData(Villager villager) {
        try {
            // 使用JSON格式序列化数据
            Map<String, Object> data = new HashMap<>();
            
            // 基本属性
            data.put("profession", villager.getProfession().key().value());
            data.put("level", villager.getVillagerLevel());
            data.put("type", villager.getVillagerType().key().value());
            
            // 根据配置决定是否保存经验
            if (plugin.getConfig().getBoolean("settings.save-experience", true)) {
                data.put("experience", villager.getVillagerExperience());
            }
            
            data.put("ageLock", villager.getAgeLock());
            
            // 使用村民的原始UUID，确保数据一致性
            data.put("uuid", villager.getUniqueId().toString());
            
            data.put("customName", villager.getCustomName());
            data.put("villagerHealth", villager.getHealth());
            data.put("maxHealth", villager.getMaxHealth());
            
            // 保存年龄信息
            data.put("age", villager.getAge());
            data.put("isAdult", villager.isAdult());
            
            // 补货信息 - 根据配置决定是否保存
            if (plugin.getConfig().getBoolean("settings.save-restock-info", true)) {
                try {
                    java.lang.reflect.Method getRestocksTodayMethod = villager.getClass().getMethod("getRestocksToday");
                    java.lang.reflect.Method getLastRestockMethod = villager.getClass().getMethod("getLastRestock");
                    
                    int restocksToday = (Integer) getRestocksTodayMethod.invoke(villager);
                    long lastRestock = (Long) getLastRestockMethod.invoke(villager);
                    
                    data.put("restocksToday", restocksToday);
                    data.put("lastRestock", lastRestock); 
                } catch (Exception e) {
                    // 这些字段不是必需的，忽略错误
                    plugin.debug("补货方法不可用，跳过...");
                }
            }
            
            // 序列化交易列表 - 根据配置决定是否保存
            if (plugin.getConfig().getBoolean("settings.save-trades", true)) {
                List<Map<String, Object>> recipesData = new ArrayList<>();
                for (MerchantRecipe recipe : villager.getRecipes()) {
                    Map<String, Object> recipeData = serializeRecipe(recipe);
                    if (recipeData != null) {
                        recipesData.add(recipeData);
                    }
                }
                data.put("recipes", recipesData);
            }
            
            // 转换为JSON字符串并Base64编码
            String jsonData = gson.toJson(data);
            return Base64.getEncoder().encodeToString(jsonData.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "序列化村民数据时发生错误", e);
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
                plugin.debug("交易价格方法不可用，跳过...");
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
            plugin.getLogger().log(Level.WARNING, "序列化交易时发生错误", e);
            return null;
        }
    }
    
    /**
     * 序列化物品堆栈 - 修复附魔保存
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
            
            // 保存附魔信息（普通物品）- 修复兼容性问题
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                    // 使用key的字符串表示，确保版本兼容性
                    String enchantKey = enchant.getKey().getKey().toString();
                    enchants.put(enchantKey, enchant.getValue());
                }
                metaData.put("enchants", enchants);
            }
            
            // 保存附魔书特定信息 - 修复附魔书附魔丢失问题
            if (meta instanceof EnchantmentStorageMeta) {
                EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
                if (enchantMeta.hasStoredEnchants()) {
                    Map<String, Integer> storedEnchants = new HashMap<>();
                    for (Map.Entry<Enchantment, Integer> enchant : enchantMeta.getStoredEnchants().entrySet()) {
                        // 使用key的字符串表示，确保版本兼容性
                        String enchantKey = enchant.getKey().getKey().toString();
                        storedEnchants.put(enchantKey, enchant.getValue());
                    }
                    metaData.put("storedEnchants", storedEnchants);
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
            
            // 保存不可破坏标志 - 使用兼容性方法
            try {
                if (meta.isUnbreakable()) {
                    metaData.put("unbreakable", true);
                }
            } catch (NoSuchMethodError e) {
                // 旧版本兼容
                try {
                    java.lang.reflect.Method isUnbreakableMethod = meta.getClass().getMethod("isUnbreakable");
                    Boolean isUnbreakable = (Boolean) isUnbreakableMethod.invoke(meta);
                    if (isUnbreakable != null && isUnbreakable) {
                        metaData.put("unbreakable", true);
                    }
                } catch (Exception ex) {
                    // 忽略异常
                }
            }
            
            itemData.put("meta", metaData);
        }
        
        return itemData;
    }
    
    /**
     * 反序列化村民数据 - 修复职业村民消失问题
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Villager deserializeVillagerData(String data, Location location) {
        try {
            // Base64解码并解析JSON
            String decoded = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            Type typeTokenType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> villagerData = gson.fromJson(decoded, typeTokenType);
            
            if (villagerData == null) {
                plugin.debug("解析村民数据失败: null");
                return null;
            }
            
            // 生成村民
            Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            
            // 立即设置基本属性，防止村民消失
            String professionStr = (String) villagerData.get("profession");
            if (professionStr != null) {
                Villager.Profession profession = getProfessionFromString(professionStr);
                if (profession != null) {
                    villager.setProfession(profession);
                    plugin.debug("设置村民职业: " + professionStr);
                } else {
                    villager.setProfession(getDefaultProfession());
                    plugin.debug("使用默认职业: " + getDefaultProfession());
                }
            }
            
            String typeStr = (String) villagerData.get("type");
            if (typeStr != null) {
                Villager.Type villagerType = getVillagerTypeFromString(typeStr);
                if (villagerType != null) {
                    villager.setVillagerType(villagerType);
                    plugin.debug("设置村民类型: " + typeStr);
                } else {
                    villager.setVillagerType(getDefaultVillagerType());
                }
            }
            
            // 设置等级和经验
            Number level = (Number) villagerData.get("level");
            if (level != null) {
                villager.setVillagerLevel(level.intValue());
            } else {
                villager.setVillagerLevel(1);
            }
            
            // 根据配置决定是否恢复经验
            if (plugin.getConfig().getBoolean("settings.save-experience", true)) {
                Number experience = (Number) villagerData.get("experience");
                if (experience != null) {
                    villager.setVillagerExperience(experience.intValue());
                }
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
                double healthValue = Math.min(health.doubleValue(), maxHealth.doubleValue());
                villager.setHealth(healthValue);
            } else {
                villager.setHealth(villager.getMaxHealth());
            }
            
            // 恢复交易列表 - 根据配置决定是否恢复
            if (plugin.getConfig().getBoolean("settings.save-trades", true)) {
                List<Map<String, Object>> recipesData = (List<Map<String, Object>>) villagerData.get("recipes");
                if (recipesData != null && !recipesData.isEmpty()) {
                    List<MerchantRecipe> recipes = new ArrayList<>();
                    
                    for (Map<String, Object> recipeData : recipesData) {
                        MerchantRecipe recipe = deserializeRecipe(recipeData);
                        if (recipe != null) {
                            recipes.add(recipe);
                        }
                    }
                    
                    if (!recipes.isEmpty()) {
                        try {
                            villager.setRecipes(recipes);
                            plugin.debug("恢复交易列表，数量: " + recipes.size());
                        } catch (Exception e) {
                            plugin.debug("设置交易列表失败: " + e.getMessage());
                        }
                    }
                }
            }
            
            // 确保村民是成人
            Boolean isAdult = (Boolean) villagerData.get("isAdult");
            if (isAdult != null && isAdult) {
                villager.setAdult();
            }
            
            // 确保村民有AI
            villager.setAI(true);
            villager.setAware(true);
            
            // 防止村民立即消失的关键设置
            villager.setRemoveWhenFarAway(false);
            
            plugin.debug("村民恢复完成: " + getProfessionName(villager.getProfession()) + 
                        ", 等级: " + villager.getVillagerLevel() + 
                        ", 交易: " + villager.getRecipes().size());
            
            return villager;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "反序列化村民数据时发生错误", e);
            return null;
        }
    }
    
    /**
     * 从字符串获取村民职业枚举值 - 修复弃用警告
     */
    private Villager.Profession getProfessionFromString(String professionStr) {
        try {
            // 使用key匹配而不是枚举值
            for (Villager.Profession profession : getProfessions()) {
                if (profession.key().value().equalsIgnoreCase(professionStr)) {
                    return profession;
                }
            }
            
            // 处理minecraft:前缀
            if (professionStr.startsWith("minecraft:")) {
                String simpleName = professionStr.substring(10);
                for (Villager.Profession profession : getProfessions()) {
                    if (profession.key().value().equalsIgnoreCase(simpleName)) {
                        return profession;
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("解析职业失败: " + professionStr);
        }
        
        return getDefaultProfession();
    }
    
    /**
     * 从字符串获取村民类型枚举值 - 修复弃用警告
     */
    private Villager.Type getVillagerTypeFromString(String typeStr) {
        try {
            // 使用key匹配而不是枚举值
            for (Villager.Type type : getVillagerTypes()) {
                if (type.key().value().equalsIgnoreCase(typeStr)) {
                    return type;
                }
            }
            
            // 处理minecraft:前缀
            if (typeStr.startsWith("minecraft:")) {
                String simpleName = typeStr.substring(10);
                for (Villager.Type type : getVillagerTypes()) {
                    if (type.key().value().equalsIgnoreCase(simpleName)) {
                        return type;
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("解析村民类型失败: " + typeStr);
        }
        
        return getDefaultVillagerType();
    }
    
    /**
     * 获取所有职业 - 避免使用已弃用的values()方法
     */
    private Villager.Profession[] getProfessions() {
        // 使用硬编码的职业列表避免弃用警告
        return new Villager.Profession[]{
            Villager.Profession.NONE,
            Villager.Profession.ARMORER,
            Villager.Profession.BUTCHER,
            Villager.Profession.CARTOGRAPHER,
            Villager.Profession.CLERIC,
            Villager.Profession.FARMER,
            Villager.Profession.FISHERMAN,
            Villager.Profession.FLETCHER,
            Villager.Profession.LEATHERWORKER,
            Villager.Profession.LIBRARIAN,
            Villager.Profession.MASON,
            Villager.Profession.NITWIT,
            Villager.Profession.SHEPHERD,
            Villager.Profession.TOOLSMITH,
            Villager.Profession.WEAPONSMITH
        };
    }
    
    /**
     * 获取所有村民类型 - 避免使用已弃用的values()方法
     */
    private Villager.Type[] getVillagerTypes() {
        // 使用硬编码的类型列表避免弃用警告
        return new Villager.Type[]{
            Villager.Type.DESERT,
            Villager.Type.JUNGLE,
            Villager.Type.PLAINS,
            Villager.Type.SAVANNA,
            Villager.Type.SNOW,
            Villager.Type.SWAMP,
            Villager.Type.TAIGA
        };
    }
    
    /**
     * 获取默认职业
     */
    private Villager.Profession getDefaultProfession() {
        return Villager.Profession.NONE;
    }
    
    /**
     * 获取默认村民类型
     */
    private Villager.Type getDefaultVillagerType() {
        return Villager.Type.PLAINS;
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
            
            if (ingredients.isEmpty()) {
                ingredients.add(new ItemStack(Material.EMERALD, 1));
            }
            
            // 反序列化结果
            Map<String, Object> resultData = (Map<String, Object>) recipeData.get("result");
            ItemStack result = deserializeItemStack(resultData);
            
            if (result == null || result.getType() == Material.AIR) {
                result = new ItemStack(Material.STONE);
            }
            
            // 创建交易
            MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
            recipe.setIngredients(ingredients);
            recipe.setUses(uses);
            recipe.setExperienceReward(expReward);
            recipe.setVillagerExperience(villagerExperience);
            
            return recipe;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "反序列化交易时发生错误", e);
            return null;
        }
    }
    
    /**
     * 反序列化物品堆栈 - 修复附魔恢复
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
                plugin.getLogger().warning("未知的物品类型: " + typeStr);
                return new ItemStack(Material.STONE);
            }
            
            Number amountNum = (Number) itemData.get("amount");
            int amount = amountNum != null ? Math.max(1, amountNum.intValue()) : 1;
            
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
                        @SuppressWarnings("unchecked")
                        List<String> lore = (List<String>) metaData.get("lore");
                        meta.setLore(lore);
                    }
                    
                    // 恢复附魔信息（普通物品）- 修复兼容性问题
                    if (metaData.containsKey("enchants")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Number> enchants = (Map<String, Number>) metaData.get("enchants");
                        for (Map.Entry<String, Number> enchant : enchants.entrySet()) {
                            try {
                                // 使用NamespacedKey从字符串获取附魔
                                String enchantKeyStr = enchant.getKey();
                                Enchantment enchantment = getEnchantmentByKey(enchantKeyStr);
                                if (enchantment != null) {
                                    meta.addEnchant(enchantment, enchant.getValue().intValue(), true);
                                } else {
                                    plugin.debug("找不到附魔: " + enchantKeyStr);
                                }
                            } catch (Exception e) {
                                plugin.debug("恢复附魔失败: " + enchant.getKey() + " - " + e.getMessage());
                            }
                        }
                    }
                    
                    // 恢复附魔书特定信息 - 修复附魔书附魔丢失问题
                    if (metaData.containsKey("storedEnchants") && meta instanceof EnchantmentStorageMeta) {
                        EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
                        @SuppressWarnings("unchecked")
                        Map<String, Number> storedEnchants = (Map<String, Number>) metaData.get("storedEnchants");
                        
                        for (Map.Entry<String, Number> enchant : storedEnchants.entrySet()) {
                            try {
                                // 使用NamespacedKey从字符串获取附魔
                                String enchantKeyStr = enchant.getKey();
                                Enchantment enchantment = getEnchantmentByKey(enchantKeyStr);
                                if (enchantment != null) {
                                    enchantMeta.addStoredEnchant(enchantment, enchant.getValue().intValue(), true);
                                } else {
                                    plugin.debug("找不到存储附魔: " + enchantKeyStr);
                                }
                            } catch (Exception e) {
                                plugin.debug("恢复存储附魔失败: " + enchant.getKey() + " - " + e.getMessage());
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
                        @SuppressWarnings("unchecked")
                        List<String> itemFlags = (List<String>) metaData.get("itemFlags");
                        for (String flagName : itemFlags) {
                            try {
                                org.bukkit.inventory.ItemFlag flag = org.bukkit.inventory.ItemFlag.valueOf(flagName);
                                meta.addItemFlags(flag);
                            } catch (IllegalArgumentException e) {
                                plugin.debug("未知的物品标志: " + flagName);
                            }
                        }
                    }
                    
                    // 恢复不可破坏标志
                    if (metaData.containsKey("unbreakable")) {
                        Boolean unbreakable = (Boolean) metaData.get("unbreakable");
                        if (unbreakable != null && unbreakable) {
                            try {
                                meta.setUnbreakable(true);
                            } catch (NoSuchMethodError e) {
                                // 旧版本兼容
                                try {
                                    java.lang.reflect.Method setUnbreakableMethod = meta.getClass().getMethod("setUnbreakable", boolean.class);
                                    setUnbreakableMethod.invoke(meta, true);
                                } catch (Exception ex) {
                                    // 忽略异常
                                }
                            }
                        }
                    }
                    
                    item.setItemMeta(meta);
                }
            }
            
            return item;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "反序列化物品时发生错误", e);
            return new ItemStack(Material.STONE);
        }
    }
    
    /**
     * 通过NamespacedKey字符串获取附魔
     */
    private Enchantment getEnchantmentByKey(String enchantKeyStr) {
        try {
            // 处理完整的NamespacedKey字符串（如"minecraft:sharpness"）
            if (enchantKeyStr.contains(":")) {
                String[] parts = enchantKeyStr.split(":", 2);
                NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                return Enchantment.getByKey(key);
            } 
            // 处理传统名称（如"SHARPNESS"）
            else {
                // 先尝试通过传统名称获取
                Enchantment enchantment = Enchantment.getByName(enchantKeyStr.toUpperCase());
                if (enchantment != null) {
                    return enchantment;
                }
                // 如果传统名称失败，尝试添加minecraft:前缀
                NamespacedKey key = new NamespacedKey("minecraft", enchantKeyStr.toLowerCase());
                return Enchantment.getByKey(key);
            }
        } catch (Exception e) {
            plugin.debug("获取附魔失败: " + enchantKeyStr + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (recentlySpawnedVillagers != null) {
            recentlySpawnedVillagers.clear();
        }
        plugin.debug("村民管理器资源已清理");
    }
    
    public NamespacedKey getVillagerDataKey() {
        return villagerDataKey;
    }
}