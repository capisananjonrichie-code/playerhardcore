package dev.mtree.deathpenalty;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Only observes events. Never edits drops, XP drops, the respawn location or the death message. */
final class DeathListener implements Listener {
    private final HealthManager health;

    DeathListener(HealthManager health) { this.health = health; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { health.onDeath(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) { health.onRespawn(e.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { health.onJoin(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { health.onQuit(e.getPlayer()); }
}
