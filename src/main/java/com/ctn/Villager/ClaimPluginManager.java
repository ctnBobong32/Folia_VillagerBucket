package com.ctn.Villager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class ClaimPluginManager implements Listener {

    private final VillagerBucketPlugin plugin;
    private final Logger logger;

    private final AtomicBoolean residenceEnabled = new AtomicBoolean(false);
    private final AtomicBoolean dominionEnabled = new AtomicBoolean(false);
    private final AtomicBoolean dominionReady = new AtomicBoolean(false);

    private Plugin residencePlugin;
    private Plugin dominionPlugin;

    private volatile Object dominionApi;
    private volatile Method checkPrivilegeFlagSilence;
    private volatile Method flagsGetPreFlag;

    private int retryTaskId = -1;
    private int retryCount = 0;
    private final int maxRetries = 60;
    private final long retryPeriodTicks = 100L;

    public ClaimPluginManager(VillagerBucketPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        logger.info("ClaimPluginManager 已加载");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        logger.info("服务器已完全启动(ServerLoadEvent)：开始检测领地插件...");
        redetectClaimPlugins();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        String name = e.getPlugin().getName().toLowerCase();
        if (name.contains("dominion") || name.contains("residence")) {
            logger.info("检测到领地插件启用(" + e.getPlugin().getName() + ")：重新检测...");
            redetectClaimPlugins();
        }
    }

    public void redetectClaimPlugins() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            retryCount = 0;
            dominionReady.set(false);
            dominionApi = null;
            checkPrivilegeFlagSilence = null;
            flagsGetPreFlag = null;
            detectPlugins();
            if (dominionEnabled.get()) {
                startDominionInitRetry();
            } else {
                stopRetry();
            }
            logger.info(getDetailedStatus());
        });
    }

    private void detectPlugins() {
        PluginManager pm = plugin.getServer().getPluginManager();
        residencePlugin = pm.getPlugin("Residence");
        residenceEnabled.set(residencePlugin != null && residencePlugin.isEnabled());
        dominionPlugin = pm.getPlugin("Dominion");
        dominionEnabled.set(dominionPlugin != null && dominionPlugin.isEnabled());
    }

    private void startDominionInitRetry() {
        if (retryTaskId != -1) return;
        retryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            retryCount++;
            if (tryInitDominion()) {
                dominionReady.set(true);
                logger.info("Dominion API 已就绪：权限检查已启用");
                stopRetry();
                return;
            }
            if (retryCount % 6 == 0) {
                logger.info("等待 Dominion API 就绪... 重试 " + retryCount + "/" + maxRetries);
            }
            if (retryCount >= maxRetries) {
                logger.warning("Dominion API 长时间未就绪，停止重试（将不拦截以免误伤）");
                stopRetry();
            }
        }, 1L, retryPeriodTicks);
    }

    private void stopRetry() {
        if (retryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(retryTaskId);
            retryTaskId = -1;
        }
    }

    private boolean tryInitDominion() {
        try {
            Class<?> dominionApiClass = Class.forName("cn.lunadeer.dominion.api.DominionAPI");
            Method getInstance = dominionApiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api == null) return false;
            Class<?> priFlagClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.PriFlag");
            checkPrivilegeFlagSilence = dominionApiClass.getMethod(
                    "checkPrivilegeFlagSilence",
                    Location.class, priFlagClass, Player.class
            );
            Class<?> flagsClass = Class.forName("cn.lunadeer.dominion.api.dtos.flag.Flags");
            flagsGetPreFlag = flagsClass.getMethod("getPreFlag", String.class);
            dominionApi = api;
            return true;
        } catch (Throwable t) {
            plugin.debug("Dominion 初始化失败: " + t.getMessage());
            return false;
        }
    }

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) return true;
        if (player.hasPermission("villagerbucket.bypass.claim")) return true;
        boolean ok = true;
        if (residenceEnabled.get()) ok &= checkResidenceFlag(player, location, "build");
        if (dominionEnabled.get()) ok &= checkDominionFlag(player, location, "place");
        return ok;
    }

    public boolean canCaptureVillager(Player player, Location location) {
        if (player == null || location == null) return true;
        if (player.hasPermission("villagerbucket.bypass.claim")) return true;
        boolean ok = true;
        if (residenceEnabled.get()) {
            boolean r = checkResidenceFlag(player, location, "admin")
                    || checkResidenceFlag(player, location, "use")
                    || checkResidenceFlag(player, location, "attack")
                    || checkResidenceFlag(player, location, "destroy");
            ok &= r;
        }
        if (dominionEnabled.get()) {
            boolean d = checkDominionFlag(player, location, "trade")
                    || checkDominionFlag(player, location, "leash");
            ok &= d;
        }
        return ok;
    }

    public boolean canDestroy(Player player, Location location) {
        if (player == null || location == null) return true;
        if (player.hasPermission("villagerbucket.bypass.claim")) return true;
        boolean ok = true;
        if (residenceEnabled.get()) ok &= checkResidenceFlag(player, location, "destroy");
        if (dominionEnabled.get()) ok &= checkDominionFlag(player, location, "break");
        return ok;
    }

    private boolean checkDominionFlag(Player player, Location location, String flagName) {
        if (!dominionReady.get() || dominionApi == null || checkPrivilegeFlagSilence == null || flagsGetPreFlag == null) {
            return true;
        }
        try {
            Object priFlag = flagsGetPreFlag.invoke(null, flagName);
            if (priFlag == null) {
                plugin.debug("Dominion 找不到 PriFlag: " + flagName);
                return true;
            }
            Object res = checkPrivilegeFlagSilence.invoke(dominionApi, location, priFlag, player);
            return res instanceof Boolean ? (Boolean) res : true;
        } catch (Throwable t) {
            plugin.debug("Dominion 权限检查异常(" + flagName + "): " + t.getMessage());
            return true;
        }
    }

    private boolean checkResidenceFlag(Player player, Location location, String flag) {
        try {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Object residenceInstance = residenceClass.getMethod("getInstance").invoke(null);
            Object residenceManager = residenceInstance.getClass().getMethod("getResidenceManager").invoke(residenceInstance);
            Object claimedRes = residenceManager.getClass().getMethod("getByLoc", Location.class).invoke(residenceManager, location);
            if (claimedRes == null) return true;
            Object permissions = claimedRes.getClass().getMethod("getPermissions").invoke(claimedRes);
            return (boolean) permissions.getClass()
                    .getMethod("playerHas", Player.class, String.class, boolean.class)
                    .invoke(permissions, player, flag, false);
        } catch (Throwable t) {
            plugin.debug("Residence权限检查异常(" + flag + "): " + t.getMessage());
            return true;
        }
    }

    public boolean isAnyClaimPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
            || Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")
            || Bukkit.getPluginManager().isPluginEnabled("Residence")
            || Bukkit.getPluginManager().isPluginEnabled("Lands");
    }

    public String getDetailedStatus() {
        return String.format(
                "领地插件状态: Residence=%s, Dominion=%s, DominionAPI=%s (重试: %d/%d)",
                residenceEnabled.get() ? "已安装" : "未安装",
                dominionEnabled.get() ? "已安装" : "未安装",
                dominionReady.get() ? "就绪" : "未就绪",
                retryCount, maxRetries
        );
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 领地插件详细调试信息 ===\n");
        sb.append("Residence启用: ").append(residenceEnabled.get()).append("\n");
        sb.append("Dominion启用: ").append(dominionEnabled.get()).append("\n");
        sb.append("DominionAPI就绪: ").append(dominionReady.get()).append("\n");
        sb.append("重试次数: ").append(retryCount).append("/").append(maxRetries).append("\n");
        sb.append("DominionAPI对象: ").append(dominionApi != null ? "存在" : "null").append("\n");
        if (dominionPlugin != null) {
            sb.append("Dominion插件: ").append(dominionPlugin.getName())
                    .append(" v").append(dominionPlugin.getDescription().getVersion()).append("\n");
        }
        if (residencePlugin != null) {
            sb.append("Residence插件: ").append(residencePlugin.getName())
                    .append(" v").append(residencePlugin.getDescription().getVersion()).append("\n");
        }
        return sb.toString();
    }
}