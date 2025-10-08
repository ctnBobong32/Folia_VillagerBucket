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
    
    public VillagerBucketCommand(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
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
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 主命令补全 - 使用部分匹配
            String[] subCommands = {"reload", "give", "info", "version", "help"};
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // give命令的玩家名补全 - 使用部分匹配
            String partialName = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
            // 如果没有在线玩家匹配，添加"sender"作为选项
            if (completions.isEmpty() && "sender".startsWith(partialName)) {
                completions.add("sender");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // give命令的职业补全 - 使用部分匹配
            String[] professions = {
                "farmer", "librarian", "armorer", "butcher", "cartographer", 
                "cleric", "fisherman", "fletcher", "leatherworker", "mason", 
                "shepherd", "toolsmith", "weaponsmith", "nitwit", "none"
            };
            String partialProfession = args[2].toLowerCase();
            for (String profession : professions) {
                if (profession.startsWith(partialProfession)) {
                    completions.add(profession);
                }
            }
        }
        
        return completions;
    }
    
    private boolean reloadCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.reload", "villagerbucket.reload"))) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.no-permission", 
                "&c你没有权限执行此操作！")));
            return true;
        }
        
        try {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.reloaded", 
                "&a配置已重载！")));
            plugin.debug("配置已通过命令重载");
            return true;
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载配置时发生错误: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "重载配置时发生错误", e);
            return true;
        }
    }
    
    private boolean giveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.give", "villagerbucket.give"))) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.no-permission", 
                "&c你没有权限执行此操作！")));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /" + getCommandName() + " give <玩家|sender> [职业]");
            return true;
        }
        
        Player target = null;
        String targetName = args[1];
        
        if (targetName.equalsIgnoreCase("sender")) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "控制台不能使用'sender'作为目标！");
                return true;
            }
        } else {
            target = Bukkit.getPlayer(targetName);
        }
        
        if (target == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.player-offline", 
                "&c玩家不在线或不存在！")));
            return true;
        }
        
        // 获取职业
        Villager.Profession profession = Villager.Profession.NONE;
        if (args.length >= 3) {
            profession = getProfessionFromString(args[2]);
        }
        
        // 创建虚拟村民桶
        ItemStack villagerBucket = createVillagerBucket(profession);
        if (villagerBucket == null) {
            sender.sendMessage(ChatColor.RED + "创建村民桶失败！");
            return true;
        }
        
        // 给予玩家
        if (target.getInventory().addItem(villagerBucket).isEmpty()) {
            sender.sendMessage(MessageFormat.format(
                ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.give-success", 
                    "&a已成功给予 {0} 一个村民桶！")),
                target.getName()
            ));
        } else {
            // 物品栏满，掉落物品
            target.getWorld().dropItemNaturally(target.getLocation(), villagerBucket);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.inventory-full", 
                "&e玩家物品栏已满，物品已掉落在地上！")));
        }
        
        return true;
    }
    
    private boolean infoCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.info", "villagerbucket.info"))) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.no-permission", 
                "&c你没有权限执行此操作！")));
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== 村民桶插件信息 ===");
        sender.sendMessage(ChatColor.YELLOW + "版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "作者: " + ChatColor.WHITE + String.join(", ", plugin.getDescription().getAuthors()));
        sender.sendMessage(ChatColor.YELLOW + "GitHub: " + ChatColor.WHITE + "https://github.com/ctnBobong32/Folia_VillagerBucket");
        sender.sendMessage(ChatColor.YELLOW + "调试模式: " + ChatColor.WHITE + (plugin.getConfig().getBoolean("settings.debug-mode", false) ? "启用" : "禁用"));
        
        return true;
    }
    
    private boolean versionCommand(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        sender.sendMessage(MessageFormat.format(
            ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.version-info", 
                "&a村民桶插件 &e版本 {0}")),
            version
        ));
        return true;
    }
    
    private boolean helpCommand(CommandSender sender) {
        List<String> helpMessages = plugin.getConfig().getStringList("messages.help");
        if (helpMessages.isEmpty()) {
            // 默认帮助信息
            sender.sendMessage(ChatColor.GOLD + "=== 村民桶插件帮助 ===");
            sender.sendMessage(ChatColor.YELLOW + "/villagerbucket give <玩家|sender> [职业]" + ChatColor.WHITE + " - 给予玩家村民桶");
            sender.sendMessage(ChatColor.YELLOW + "/villagerbucket reload" + ChatColor.WHITE + " - 重载插件配置");
            sender.sendMessage(ChatColor.YELLOW + "/villagerbucket info" + ChatColor.WHITE + " - 查看插件信息");
            sender.sendMessage(ChatColor.YELLOW + "/villagerbucket version" + ChatColor.WHITE + " - 查看版本信息");
            sender.sendMessage(ChatColor.YELLOW + "/villagerbucket help" + ChatColor.WHITE + " - 显示此帮助信息");
        } else {
            for (String line : helpMessages) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            plugin.getConfig().getString("messages.usage", 
            "&c用法: /villagerbucket [reload|give|info|version|help]")));
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
                    return Villager.Profession.FARMER;
                case "librarian":
                    return Villager.Profession.LIBRARIAN;
                case "armorer":
                    return Villager.Profession.ARMORER;
                case "butcher":
                    return Villager.Profession.BUTCHER;
                case "cartographer":
                    return Villager.Profession.CARTOGRAPHER;
                case "cleric":
                    return Villager.Profession.CLERIC;
                case "fisherman":
                    return Villager.Profession.FISHERMAN;
                case "fletcher":
                    return Villager.Profession.FLETCHER;
                case "leatherworker":
                    return Villager.Profession.LEATHERWORKER;
                case "mason":
                    return Villager.Profession.MASON;
                case "shepherd":
                    return Villager.Profession.SHEPHERD;
                case "toolsmith":
                    return Villager.Profession.TOOLSMITH;
                case "weaponsmith":
                    return Villager.Profession.WEAPONSMITH;
                case "nitwit":
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
     * 创建虚拟村民桶 - 修复数据完整性问题
     */
    private ItemStack createVillagerBucket(Villager.Profession profession) {
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
            
            if (loreTemplate.isEmpty()) {
                // 默认Lore
                lore.add("§7包含一个被捕捉的村民");
                lore.add("§7右键放置村民");
                lore.add("");
                lore.add("§e职业: " + professionName);
                lore.add("§e等级: 1");
                lore.add("§e类型: 平原");
                lore.add("§e年龄: 成年");
                lore.add("§e经验: 0");
            } else {
                for (String line : loreTemplate) {
                    String processedLine = line
                        .replace("{profession}", professionName)
                        .replace("{level}", "1")
                        .replace("{type}", "平原")
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
            villagerData.put("level", 1);
            villagerData.put("type", Villager.Type.PLAINS.key().value());
            villagerData.put("experience", 0);
            villagerData.put("ageLock", true);
            villagerData.put("uuid", UUID.randomUUID().toString());
            villagerData.put("isAdult", true);
            villagerData.put("age", 0); // 添加年龄数据
            villagerData.put("villagerHealth", 20.0);
            villagerData.put("maxHealth", 20.0);
            villagerData.put("customName", null);
            villagerData.put("recipes", new ArrayList<>());
            
            // 序列化数据
            String jsonData = new com.google.gson.Gson().toJson(villagerData);
            String encodedData = Base64.getEncoder().encodeToString(jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // 保存到PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(villagerManager.getVillagerDataKey(), PersistentDataType.STRING, encodedData);
            
            bucket.setItemMeta(meta);
            
            plugin.debug("成功创建虚拟村民桶，职业: " + professionName);
            return bucket;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建虚拟村民桶时发生错误", e);
            return null;
        }
    }
}
