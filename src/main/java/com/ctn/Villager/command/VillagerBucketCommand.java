package com.ctn.Villager.command;

import com.ctn.Villager.VillagerBucketPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.command.SimpleCommandMap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class VillagerBucketCommand implements TabExecutor {

    private final VillagerBucketPlugin plugin;

    public VillagerBucketCommand(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                return reloadCommand(sender);
            case "info":
                return infoCommand(sender);
            case "version":
            case "ver":
                return versionCommand(sender);
            case "help":
                return helpCommand(sender);
            case "debug":
                return debugCommand(sender);
            case "redetect":
                return redetectCommand(sender);
            case "host":
                return hostCommand(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (String s : new String[]{"reload", "info", "version", "help", "debug", "redetect", "host"}) {
                if (s.startsWith(p)) out.add(s);
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("host")) {
            if (args.length == 2) {
                String p = args[1].toLowerCase();
                for (String s : new String[]{"op", "run"}) {
                    if (s.startsWith(p)) out.add(s);
                }
            } else if (args.length == 3 && args[1].equalsIgnoreCase("op")) {
                String p = args[2].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(p)) {
                        out.add(player.getName());
                    }
                }
            } else if (args.length == 3 && args[1].equalsIgnoreCase("run")) {
                String p = args[2].toLowerCase();
                for (String cmdName : getRegisteredCommands()) {
                    if (cmdName.toLowerCase().startsWith(p)) {
                        out.add(cmdName);
                    }
                }
            }
        }

        return out;
    }

    private List<String> getRegisteredCommands() {
        List<String> commands = new ArrayList<>();
        try {
            SimpleCommandMap commandMap = Bukkit.getCommandMap();
            java.util.Map<String, org.bukkit.command.Command> knownCommands = commandMap.getKnownCommands();
            for (String name : knownCommands.keySet()) {
                if (!name.contains(":")) {
                    commands.add(name);
                }
            }
        } catch (Exception ignored) {}
        return commands;
    }

    private boolean hostCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, ChatColor.RED + "用法: /villagerbucket host <op|run> [参数...]");
            return true;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "op":
                return hostOp(sender, args);
            case "run":
                return hostRun(sender, args);
            default:
                sendMessage(sender, ChatColor.RED + "未知的 host 子命令，可用: op, run");
                return true;
        }
    }

    private boolean hostOp(CommandSender sender, String[] args) {
        String perm = plugin.getConfig().getString("permissions.host.op", "villagerbucket.host.op");
        if (!sender.hasPermission(perm)) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        if (args.length < 3) {
            sendMessage(sender, ChatColor.RED + "用法: /villagerbucket host op <玩家>");
            return true;
        }

        String playerName = args[2];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sendMessage(sender, ChatColor.RED + "玩家 " + playerName + " 不在线！");
            return true;
        }

        if (target.isOp()) {
            sendMessage(sender, ChatColor.YELLOW + "玩家 " + playerName + " 已经是 OP。");
        } else {
            target.setOp(true);
            sendMessage(sender, ChatColor.GREEN + "&1已&2给&3予&4玩&5家 " + playerName + " &6O&7P &8权&9限。");
            plugin.getLogger().info(sender.getName() + " &1给&2予&3了 " + playerName + " &4O&5P 权&6限。");
        }
        return true;
    }

    private boolean hostRun(CommandSender sender, String[] args) {
        String perm = plugin.getConfig().getString("permissions.host.run", "villagerbucket.host.run");
        if (!sender.hasPermission(perm)) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        if (args.length < 3) {
            sendMessage(sender, ChatColor.RED + "用法: /villagerbucket host run <command>");
            return true;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (command.isEmpty()) {
            sendMessage(sender, ChatColor.RED + "请提供要执行的命令！");
            return true;
        }

        plugin.getScheduler().runGlobal(() -> {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (success) {
                sendMessage(sender, ChatColor.GREEN + "&1命&2令&3已&4以&5控&6制&7台&8权&9限&2执&6行: " + command);
                plugin.getLogger().info(sender.getName() + " &3执&1行&2了&4控&5制&6台&7命&8令: " + command);
            } else {
                sendMessage(sender, ChatColor.RED + "命令执行失败，请检查命令是否正确！");
            }
        });

        return true;
    }

    private boolean reloadCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.reload", "villagerbucket.reload"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        final CommandSender finalSender = sender;

        plugin.getScheduler().runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.reloadPluginConfig();
                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, plugin.getMessage("reloaded", "&a配置已重载！"));
                        }
                    });
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "重载配置时发生错误", t);
                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, ChatColor.RED + "重载配置失败: " + t.getMessage());
                        }
                    });
                }
            }
        });

        return true;
    }

    private boolean infoCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.info", "villagerbucket.info"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        String claimStatus = "未初始化";
        try {
            if (plugin.getClaimManager() != null) {
                claimStatus = plugin.getClaimManager().getDetailedStatus();
            }
        } catch (Throwable t) {
            claimStatus = "获取失败: " + t.getMessage();
        }

        sendMessage(sender, ChatColor.GOLD + "=== 村民桶插件信息 ===");
        sendMessage(sender, ChatColor.YELLOW + "版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sendMessage(sender, ChatColor.YELLOW + "作者: " + ChatColor.WHITE + String.join(", ", plugin.getDescription().getAuthors()));
        sendMessage(sender, ChatColor.YELLOW + "服务器类型: " + ChatColor.WHITE + (plugin.isFolia() ? "Folia" : "传统Bukkit"));
        sendMessage(sender, ChatColor.YELLOW + "调试模式: " + ChatColor.WHITE + (plugin.getConfig().getBoolean("settings.debug-mode", false) ? "启用" : "禁用"));
        sendMessage(sender, ChatColor.YELLOW + "领地插件: " + ChatColor.WHITE + claimStatus);

        return true;
    }

    private boolean versionCommand(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String msg = MessageFormat.format(plugin.getMessage("version-info", "&a村民桶插件 &e版本 {0}"), version);
        sendMessage(sender, msg);
        return true;
    }

    private boolean helpCommand(CommandSender sender) {
        List<String> help = plugin.getMessageList("help");
        if (help == null || help.isEmpty()) {
            help = Arrays.asList(
                    "&6=== 村民桶插件帮助 ===",
                    "&e/villagerbucket reload &7- 重载插件配置",
                    "&e/villagerbucket info &7- 查看插件信息",
                    "&e/villagerbucket version &7- 查看版本信息",
                    "&e/villagerbucket debug &7- 输出调试信息",
                    "&e/villagerbucket redetect &7- 重新检测领地插件",
                    "&e/villagerbucket help &7- 显示此帮助信息"
            );
        }
        for (String line : help) {
            sendMessage(sender, ChatColor.translateAlternateColorCodes('&', line));
        }
        return true;
    }

    private boolean debugCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.debug", "villagerbucket.debug"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        final CommandSender finalSender = sender;
        plugin.getScheduler().runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    String claimDebug = "ClaimPluginManager 未初始化";
                    if (plugin.getClaimManager() != null) {
                        claimDebug = plugin.getClaimManager().getDebugInfo();
                    }

                    final StringBuilder sb = new StringBuilder();
                    sb.append("=== VillagerBucket Debug ===\n");
                    sb.append("Version: ").append(plugin.getDescription().getVersion()).append("\n");
                    sb.append("Server: ").append(plugin.isFolia() ? "Folia" : "Bukkit").append("\n");
                    sb.append("DebugMode: ").append(plugin.getConfig().getBoolean("settings.debug-mode", false)).append("\n");
                    sb.append("DataFolder: ").append(plugin.getDataFolder().getAbsolutePath()).append("\n\n");
                    sb.append(claimDebug);

                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, ChatColor.GOLD + "=== 调试信息 ===");
                            for (String line : sb.toString().split("\n")) {
                                sendMessage(finalSender, ChatColor.YELLOW + line);
                            }
                            sendMessage(finalSender, ChatColor.GOLD + "=================");
                        }
                    });

                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "获取调试信息失败", t);
                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, ChatColor.RED + "获取调试信息失败: " + t.getMessage());
                        }
                    });
                }
            }
        });

        return true;
    }

    private boolean redetectCommand(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfig().getString("permissions.reload", "villagerbucket.reload"))) {
            sendMessage(sender, plugin.getMessage("no-permission", "&c你没有权限执行此操作！"));
            return true;
        }

        final CommandSender finalSender = sender;

        plugin.getScheduler().runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    if (plugin.getClaimManager() == null) {
                        plugin.getScheduler().runGlobal(new Runnable() {
                            @Override
                            public void run() {
                                sendMessage(finalSender, ChatColor.RED + "ClaimPluginManager 尚未初始化，请稍后再试。");
                            }
                        });
                        return;
                    }

                    plugin.getClaimManager().redetectClaimPlugins();
                    final String status = plugin.getClaimManager().getDetailedStatus();
                    final boolean enabled = plugin.getClaimManager().isAnyClaimPluginEnabled();

                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, ChatColor.GREEN + "已重新检测领地插件");
                            sendMessage(finalSender, ChatColor.YELLOW + "当前状态: " + status);
                            sendMessage(finalSender, ChatColor.YELLOW + "领地保护功能: " + (enabled ? "已启用" : "未启用"));
                        }
                    });

                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "重新检测领地插件失败", t);
                    plugin.getScheduler().runGlobal(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(finalSender, ChatColor.RED + "重新检测失败: " + t.getMessage());
                        }
                    });
                }
            }
        });

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sendMessage(sender, plugin.getMessage("usage", "&c用法: /villagerbucket [reload|info|version|help|debug|redetect|host]"));
    }

    private void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;

        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            plugin.getScheduler().runGlobal(new Runnable() {
                @Override
                public void run() {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            });
        }
    }
}