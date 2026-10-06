package dev.mtree.deathpenalty;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All the logic. Uses the normal MAX_HEALTH attribute; never touches damage, drops or respawn.
 * Recovery uses wall-clock timestamps, so it survives restarts and works while the player is offline.
 * Only one one-shot scheduled task exists per online, injured player.
 */
final class HealthManager {
    private static final int MAX_STREAK = 3;

    private final DeathPenaltyPlugin plugin;
    private final DataStore data;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    /** Players who died and are waiting to respawn; value = whether a heart was actually lost. */
    private final Map<UUID, Boolean> pendingNotice = new HashMap<>();

    HealthManager(DeathPenaltyPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }
    int maxHearts() { return Math.max(1, cfg().getInt("health.maximum-hearts", 10)); }
    int minHearts() { return Math.max(1, Math.min(maxHearts(), cfg().getInt("health.minimum-hearts", 6))); }
    int startingHearts() { return Math.max(1, Math.min(maxHearts(), cfg().getInt("health.starting-hearts", 10))); }
    private boolean recoveryOn() { return cfg().getBoolean("recovery.enabled", true); }

    int hearts(UUID id) { return Math.min(maxHearts(), data.getHearts(id, startingHearts())); }
    long nextRecovery(UUID id) { return data.getNext(id); }

    private long dayMs() {
        return (long) (cfg().getDouble("recovery.day-length-minutes", 20) * 60_000L);
    }

    /** Days needed for a given death streak (1, 2, 3+), capped by maximum-days. */
    private double daysFor(int streak) {
        double d = switch (Math.max(1, streak)) {
            case 1 -> cfg().getDouble("recovery.first-death-days", 1);
            case 2 -> cfg().getDouble("recovery.second-death-days", 2);
            default -> cfg().getDouble("recovery.third-death-days", 3);
        };
        return Math.min(d, cfg().getDouble("recovery.maximum-days", 3));
    }

    private long delayMs(int streak) { return (long) (daysFor(streak) * dayMs()); }

    // ---------------------------------------------------------------- attribute

    void applyMax(Player p, int hearts) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double target = hearts * 2.0;
        if (attr.getBaseValue() != target) attr.setBaseValue(target);
        if (!p.isDead() && p.getHealth() > target) p.setHealth(target);
    }

    // ---------------------------------------------------------------- events

    void onJoin(Player p) {
        PackSender.send(plugin, p);
        UUID id = p.getUniqueId();
        if (!data.has(id)) return;           // never penalised: leave their health alone
        processRecovery(p, true);            // catch up on time spent offline
        applyMax(p, hearts(id));
    }

    void onQuit(Player p) {
        cancel(p.getUniqueId());
        pendingNotice.remove(p.getUniqueId());
    }

    void onDeath(Player p) {
        UUID id = p.getUniqueId();
        int max = maxHearts(), min = minHearts();
        int hearts = hearts(id);
        boolean lost = hearts > min;
        if (lost) hearts--;
        data.setHearts(id, hearts);

        if (hearts < max && recoveryOn()) {
            int streak = Math.min(MAX_STREAK, data.getStreak(id) + 1);
            data.setStreak(id, streak);
            data.setNext(id, System.currentTimeMillis() + delayMs(streak));
        }
        data.save();
        applyMax(p, hearts);
        pendingNotice.put(id, lost);
    }

    /** Called from PlayerRespawnEvent; effects/XP are applied one tick later, once the player is alive. */
    void onRespawn(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            Boolean lost = pendingNotice.remove(p.getUniqueId());
            if (lost == null) return;
            applyMax(p, hearts(p.getUniqueId()));
            applyEffects(p);
            applyXp(p);
            sendDeathMessages(p, lost);
            scheduleRecovery(p);
        });
    }

    // ---------------------------------------------------------------- penalties

    private void applyEffects(Player p) {
        int w = cfg().getInt("penalties.weakness.duration-seconds", 300);
        if (cfg().getBoolean("penalties.weakness.enabled", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, w * 20, 0, false, true, true));
        }
        int f = cfg().getInt("penalties.mining-fatigue.duration-seconds", 300);
        if (cfg().getBoolean("penalties.mining-fatigue.enabled", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, f * 20, 0, false, true, true));
        }
    }

    private void applyXp(Player p) {
        if (!cfg().getBoolean("penalties.xp.enabled", true)) return;
        double pct = Math.max(0, Math.min(100, cfg().getDouble("penalties.xp.percentage", 10)));
        int total = totalXp(p);
        int loss = (int) Math.floor(total * pct / 100.0);
        if (loss <= 0) return;                // e.g. vanilla death already cleared XP, nothing to take
        int remaining = Math.max(0, total - loss);
        p.setLevel(0);
        p.setExp(0f);
        p.giveExp(remaining);
    }

    private static int totalXp(Player p) {
        int l = p.getLevel();
        int base = l <= 16 ? l * l + 6 * l
                : l <= 31 ? (int) (2.5 * l * l - 40.5 * l + 360)
                : (int) (4.5 * l * l - 162.5 * l + 2220);
        int next = l <= 15 ? 2 * l + 7 : l <= 30 ? 5 * l - 38 : 9 * l - 158;
        return base + Math.round(p.getExp() * next);
    }

    private void sendDeathMessages(Player p, boolean lost) {
        FileConfiguration c = cfg();
        String hearts = String.valueOf(hearts(p.getUniqueId()));
        p.sendMessage(Text.color(c.getString("messages.death-header")));
        p.sendMessage(Text.color((lost ? c.getString("messages.death-health")
                : c.getString("messages.death-health-minimum")).replace("{hearts}", hearts)));
        if (c.getBoolean("penalties.weakness.enabled", true)) {
            p.sendMessage(Text.color(c.getString("messages.death-weakness").replace("{duration}",
                    Text.duration(c.getInt("penalties.weakness.duration-seconds", 300)))));
        }
        if (c.getBoolean("penalties.mining-fatigue.enabled", true)) {
            p.sendMessage(Text.color(c.getString("messages.death-fatigue").replace("{duration}",
                    Text.duration(c.getInt("penalties.mining-fatigue.duration-seconds", 300)))));
        }
        if (c.getBoolean("penalties.xp.enabled", true)) {
            p.sendMessage(Text.color(c.getString("messages.death-xp").replace("{percent}",
                    String.valueOf((int) c.getDouble("penalties.xp.percentage", 10)))));
        }
    }

    // ---------------------------------------------------------------- recovery

    /** Gives back every heart whose timer has passed. Safe to call any time. */
    void processRecovery(Player p, boolean notify) {
        UUID id = p.getUniqueId();
        int max = maxHearts();
        int hearts = hearts(id);

        if (hearts >= max || !recoveryOn()) {
            if (hearts >= max && (data.getNext(id) != 0 || data.getStreak(id) != 0)) {
                data.setNext(id, 0);
                data.setStreak(id, 0);
                data.save();
            }
            cancel(id);
            return;
        }

        long now = System.currentTimeMillis();
        long next = data.getNext(id);
        if (next == 0) next = now + delayMs(1);   // injured without a timer (e.g. admin command)

        int gained = 0;
        while (hearts < max && next <= now) {
            hearts++;
            gained++;
            data.setStreak(id, 0);                 // next heart uses the first-death delay again
            next = hearts < max ? next + delayMs(1) : 0;
        }
        data.setHearts(id, hearts);
        data.setNext(id, next);
        if (hearts >= max) data.setStreak(id, 0);
        data.save();

        if (gained > 0) {
            applyMax(p, hearts);
            if (notify) {
                for (String line : cfg().getStringList("messages.recovery")) {
                    p.sendMessage(Text.color(line.replace("{hearts}", String.valueOf(hearts2(id)))
                            .replace("{max}", String.valueOf(max))));
                }
            }
        }
        scheduleRecovery(p);
    }

    private int hearts2(UUID id) { return hearts(id); }

    /** One one-shot task, firing exactly when the next heart is due. */
    void scheduleRecovery(Player p) {
        UUID id = p.getUniqueId();
        cancel(id);
        if (!recoveryOn() || hearts(id) >= maxHearts()) return;
        long next = data.getNext(id);
        if (next == 0) return;
        long ticks = Math.max(1L, (next - System.currentTimeMillis()) / 50L);
        tasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) processRecovery(online, true);
        }, ticks));
    }

    private void cancel(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();
    }

    void shutdown() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
        pendingNotice.clear();
    }

    void rescheduleAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (data.has(p.getUniqueId())) processRecovery(p, true);
        }
    }

    // ---------------------------------------------------------------- admin

    void setHearts(Player p, int hearts) {
        UUID id = p.getUniqueId();
        int max = maxHearts();
        hearts = Math.max(1, Math.min(max, hearts));
        data.setHearts(id, hearts);
        data.setStreak(id, 0);
        data.setNext(id, hearts < max && recoveryOn() ? System.currentTimeMillis() + delayMs(1) : 0);
        data.save();
        applyMax(p, hearts);
        scheduleRecovery(p);
    }

    void reset(Player p) {
        UUID id = p.getUniqueId();
        cancel(id);
        data.remove(id);
        data.save();
        applyMax(p, startingHearts());
    }
}
