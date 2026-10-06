package dev.mtree.deathpenalty;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Persists per-player state in data.yml (keyed by UUID). Tiny file, written only when something changes. */
final class DataStore {
    private final DeathPenaltyPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    DataStore(DeathPenaltyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    boolean has(UUID id) { return yaml.contains("players." + id); }

    int getHearts(UUID id, int def) { return yaml.getInt("players." + id + ".hearts", def); }
    void setHearts(UUID id, int v) { yaml.set("players." + id + ".hearts", v); }

    /** Consecutive deaths without a full recovery step. */
    int getStreak(UUID id) { return yaml.getInt("players." + id + ".streak", 0); }
    void setStreak(UUID id, int v) { yaml.set("players." + id + ".streak", v); }

    /** Epoch millis when the next heart comes back (0 = none pending). */
    long getNext(UUID id) { return yaml.getLong("players." + id + ".next", 0L); }
    void setNext(UUID id, long v) { yaml.set("players." + id + ".next", v); }

    void remove(UUID id) { yaml.set("players." + id, null); }

    void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
