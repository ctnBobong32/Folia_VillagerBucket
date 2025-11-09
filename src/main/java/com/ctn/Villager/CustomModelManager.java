package com.ctn.Villager;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义模型数据管理器
 * 独立管理村民桶的自定义模型数据
 */
public class CustomModelManager {
    private final VillagerBucketPlugin plugin;
    private final Map<String, Integer> professionModelMap;
    private final Map<String, Integer> typeModelMap;
    private int defaultModelData;
    
    public CustomModelManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.professionModelMap = new HashMap<>();
        this.typeModelMap = new HashMap<>();
        initializeModelData();
    }
    
    /**
     * 初始化模型数据映射
     */
    private void initializeModelData() {
        // 从配置加载默认模型数据
        this.defaultModelData = plugin.getConfig().getInt("settings.custom-model-data", 1000);
        
        // 职业模型数据映射
        professionModelMap.put("none", defaultModelData);
        professionModelMap.put("farmer", defaultModelData + 1);
        professionModelMap.put("librarian", defaultModelData + 2);
        professionModelMap.put("armorer", defaultModelData + 3);
        professionModelMap.put("butcher", defaultModelData + 4);
        professionModelMap.put("cartographer", defaultModelData + 5);
        professionModelMap.put("cleric", defaultModelData + 6);
        professionModelMap.put("fisherman", defaultModelData + 7);
        professionModelMap.put("fletcher", defaultModelData + 8);
        professionModelMap.put("leatherworker", defaultModelData + 9);
        professionModelMap.put("mason", defaultModelData + 10);
        professionModelMap.put("shepherd", defaultModelData + 11);
        professionModelMap.put("toolsmith", defaultModelData + 12);
        professionModelMap.put("weaponsmith", defaultModelData + 13);
        professionModelMap.put("nitwit", defaultModelData + 14);
        
        // 类型模型数据映射
        typeModelMap.put("plains", defaultModelData + 100);
        typeModelMap.put("desert", defaultModelData + 101);
        typeModelMap.put("jungle", defaultModelData + 102);
        typeModelMap.put("savanna", defaultModelData + 103);
        typeModelMap.put("snow", defaultModelData + 104);
        typeModelMap.put("swamp", defaultModelData + 105);
        typeModelMap.put("taiga", defaultModelData + 106);
    }
    
    /**
     * 获取职业对应的模型数据
     */
    public int getProfessionModelData(String profession) {
        return professionModelMap.getOrDefault(profession.toLowerCase(), defaultModelData);
    }
    
    /**
     * 获取类型对应的模型数据
     */
    public int getTypeModelData(String type) {
        return typeModelMap.getOrDefault(type.toLowerCase(), defaultModelData + 100);
    }
    
    /**
     * 获取组合模型数据（职业+类型）
     */
    public int getCombinedModelData(String profession, String type) {
        int professionModel = getProfessionModelData(profession);
        int typeModel = getTypeModelData(type);
        
        // 组合算法：职业模型数据 + 类型偏移量
        return professionModel + (typeModel - defaultModelData - 100);
    }
    
    /**
     * 应用自定义模型数据到物品
     */
    public void applyModelData(ItemStack item, String profession, String type) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        
        int modelData = getCombinedModelData(profession, type);
        meta.setCustomModelData(modelData);
        item.setItemMeta(meta);
    }
    
    /**
     * 从物品获取模型数据
     */
    public Integer getModelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
    }
    
    /**
     * 检查物品是否使用自定义模型数据
     */
    public boolean hasCustomModelData(ItemStack item) {
        Integer modelData = getModelData(item);
        return modelData != null && modelData >= defaultModelData && modelData < defaultModelData + 200;
    }
    
    /**
     * 获取默认模型数据
     */
    public int getDefaultModelData() {
        return defaultModelData;
    }
    
    /**
     * 重新加载模型数据配置
     */
    public void reload() {
        professionModelMap.clear();
        typeModelMap.clear();
        initializeModelData();
    }
}