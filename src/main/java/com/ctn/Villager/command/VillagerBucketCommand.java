package com.ctn.Villager.command;

import com.ctn.Villager.VillagerBucketPlugin;
import com.ctn.Villager.VillagerManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;

public class VillagerBucketCommand implements TabExecutor {
    private final VillagerBucketPlugin plugin;
    private final VillagerManager villagerManager;
    private final com.google.gson.Gson gson;
    
    // 中文职业映射
    private final Map<String, String> professionChineseMap = new HashMap<>();
    // 中文类型映射
    private final Map<String, String> typeChineseMap = new HashMap<>();
    // 反向映射（中文到英文）
    private final Map<String, String> professionReverseMap = new HashMap<>();
    private final Map<String, String> typeReverseMap = new HashMap<>();
    
    public VillagerBucketCommand(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
        this.gson = new com.google.gson.Gson();
        
        // 初始化中文映射
        initializeChineseMappings();
    }
    
    /**
     * 初始化中文映射
     */
    private void initializeChineseMappings() {
        // 职业中文映射
        professionChineseMap.put("none", "无职业");
        professionChineseMap.put("armorer", "盔甲匠");
        professionChineseMap.put("butcher", "屠夫");
        professionChineseMap.put("cartographer", "制图师");
        professionChineseMap.put("cleric", "牧师");
        professionChineseMap.put("farmer", "农民");
        professionChineseMap.put("fisherman", "渔夫");
        professionChineseMap.put("fletcher", "制箭师");
        professionChineseMap.put("leatherworker", "皮匠");
        professionChineseMap.put("librarian", "图书管理员");
        professionChineseMap.put("mason", "石匠");
        professionChineseMap.put("nitwit", "傻子");
        professionChineseMap.put("shepherd", "牧羊人");
        professionChineseMap.put("toolsmith", "工具匠");
        professionChineseMap.put("weaponsmith", "武器匠");
        
        // 类型中文映射
        typeChineseMap.put("desert", "沙漠");
        typeChineseMap.put("jungle", "丛林");
        typeChineseMap.put("plains", "平原");
        typeChineseMap.put("savanna", "热带草原");
        typeChineseMap.put("snow", "雪原");
        typeChineseMap.put("swamp", "沼泽");
        typeChineseMap.put("taiga", "针叶林");
        
        // 创建反向映射
        for (Map.Entry<String, String> entry : professionChineseMap.entrySet()) {
            professionReverseMap.put(entry.getValue(), entry.getKey());
        }
        for (Map.Entry<String, String> entry : typeChineseMap.entrySet()) {
            typeReverseMap.put(entry.getValue(), entry.getKey());
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                return reloadCommand(sender);
            case "give":
                return giveCommand(sender, args);
            case "info":
                return infoCommand(sender);
            case "version":
            case "ver":
                return versionCommand(sender);
            case "help":
                return helpCommand(sender);
            case "debug": // 🆕 新增调试命令
                return debugCommand(sender, args);
            case "redetect": // 🆕 新增重新检测命令
                return redetectCommand(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 主命令补全
            String[] subCommands = {"reload", "give", "info", "version", "help", "debug", "redetect"};
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // give命令的玩家名补全
            String partialName = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
            if ("sender".startsWith(partialName)) {
                completions.add("sender");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // 职业补全（第二个参数）
            String partialInput = args[2].toLowerCase();
            
            // 中文职业补全
            for (String chineseName : professionChineseMap.values()) {
                if (chineseName.toLowerCase().startsWith(partialInput)) {
                    completions.add(chineseName);
                }
            }
            
            // 英文职业补全
            for (String englishName : professionChineseMap.keySet()) {
                if (englishName.toLowerCase().startsWith(partialInput)) {
                    completions.add(englishName);
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // 类型补全（第三个参数）
            String partialInput = args[3].toLowerCase();
            
            // 中文类型补全
            for (String chineseName : typeChineseMap.values()) {
                if (chineseName.toLowerCase().startsWith(partialInput)) {
                    completions.add(chineseName);
                }
            }
            
            // 英文类型补全
            for (String englishName : typeChineseMap.keySet()) {
                if (englishName.toLowerCase().startsWith(partialInput)) {
                    completions.add(englishName);
                }
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            // 等级补全（第四个参数）- 限制为2-5级
            String[] levels = {"2", "3", "4", "5"};
            String partialLevel = args[4].toLowerCase();
            for (String level : levels) {
                if (level.startsWith(partialLevel)) {
                    completions.add(level);
                }
            }
        }
        
        return completions;
    }
    
    private boolean reloadCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.reload", "villagerbucket.reload"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }
        
        // 创建 final 变量
        final CommandSender finalSender = sender;
        
        // 使用调度器异步重载配置
        plugin.getScheduler().runAsync(() -> {
            try {
                plugin.reloadPluginConfig();
                
                // 回到主线程发送成功消息
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, plugin.getMessage("reloaded", "&a配置已重载！"));
                    plugin.debug("配置已通过命令重载");
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "重载配置时发生错误", e);
                
                // 创建 final 异常消息变量
                final String errorMessage = e.getMessage();
                
                // 回到主线程发送错误消息
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.RED + "重载配置时发生错误: " + errorMessage);
                });
            }
        });
        
        return true;
    }
    
    private boolean giveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.give", "villagerbucket.give"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }
        
        if (args.length < 2) {
            sendMessage(sender, ChatColor.RED + "用法: /" + getCommandName() + " give <玩家> [职业] [类型] [等级]");
            sendMessage(sender, ChatColor.GRAY + "示例: /vb give Bobong 图书管理员 平原 3");
            sendMessage(sender, ChatColor.GRAY + "职业: " + String.join(", ", professionChineseMap.values()));
            sendMessage(sender, ChatColor.GRAY + "类型: " + String.join(", ", typeChineseMap.values()));
            sendMessage(sender, ChatColor.GRAY + "等级: 2-5 (默认为2)");
            return true;
        }
        
        // 将需要使用的变量声明为 final 或 effectively final
        final String targetName = args[1];
        final boolean isSenderTarget = targetName.equalsIgnoreCase("sender");
        final CommandSender finalSender = sender;
        final String[] finalArgs = args; // 将 args 也设为 final
        
        // 异步处理give命令
        plugin.getScheduler().runAsync(() -> {
            try {
                Player target = null;
                
                if (isSenderTarget) {
                    if (finalSender instanceof Player) {
                        target = (Player) finalSender;
                    } else {
                        plugin.getScheduler().runGlobal(() -> {
                            sendMessage(finalSender, ChatColor.RED + "控制台不能使用'sender'作为目标！");
                        });
                        return;
                    }
                } else {
                    target = Bukkit.getPlayer(targetName);
                }
                
                if (target == null) {
                    plugin.getScheduler().runGlobal(() -> {
                        sendMessage(finalSender, plugin.getMessage("player-offline", "&c玩家不在线或不存在！"));
                    });
                    return;
                }
                
                // 将 target 转为 final 变量供内部使用
                final Player finalTarget = target;
                
                // 解析参数 - 将这些变量设为 final
                final Villager.Profession profession = parseProfession(finalArgs);
                final Villager.Type villagerType = parseVillagerType(finalArgs);
                final int level = parseLevel(finalArgs);
                
                if (level < 2 || level > 5) {
                    plugin.getScheduler().runGlobal(() -> {
                        sendMessage(finalSender, ChatColor.RED + "等级必须是2-5之间的数字！");
                    });
                    return;
                }
                
                // 创建虚拟村民桶
                ItemStack villagerBucket = createVillagerBucket(profession, level, villagerType);
                if (villagerBucket == null) {
                    plugin.getScheduler().runGlobal(() -> {
                        sendMessage(finalSender, ChatColor.RED + "创建村民桶失败！");
                    });
                    return;
                }
                
                // 创建final变量供lambda使用
                final ItemStack finalBucket = villagerBucket;
                final String professionName = VillagerManager.getProfessionName(profession);
                final String typeName = VillagerManager.getVillagerTypeName(villagerType);
                final int finalLevel = level;
                
                // 在主线程给予物品
                plugin.getScheduler().runAtEntity(finalTarget, () -> {
                    // 给予玩家
                    if (finalTarget.getInventory().addItem(finalBucket).isEmpty()) {
                        String successMessage = MessageFormat.format(
                            plugin.getMessage("give-success", "&a已成功给予 {0} 一个村民桶！"),
                            finalTarget.getName()
                        );
                        sendMessage(finalSender, ChatColor.translateAlternateColorCodes('&', 
                            successMessage + " (&6" + professionName + "&a, 类型:&e" + typeName + "&a, 等级:&e" + finalLevel + "&a)"));
                    } else {
                        // 物品栏满，掉落物品
                        finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), finalBucket);
                        sendMessage(finalSender, plugin.getMessage("inventory-full", "&e物品栏已满，物品已掉落在地上！"));
                    }
                });
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "执行give命令时发生错误", e);
                final String errorMessage = e.getMessage();
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.RED + "执行命令时发生错误: " + errorMessage);
                });
            }
        });
        
        return true;
    }
    
    /**
     * 解析职业参数
     */
    private Villager.Profession parseProfession(String[] args) {
        Villager.Profession profession = Villager.Profession.NONE;
        
        if (args.length >= 3) {
            String professionInput = args[2];
            // 检查是否是中文
            if (professionReverseMap.containsKey(professionInput)) {
                profession = getProfessionFromString(professionReverseMap.get(professionInput));
            } else {
                profession = getProfessionFromString(professionInput);
            }
        }
        
        return profession;
    }
    
    /**
     * 解析村民类型参数
     */
    private Villager.Type parseVillagerType(String[] args) {
        Villager.Type villagerType = Villager.Type.PLAINS;
        
        if (args.length >= 4) {
            String typeInput = args[3];
            // 检查是否是中文
            if (typeReverseMap.containsKey(typeInput)) {
                villagerType = getVillagerTypeFromString(typeReverseMap.get(typeInput));
            } else {
                villagerType = getVillagerTypeFromString(typeInput);
            }
        }
        
        return villagerType;
    }
    
    /**
     * 解析等级参数
     */
    private int parseLevel(String[] args) {
        int level = 2; // 默认等级为2
        
        if (args.length >= 5) {
            try {
                level = Integer.parseInt(args[4]);
                level = Math.max(2, Math.min(5, level)); // 限制在2-5级
            } catch (NumberFormatException e) {
                // 解析失败时使用默认值
                level = 2;
            }
        }
        
        return level;
    }
    
    private boolean infoCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.info", "villagerbucket.info"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }
        
        // 创建 final 变量
        final CommandSender finalSender = sender;
        
        // 使用调度器异步准备信息
        plugin.getScheduler().runAsync(() -> {
            List<String> infoLines = new ArrayList<>();
            infoLines.add(ChatColor.GOLD + "=== 村民桶插件信息 ===");
            infoLines.add(ChatColor.YELLOW + "版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
            infoLines.add(ChatColor.YELLOW + "作者: " + ChatColor.WHITE + String.join(", ", plugin.getDescription().getAuthors()));
            infoLines.add(ChatColor.YELLOW + "GitHub: " + ChatColor.WHITE + "https://github.com/ctnBobong32/Folia_VillagerBucket");
            infoLines.add(ChatColor.YELLOW + "调试模式: " + ChatColor.WHITE + (plugin.getConfig().getBoolean("settings.debug-mode", false) ? "启用" : "禁用"));
            infoLines.add(ChatColor.YELLOW + "服务器类型: " + ChatColor.WHITE + (plugin.isFolia() ? "Folia" : "传统Bukkit"));
            infoLines.add(ChatColor.YELLOW + "Folia超时设置: " + ChatColor.WHITE + plugin.getConfig().getInt("settings.folia.timeout-seconds", 10) + "秒");
            
            // 🆕 添加领地插件状态信息
            String claimStatus = plugin.getClaimManager().getDetailedStatus();
            infoLines.add(ChatColor.YELLOW + "领地插件: " + ChatColor.WHITE + claimStatus);
            
            // 🆕 添加村民管理器状态
            infoLines.add(ChatColor.YELLOW + "村民管理器: " + ChatColor.WHITE + "已初始化");
            
            // 在主线程发送信息
            plugin.getScheduler().runGlobal(() -> {
                for (String line : infoLines) {
                    sendMessage(finalSender, line);
                }
            });
        });
        
        return true;
    }
    
    private boolean versionCommand(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        
        // 创建final变量供lambda使用
        final String finalVersion = version;
        final CommandSender finalSender = sender;
        
        // 使用调度器异步准备版本信息
        plugin.getScheduler().runAsync(() -> {
            String versionMessage = MessageFormat.format(
                plugin.getMessage("version-info", "&a村民桶插件 &e版本 {0}"),
                finalVersion
            );
            
            // 在主线程发送版本信息
            plugin.getScheduler().runGlobal(() -> {
                sendMessage(finalSender, versionMessage);
            });
        });
        
        return true;
    }
    
    private boolean helpCommand(CommandSender sender) {
        // 创建final变量供lambda使用
        final CommandSender finalSender = sender;
        
        // 使用调度器异步准备帮助信息
        plugin.getScheduler().runAsync(() -> {
            List<String> helpMessages = plugin.getMessageList("help");
            
            // 如果没有配置帮助消息，使用默认值
            if (helpMessages.isEmpty()) {
                helpMessages = Arrays.asList(
                    "&6=== 村民桶插件帮助 ===",
                    "&e/villagerbucket give <玩家> [职业] [类型] [等级] &7- 给予玩家村民桶",
                    "&e/villagerbucket reload &7- 重载插件配置", 
                    "&e/villagerbucket info &7- 查看插件信息",
                    "&e/villagerbucket version &7- 查看版本信息",
                    "&e/villagerbucket debug &7- 调试信息",
                    "&e/villagerbucket redetect &7- 重新检测领地插件",
                    "&e/villagerbucket help &7- 显示此帮助信息"
                );
            }
            
            // 创建final变量
            final List<String> finalHelpMessages = helpMessages;
            
            plugin.getScheduler().runGlobal(() -> {
                for (String line : finalHelpMessages) {
                    sendMessage(finalSender, ChatColor.translateAlternateColorCodes('&', line));
                }
            });
        });
        
        return true;
    }
    
    /**
     * 🆕 调试命令 - 显示详细的领地插件调试信息
     */
    private boolean debugCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.debug", "villagerbucket.debug"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }
        
        final CommandSender finalSender = sender;
        
        // 使用调度器异步获取调试信息
        plugin.getScheduler().runAsync(() -> {
            try {
                // 获取领地插件的详细调试信息
                String debugInfo = plugin.getClaimManager().getDebugInfo();
                
                // 获取插件基本信息
                StringBuilder fullDebugInfo = new StringBuilder();
                fullDebugInfo.append("=== 村民桶插件调试信息 ===\n");
                fullDebugInfo.append("插件版本: ").append(plugin.getDescription().getVersion()).append("\n");
                fullDebugInfo.append("服务器类型: ").append(plugin.isFolia() ? "Folia" : "传统Bukkit").append("\n");
                fullDebugInfo.append("调试模式: ").append(plugin.getConfig().getBoolean("settings.debug-mode", false) ? "启用" : "禁用").append("\n");
                fullDebugInfo.append("数据文件夹: ").append(plugin.getDataFolder().getAbsolutePath()).append("\n\n");
                
                fullDebugInfo.append(debugInfo);
                
                // 在主线程发送调试信息
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.GOLD + "=== 调试信息 ===");
                    // 逐行发送，确保长消息不会截断
                    for (String line : fullDebugInfo.toString().split("\n")) {
                        sendMessage(finalSender, ChatColor.YELLOW + line);
                    }
                    sendMessage(finalSender, ChatColor.GOLD + "=================");
                });
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "获取调试信息时发生错误", e);
                final String errorMessage = e.getMessage();
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.RED + "获取调试信息时发生错误: " + errorMessage);
                });
            }
        });
        
        return true;
    }
    
    /**
     * 🆕 重新检测领地插件命令
     */
    private boolean redetectCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.reload", "villagerbucket.reload"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }
        
        final CommandSender finalSender = sender;
        
        // 使用调度器异步重新检测
        plugin.getScheduler().runAsync(() -> {
            try {
                // 重新检测领地插件
                plugin.getClaimManager().redetectClaimPlugins();
                
                // 获取最新的状态
                String newStatus = plugin.getClaimManager().getDetailedStatus();
                
                // 在主线程发送结果
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.GREEN + "✅ 已重新检测领地插件");
                    sendMessage(finalSender, ChatColor.YELLOW + "当前状态: " + newStatus);
                    sendMessage(finalSender, ChatColor.GREEN + "领地保护功能: " + 
                        (plugin.getClaimManager().isAnyClaimPluginEnabled() ? "已启用" : "未启用"));
                });
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "重新检测领地插件时发生错误", e);
                final String errorMessage = e.getMessage();
                plugin.getScheduler().runGlobal(() -> {
                    sendMessage(finalSender, ChatColor.RED + "重新检测领地插件时发生错误: " + errorMessage);
                });
            }
        });
        
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sendMessage(sender, plugin.getMessage("usage", "&c用法: /villagerbucket [reload|give|info|version|help|debug|redetect]"));
        sendMessage(sender, ChatColor.GRAY + "职业支持中文和英文，等级2-5，类型支持中文和英文");
        sendMessage(sender, ChatColor.GRAY + "使用 " + ChatColor.YELLOW + "/villagerbucket debug " + ChatColor.GRAY + "查看详细调试信息");
    }
    
    /**
     * 发送消息的辅助方法
     */
    private void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        
        // 创建final变量供lambda使用
        final CommandSender finalSender = sender;
        final String finalMessage = message;
        
        // 使用调度器在主线程发送消息
        plugin.getScheduler().runGlobal(() -> {
            finalSender.sendMessage(finalMessage);
        });
    }
    
    private String getCommandName() {
        return "villagerbucket";
    }
    
    /**
     * 从字符串获取村民职业
     */
    private Villager.Profession getProfessionFromString(String professionStr) {
        try {
            switch (professionStr.toLowerCase()) {
                case "farmer":
                case "农民":
                    return Villager.Profession.FARMER;
                case "librarian":
                case "图书管理员":
                    return Villager.Profession.LIBRARIAN;
                case "armorer":
                case "盔甲匠":
                    return Villager.Profession.ARMORER;
                case "butcher":
                case "屠夫":
                    return Villager.Profession.BUTCHER;
                case "cartographer":
                case "制图师":
                    return Villager.Profession.CARTOGRAPHER;
                case "cleric":
                case "牧师":
                    return Villager.Profession.CLERIC;
                case "fisherman":
                case "渔夫":
                    return Villager.Profession.FISHERMAN;
                case "fletcher":
                case "制箭师":
                    return Villager.Profession.FLETCHER;
                case "leatherworker":
                case "皮匠":
                    return Villager.Profession.LEATHERWORKER;
                case "mason":
                case "石匠":
                    return Villager.Profession.MASON;
                case "shepherd":
                case "牧羊人":
                    return Villager.Profession.SHEPHERD;
                case "toolsmith":
                case "工具匠":
                    return Villager.Profession.TOOLSMITH;
                case "weaponsmith":
                case "武器匠":
                    return Villager.Profession.WEAPONSMITH;
                case "nitwit":
                case "傻子":
                    return Villager.Profession.NITWIT;
                default:
                    return Villager.Profession.NONE;
            }
        } catch (Exception e) {
            plugin.debug("解析职业失败: " + professionStr);
            return Villager.Profession.NONE;
        }
    }
    
    /**
     * 从字符串获取村民类型
     */
    private Villager.Type getVillagerTypeFromString(String typeStr) {
        try {
            switch (typeStr.toLowerCase()) {
                case "desert":
                case "沙漠":
                    return Villager.Type.DESERT;
                case "jungle":
                case "丛林":
                    return Villager.Type.JUNGLE;
                case "plains":
                case "平原":
                    return Villager.Type.PLAINS;
                case "savanna":
                case "热带草原":
                    return Villager.Type.SAVANNA;
                case "snow":
                case "雪原":
                    return Villager.Type.SNOW;
                case "swamp":
                case "沼泽":
                    return Villager.Type.SWAMP;
                case "taiga":
                case "针叶林":
                    return Villager.Type.TAIGA;
                default:
                    return Villager.Type.PLAINS;
            }
        } catch (Exception e) {
            plugin.debug("解析村民类型失败: " + typeStr);
            return Villager.Type.PLAINS;
        }
    }
    
    /**
     * 创建虚拟村民桶 - 增强版本（支持自定义等级和类型）
     */
    private ItemStack createVillagerBucket(Villager.Profession profession, int level, Villager.Type type) {
        try {
            ItemStack bucket = new ItemStack(Material.BUCKET);
            ItemMeta meta = bucket.getItemMeta();
            
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(Material.BUCKET);
            }
            
            // 设置显示名称
            String displayName = ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("bucket.name", "&6村民桶"));
            meta.setDisplayName(displayName);
            
            // 设置Lore
            List<String> lore = new ArrayList<>();
            List<String> loreTemplate = plugin.getConfig().getStringList("bucket.lore");
            
            String professionName = VillagerManager.getProfessionName(profession);
            String typeName = VillagerManager.getVillagerTypeName(type);
            
            if (loreTemplate.isEmpty()) {
                // 默认Lore
                lore.add("§7包含一个被捕捉的村民");
                lore.add("§7右键放置村民");
                lore.add("");
                lore.add("§e职业: " + professionName);
                lore.add("§e等级: " + level);
                lore.add("§e类型: " + typeName);
                lore.add("§e年龄: 成年");
                lore.add("§e经验: 0");
            } else {
                for (String line : loreTemplate) {
                    String processedLine = line
                        .replace("{profession}", professionName)
                        .replace("{level}", String.valueOf(level))
                        .replace("{type}", typeName)
                        .replace("{age}", "成年")
                        .replace("{experience}", "0")
                        .replace("{recipe-count}", "0");
                    lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
                }
            }
            
            meta.setLore(lore);
            
            // 设置自定义模型数据
            int customModelData = plugin.getConfig().getInt("settings.custom-model-data", 1000);
            meta.setCustomModelData(customModelData);
            
            // 创建完整的虚拟村民数据 - 修复数据缺失问题
            Map<String, Object> villagerData = new HashMap<>();
            villagerData.put("profession", profession.key().value());
            villagerData.put("level", level);
            villagerData.put("type", type.key().value());
            villagerData.put("experience", 0);
            villagerData.put("ageLock", true);
            villagerData.put("uuid", UUID.randomUUID().toString());
            villagerData.put("isAdult", true);
            villagerData.put("age", 0); // 添加年龄数据
            villagerData.put("villagerHealth", 20.0);
            villagerData.put("maxHealth", 20.0);
            villagerData.put("customName", null);
            villagerData.put("recipes", new ArrayList<>());
            
            // 添加Folia特定的数据完整性检查
            villagerData.put("dataVersion", "1.3.0-folia");
            villagerData.put("timestamp", System.currentTimeMillis());
            
            // 序列化数据
            String jsonData = gson.toJson(villagerData);
            if (jsonData == null || jsonData.isEmpty()) {
                plugin.debug("虚拟村民数据序列化失败");
                return null;
            }
            
            String encodedData = Base64.getEncoder().encodeToString(jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // 保存到PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(villagerManager.getVillagerDataKey(), PersistentDataType.STRING, encodedData);
            
            bucket.setItemMeta(meta);
            
            plugin.debug("成功创建虚拟村民桶，职业: " + professionName + ", 等级: " + level + ", 类型: " + typeName);
            return bucket;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建虚拟村民桶时发生错误", e);
            return null;
        }
    }
}