package dev.mtree.deathpenalty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathPenaltyPlugin extends JavaPlugin {
    private DataStore data;
    private HealthManager health;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        health = new HealthManager(this, data);

        getServer().getPluginManager().registerEvents(new DeathListener(health), this);
        DeathPenaltyCommand cmd = new DeathPenaltyCommand(this, health);
        getCommand("deathpenalty").setExecutor(cmd);
        getCommand("deathpenalty").setTabCompleter(cmd);

        if (getConfig().getBoolean("hardcore-hearts.enabled", true)) {
            getLogger().info("Hardcore heart look is done with a resource pack (see README); the plugin only manages max health.");
        }
        // /reload safety: sync players who are already online
        for (Player p : Bukkit.getOnlinePlayers()) health.onJoin(p);
    }

    @Override
    public void onDisable() {
        if (health != null) health.shutdown();
        if (data != null) data.save();
    }

    void reloadAll() {
        reloadConfig();
        health.rescheduleAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (data.has(p.getUniqueId())) health.applyMax(p, health.hearts(p.getUniqueId()));
        }
    }
}
