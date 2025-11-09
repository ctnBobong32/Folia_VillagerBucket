package com.ctn.Villager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ClaimPluginManager {
    private final VillagerBucketPlugin plugin;
    private final Logger logger;
    private boolean residenceEnabled = false;
    private boolean dominionEnabled = false;
    private int retryCount = 0;
    private final int maxRetries = 30;
    
    private Plugin residencePlugin = null;
    private Plugin dominionPlugin = null;
    
    // Dominion API 缓存
    private Object dominionAPIInstance = null;
    private boolean dominionAPIVerified = false;
    private Method checkPrivilegeFlagSilenceMethod = null;
    private Class<?> priFlagClass = null;
    private Object buildFlag = null;
    private Object destroyFlag = null;
    
    public ClaimPluginManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initialDetection();
    }
    
    private void initialDetection() {
        logger.info("进行初次领地插件检测...");
        detectClaimPlugins();
        
        if (!dominionEnabled && retryCount < maxRetries) {
            startRetryCycle();
        }
    }
    
    private void startRetryCycle() {
        plugin.getScheduler().runAsyncLater(() -> {
            retryCount++;
            logger.info("第 " + retryCount + " 次重试检测领地插件...");
            detectClaimPlugins();
            
            if (!dominionEnabled && retryCount < maxRetries) {
                startRetryCycle();
            } else if (retryCount >= maxRetries) {
                logger.info("已达到最大重试次数 (" + maxRetries + ")，停止检测 Dominion 插件");
            }
        }, 400L);
    }
    
    private void detectClaimPlugins() {
        PluginManager pm = plugin.getServer().getPluginManager();
        
        List<Plugin> enabledPlugins = new ArrayList<>();
        for (Plugin p : pm.getPlugins()) {
            if (p.isEnabled()) {
                enabledPlugins.add(p);
            }
        }
        
        boolean previousResidence = residenceEnabled;
        boolean previousDominion = dominionEnabled;
        
        detectResidence(pm, enabledPlugins);
        detectDominion(pm, enabledPlugins);
        
        if (residenceEnabled != previousResidence || dominionEnabled != previousDominion) {
            logger.info("领地插件状态更新: " + getDetailedStatus());
            
            if (!previousDominion && dominionEnabled) {
                verifyDominionFunctionality();
            }
        }
        
        if (!residenceEnabled && !dominionEnabled && retryCount == 0) {
            logger.info("未检测到领地插件，领地保护功能未启用");
        } else if ((residenceEnabled || dominionEnabled) && retryCount == 0) {
            logger.info("领地保护功能已启用");
        }
    }
    
    /**
     * 检测Residence插件
     */
    private void detectResidence(PluginManager pm, List<Plugin> enabledPlugins) {
        residencePlugin = pm.getPlugin("Residence");
        if (residencePlugin != null && residencePlugin.isEnabled()) {
            residenceEnabled = true;
            return;
        }
        
        for (Plugin p : enabledPlugins) {
            String name = p.getName().toLowerCase();
            if (name.contains("residence")) {
                residencePlugin = p;
                residenceEnabled = true;
                return;
            }
        }
        
        residenceEnabled = false;
    }
    
    /**
     * 检测Dominion插件
     */
    private void detectDominion(PluginManager pm, List<Plugin> enabledPlugins) {
        dominionPlugin = pm.getPlugin("Dominion");
        if (dominionPlugin != null && dominionPlugin.isEnabled()) {
            dominionEnabled = true;
            return;
        }
        
        for (Plugin p : enabledPlugins) {
            String name = p.getName().toLowerCase();
            if (name.contains("dominion")) {
                dominionPlugin = p;
                dominionEnabled = true;
                return;
            }
        }
        
        for (Plugin p : enabledPlugins) {
            String mainClass = p.getDescription().getMain();
            if (mainClass != null && mainClass.toLowerCase().contains("dominion")) {
                dominionPlugin = p;
                dominionEnabled = true;
                return;
            }
        }
        
        // 通过类存在性检测
        if (checkDominionClassExists()) {
            dominionEnabled = true;
        }
    }
    
    private boolean checkDominionClassExists() {
        try {
            Class.forName("cn.lunadeer.dominion.Dominion");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 验证 Dominion 功能 - 修复 PriFlag 问题
     */
    private void verifyDominionFunctionality() {
        logger.info("验证 Dominion 插件功能...");
        
        try {
            // 方法1: 尝试通过 Dominion 主类获取实例
            try {
                Class<?> dominionClass = Class.forName("cn.lunadeer.dominion.Dominion");
                Field instanceField = dominionClass.getDeclaredField("instance");
                Object dominionInstance = instanceField.get(null);
                
                if (dominionInstance != null) {
                    // 通过 DominionInterface 获取 API 实例
                    Class<?> dominionInterfaceClass = Class.forName("cn.lunadeer.dominion.DominionInterface");
                    Field apiInstanceField = dominionInterfaceClass.getDeclaredField("instance");
                    dominionAPIInstance = apiInstanceField.get(null);
                    
                    if (dominionAPIInstance != null) {
                        logger.info("成功通过 DominionInterface 获取实例");
                        setupDominionAPI(dominionInterfaceClass);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.info("通过 Dominion 主类获取实例失败: " + e.getMessage());
            }
            
            // 方法2: 直接创建 DominionInterface 实例
            try {
                Class<?> dominionInterfaceClass = Class.forName("cn.lunadeer.dominion.DominionInterface");
                dominionAPIInstance = dominionInterfaceClass.newInstance();
                logger.info("成功创建 DominionInterface 实例");
                setupDominionAPI(dominionInterfaceClass);
                return;
            } catch (Exception e) {
                logger.info("创建 DominionInterface 实例失败: " + e.getMessage());
            }
            
            logger.warning("所有 Dominion API 获取方法都失败了");
            
        } catch (Exception e) {
            logger.warning("Dominion 功能验证失败: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 设置 Dominion API 相关资源 - 修复 PriFlag 问题
     */
    private void setupDominionAPI(Class<?> apiClass) {
        try {
            // 获取 PriFlag 类
            priFlagClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.PriFlag");
            logger.info("成功加载 PriFlag 类");
            
            // 获取检查权限的方法
            checkPrivilegeFlagSilenceMethod = apiClass.getMethod("checkPrivilegeFlagSilence", 
                Location.class, priFlagClass, Player.class);
            logger.info("成功获取 checkPrivilegeFlagSilence 方法");
            
            // 现在尝试获取 BUILD 和 DESTROY 标志
            if (!loadPriFlags()) {
                logger.warning("无法加载 PriFlag 实例，将使用字符串方式调用");
                // 即使无法加载标志，我们仍然可以尝试使用其他方法
                dominionAPIVerified = true;
            } else {
                dominionAPIVerified = true;
                logger.info("Dominion API 设置成功，领地保护功能已完全激活");
            }
            
        } catch (Exception e) {
            logger.warning("设置 Dominion API 时出错: " + e.getMessage());
            // 即使部分设置失败，我们仍然尝试继续
            dominionAPIVerified = true;
        }
    }
    
    /**
     * 加载 PriFlag 实例 - 使用反射获取实际的标志对象
     */
    private boolean loadPriFlags() {
        try {
            // 方法1: 尝试通过 FlagCache 或类似的管理器获取标志
            if (loadFlagsFromManager()) {
                return true;
            }
            
            // 方法2: 尝试通过配置类获取标志
            if (loadFlagsFromConfig()) {
                return true;
            }
            
            // 方法3: 尝试直接创建标志实例（最后的手段）
            if (createFlagsDirectly()) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warning("加载 PriFlag 实例时出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 通过管理器类获取标志
     */
    private boolean loadFlagsFromManager() {
        try {
            // 尝试常见的标志管理器类路径
            String[] managerClasses = {
                "cn.lunadeer.dominion.managers.FlagManager",
                "cn.lunadeer.dominion.cache.FlagCache",
                "cn.lunadeer.dominion.utils.FlagUtils"
            };
            
            for (String className : managerClasses) {
                try {
                    Class<?> managerClass = Class.forName(className);
                    // 尝试获取实例或静态方法
                    Object managerInstance = null;
                    try {
                        Field instanceField = managerClass.getDeclaredField("instance");
                        managerInstance = instanceField.get(null);
                    } catch (Exception e) {
                        // 如果没有单例实例，尝试静态方法
                        try {
                            Method getInstanceMethod = managerClass.getMethod("getInstance");
                            managerInstance = getInstanceMethod.invoke(null);
                        } catch (Exception e2) {
                            // 如果也没有getInstance方法，尝试创建新实例
                            managerInstance = managerClass.newInstance();
                        }
                    }
                    
                    if (managerInstance != null) {
                        // 尝试获取 BUILD 标志
                        try {
                            Method getBuildMethod = managerClass.getMethod("getBuildFlag");
                            buildFlag = getBuildMethod.invoke(managerInstance);
                            logger.info("通过管理器获取 BUILD 标志成功");
                        } catch (NoSuchMethodException e) {
                            // 尝试其他方法名
                            try {
                                Method getFlagMethod = managerClass.getMethod("getFlag", String.class);
                                buildFlag = getFlagMethod.invoke(managerInstance, "BUILD");
                                logger.info("通过管理器获取 BUILD 标志成功（使用字符串）");
                            } catch (Exception e2) {
                                // 继续尝试
                            }
                        }
                        
                        // 尝试获取 DESTROY 标志
                        try {
                            Method getDestroyMethod = managerClass.getMethod("getDestroyFlag");
                            destroyFlag = getDestroyMethod.invoke(managerInstance);
                            logger.info("通过管理器获取 DESTROY 标志成功");
                        } catch (NoSuchMethodException e) {
                            // 尝试其他方法名
                            try {
                                Method getFlagMethod = managerClass.getMethod("getFlag", String.class);
                                destroyFlag = getFlagMethod.invoke(managerInstance, "DESTROY");
                                logger.info("通过管理器获取 DESTROY 标志成功（使用字符串）");
                            } catch (Exception e2) {
                                // 继续尝试
                            }
                        }
                        
                        if (buildFlag != null && destroyFlag != null) {
                            return true;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // 继续尝试下一个类
                }
            }
            return false;
            
        } catch (Exception e) {
            logger.warning("通过管理器加载标志失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 通过配置类获取标志
     */
    private boolean loadFlagsFromConfig() {
        try {
            Class<?> configClass = Class.forName("cn.lunadeer.dominion.configuration.Configuration");
            
            // 尝试获取标志字段
            try {
                Field buildField = configClass.getDeclaredField("BUILD");
                buildFlag = buildField.get(null);
                Field destroyField = configClass.getDeclaredField("DESTROY");
                destroyFlag = destroyField.get(null);
                
                if (buildFlag != null && destroyFlag != null) {
                    logger.info("通过配置类获取标志成功");
                    return true;
                }
            } catch (NoSuchFieldException e) {
                // 尝试其他可能的字段名
                String[] buildNames = {"BUILD_FLAG", "FLAG_BUILD", "BUILD"};
                String[] destroyNames = {"DESTROY_FLAG", "FLAG_DESTROY", "DESTROY"};
                
                for (String buildName : buildNames) {
                    for (String destroyName : destroyNames) {
                        try {
                            Field buildField = configClass.getDeclaredField(buildName);
                            buildFlag = buildField.get(null);
                            Field destroyField = configClass.getDeclaredField(destroyName);
                            destroyFlag = destroyField.get(null);
                            
                            if (buildFlag != null && destroyFlag != null) {
                                logger.info("通过配置类获取标志成功（字段: " + buildName + ", " + destroyName + ")");
                                return true;
                            }
                        } catch (Exception e2) {
                            // 继续尝试
                        }
                    }
                }
            }
            return false;
            
        } catch (Exception e) {
            logger.warning("通过配置类加载标志失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 直接创建标志实例（最后的手段）
     */
    private boolean createFlagsDirectly() {
        try {
            // 获取必要的参数类
            Class<?> materialClass = Class.forName("org.bukkit.Material");
            
            // 获取 Material 枚举值
            Object stoneMaterial = Enum.valueOf(materialClass.asSubclass(Enum.class), "STONE");
            Object diamondMaterial = Enum.valueOf(materialClass.asSubclass(Enum.class), "DIAMOND_PICKAXE");
            
            // 创建 BUILD 标志
            buildFlag = priFlagClass.getConstructor(
                String.class, String.class, String.class, 
                Boolean.class, Boolean.class, materialClass
            ).newInstance(
                "build", "建造权限", "允许在领地内建造", 
                true, true, stoneMaterial
            );
            
            // 创建 DESTROY 标志
            destroyFlag = priFlagClass.getConstructor(
                String.class, String.class, String.class, 
                Boolean.class, Boolean.class, materialClass
            ).newInstance(
                "destroy", "破坏权限", "允许在领地内破坏", 
                true, true, diamondMaterial
            );
            
            logger.info("直接创建 PriFlag 实例成功");
            return true;
            
        } catch (Exception e) {
            logger.warning("直接创建标志实例失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查建造权限（放置村民）
     */
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("villagerbucket.bypass.claim")) {
            return true;
        }
        
        if (!residenceEnabled && !dominionEnabled) {
            return true;
        }
        
        try {
            boolean result = true;
            
            if (residenceEnabled) {
                boolean residenceCheck = checkResidenceBuild(player, location);
                result = result && residenceCheck;
            }
            
            if (dominionEnabled && dominionAPIVerified) {
                boolean dominionCheck = checkDominionBuild(player, location);
                result = result && dominionCheck;
            }
            
            return result;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "检查建造权限时发生错误", e);
            return true;
        }
    }
    
    /**
     * 检查破坏权限（捕获村民）
     */
    public boolean canDestroy(Player player, Location location) {
        if (player.hasPermission("villagerbucket.bypass.claim")) {
            return true;
        }
        
        if (!residenceEnabled && !dominionEnabled) {
            return true;
        }
        
        try {
            boolean result = true;
            
            if (residenceEnabled) {
                boolean residenceCheck = checkResidenceDestroy(player, location);
                result = result && residenceCheck;
            }
            
            if (dominionEnabled && dominionAPIVerified) {
                boolean dominionCheck = checkDominionDestroy(player, location);
                result = result && dominionCheck;
            }
            
            return result;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "检查破坏权限时发生错误", e);
            return true;
        }
    }
    
    /**
     * 检查 Dominion 建造权限 - 增强版本
     */
    private boolean checkDominionBuild(Player player, Location location) {
        if (!dominionAPIVerified || dominionAPIInstance == null) {
            return true;
        }
        
        try {
            // 如果有 BUILD 标志，使用标志检查
            if (buildFlag != null && checkPrivilegeFlagSilenceMethod != null) {
                return (Boolean) checkPrivilegeFlagSilenceMethod.invoke(
                    dominionAPIInstance, location, buildFlag, player);
            }
            
            // 备用方法：使用字符串权限检查
            return checkDominionPermission(player, location, "build");
            
        } catch (Exception e) {
            plugin.debug("Dominion 建造权限检查异常: " + e.getMessage());
            // 尝试备用方法
            return checkDominionPermission(player, location, "build");
        }
    }
    
    /**
     * 检查 Dominion 破坏权限 - 增强版本
     */
    private boolean checkDominionDestroy(Player player, Location location) {
        if (!dominionAPIVerified || dominionAPIInstance == null) {
            return true;
        }
        
        try {
            // 如果有 DESTROY 标志，使用标志检查
            if (destroyFlag != null && checkPrivilegeFlagSilenceMethod != null) {
                return (Boolean) checkPrivilegeFlagSilenceMethod.invoke(
                    dominionAPIInstance, location, destroyFlag, player);
            }
            
            // 备用方法：使用字符串权限检查
            return checkDominionPermission(player, location, "destroy");
            
        } catch (Exception e) {
            plugin.debug("Dominion 破坏权限检查异常: " + e.getMessage());
            // 尝试备用方法
            return checkDominionPermission(player, location, "destroy");
        }
    }
    
    /**
     * 备用 Dominion 权限检查方法 - 使用字符串权限
     */
    private boolean checkDominionPermission(Player player, Location location, String permission) {
        try {
            // 方法1: 通过 getDominion 方法获取领地并检查权限
            Class<?> apiClass = dominionAPIInstance.getClass();
            Method getDominionMethod = apiClass.getMethod("getDominion", Location.class);
            Object dominion = getDominionMethod.invoke(dominionAPIInstance, location);
            
            if (dominion == null) {
                // 没有领地，允许操作
                return true;
            }
            
            // 方法2: 尝试检查玩家是否是领地成员
            Method getMemberMethod = apiClass.getMethod("getMember", 
                Class.forName("cn.lunadeer.dominion.api.dtos.DominionDTO"), Player.class);
            Object member = getMemberMethod.invoke(dominionAPIInstance, dominion, player);
            
            if (member != null) {
                // 如果玩家是领地成员，检查具体权限
                Class<?> memberClass = member.getClass();
                
                // 尝试获取权限相关的方法
                try {
                    Method canBuildMethod = memberClass.getMethod("canBuild");
                    Method canDestroyMethod = memberClass.getMethod("canDestroy");
                    
                    if ("build".equals(permission)) {
                        return (Boolean) canBuildMethod.invoke(member);
                    } else if ("destroy".equals(permission)) {
                        return (Boolean) canDestroyMethod.invoke(member);
                    }
                } catch (NoSuchMethodException e) {
                    // 如果成员对象没有这些方法，默认允许
                    return true;
                }
            }
            
            // 如果玩家不是领地成员，但领地存在，拒绝操作
            return false;
            
        } catch (Exception e) {
            plugin.debug("备用 Dominion 权限检查异常: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 检查 Residence 建造权限
     */
    private boolean checkResidenceBuild(Player player, Location location) {
        try {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Object residenceInstance = residenceClass.getMethod("getInstance").invoke(null);
            Object residenceManager = residenceInstance.getClass().getMethod("getResidenceManager").invoke(residenceInstance);
            Object claimedRes = residenceManager.getClass().getMethod("getByLoc", Location.class).invoke(residenceManager, location);
            
            if (claimedRes != null) {
                Object permissions = claimedRes.getClass().getMethod("getPermissions").invoke(claimedRes);
                boolean hasBuild = (boolean) permissions.getClass().getMethod("playerHas", Player.class, String.class, boolean.class)
                        .invoke(permissions, player, "build", false);
                boolean hasAdmin = (boolean) permissions.getClass().getMethod("playerHas", Player.class, String.class, boolean.class)
                        .invoke(permissions, player, "admin", false);
                return hasBuild || hasAdmin;
            }
            return true;
            
        } catch (Exception e) {
            plugin.debug("Residence权限检查异常: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 检查 Residence 破坏权限
     */
    private boolean checkResidenceDestroy(Player player, Location location) {
        try {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Object residenceInstance = residenceClass.getMethod("getInstance").invoke(null);
            Object residenceManager = residenceInstance.getClass().getMethod("getResidenceManager").invoke(residenceInstance);
            Object claimedRes = residenceManager.getClass().getMethod("getByLoc", Location.class).invoke(residenceManager, location);
            
            if (claimedRes != null) {
                Object permissions = claimedRes.getClass().getMethod("getPermissions").invoke(claimedRes);
                boolean hasDestroy = (boolean) permissions.getClass().getMethod("playerHas", Player.class, String.class, boolean.class)
                        .invoke(permissions, player, "destroy", false);
                boolean hasAdmin = (boolean) permissions.getClass().getMethod("playerHas", Player.class, String.class, boolean.class)
                        .invoke(permissions, player, "admin", false);
                return hasDestroy || hasAdmin;
            }
            return true;
            
        } catch (Exception e) {
            plugin.debug("Residence权限检查异常: " + e.getMessage());
            return true;
        }
    }
    
    public void redetectClaimPlugins() {
        logger.info("手动重新检测领地插件...");
        retryCount = 0;
        dominionAPIVerified = false;
        dominionAPIInstance = null;
        buildFlag = null;
        destroyFlag = null;
        detectClaimPlugins();
    }
    
    public boolean isAnyClaimPluginEnabled() {
        return residenceEnabled || dominionEnabled;
    }
    
    public String getDetailedStatus() {
        return String.format(
            "领地插件状态: Residence=%s, Dominion=%s, API验证=%s (重试: %d/%d)",
            residenceEnabled ? "已安装" : "未安装",
            dominionEnabled ? "已安装" : "未安装",
            dominionAPIVerified ? "成功" : "失败", 
            retryCount, maxRetries
        );
    }
    
    public String getDebugInfo() {
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("=== 领地插件详细调试信息 ===\n");
        debugInfo.append("重试次数: ").append(retryCount).append("/").append(maxRetries).append("\n");
        debugInfo.append("Residence启用: ").append(residenceEnabled).append("\n");
        debugInfo.append("Dominion启用: ").append(dominionEnabled).append("\n");
        debugInfo.append("Dominion API验证: ").append(dominionAPIVerified).append("\n");
        debugInfo.append("Dominion API实例: ").append(dominionAPIInstance != null ? "存在" : "null").append("\n");
        debugInfo.append("BUILD标志: ").append(buildFlag != null ? "已加载" : "未加载").append("\n");
        debugInfo.append("DESTROY标志: ").append(destroyFlag != null ? "已加载" : "未加载").append("\n");
        
        if (dominionPlugin != null) {
            debugInfo.append("Dominion插件信息: ").append(dominionPlugin.getName())
                    .append(" v").append(dominionPlugin.getDescription().getVersion()).append("\n");
        }
        
        return debugInfo.toString();
    }
    
    public Plugin getResidencePlugin() {
        return residencePlugin;
    }
    
    public Plugin getDominionPlugin() {
        return dominionPlugin;
    }
}