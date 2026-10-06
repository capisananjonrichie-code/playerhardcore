package dev.mtree.deathpenalty;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Sends the "Hardcore Hearts" resource pack to Java players on join. One packet per join, nothing else. */
final class PackSender {
    private PackSender() {}

    static void send(DeathPenaltyPlugin plugin, Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("hardcore-hearts.enabled", true)) return;
        String url = c.getString("hardcore-hearts.resource-pack-url", "").trim();
        if (url.isEmpty()) return;

        // Floodgate (Bedrock) players have a UUID whose most-significant bits are 0. They cannot use Java packs.
        UUID id = p.getUniqueId();
        if (id.getMostSignificantBits() == 0L) return;

        byte[] hash = null;
        String sha1 = c.getString("hardcore-hearts.resource-pack-sha1", "").trim();
        if (sha1.length() == 40) {
            try {
                hash = new byte[20];
                for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                hash = null;
            }
        }
        // Fixed pack id per URL so the client replaces the pack instead of stacking copies.
        UUID packId = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        Component prompt = Text.color(c.getString("hardcore-hearts.prompt", "&7MTREE uses Hardcore-style hearts."));
        p.setResourcePack(packId, url, hash, prompt, c.getBoolean("hardcore-hearts.required", false));
    }
}
