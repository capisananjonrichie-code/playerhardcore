package dev.mtree.deathpenalty;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DeathPenaltyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("reload", "status", "reset", "heal", "sethearts");

    private final DeathPenaltyPlugin plugin;
    private final HealthManager health;

    DeathPenaltyCommand(DeathPenaltyPlugin plugin, HealthManager health) {
        this.plugin = plugin;
        this.health = health;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(Text.color(plugin.getConfig().getString("messages.prefix", "") + text));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) { msg(s, "/" + label + " <reload|status|reset|heal|sethearts>"); return true; }
        String sub = a[0].toLowerCase();
        boolean admin = s.hasPermission("deathpenalty.admin");

        // status of yourself is allowed for everyone
        if (sub.equals("status") && a.length == 1) {
            if (!(s instanceof Player p)) { msg(s, "Console: /deathpenalty status <player>"); return true; }
            if (!p.hasPermission("deathpenalty.status") && !admin) { noPerm(s); return true; }
            status(s, p);
            return true;
        }
        if (!admin) { noPerm(s); return true; }

        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                msg(s, plugin.getConfig().getString("messages.reloaded"));
            }
            case "status" -> { Player t = target(s, a); if (t != null) status(s, t); }
            case "reset" -> {
                Player t = target(s, a);
                if (t != null) { health.reset(t); msg(s, "Reset " + t.getName() + "."); }
            }
            case "heal" -> {
                Player t = target(s, a);
                if (t != null) { health.setHearts(t, health.maxHearts()); msg(s, "Fully healed " + t.getName() + "."); }
            }
            case "sethearts" -> {
                Player t = target(s, a);
                if (t == null) return true;
                if (a.length < 3) { msg(s, "/deathpenalty sethearts <player> <amount>"); return true; }
                try {
                    health.setHearts(t, Integer.parseInt(a[2]));
                    msg(s, t.getName() + " now has " + health.hearts(t.getUniqueId()) + " hearts.");
                } catch (NumberFormatException ex) {
                    msg(s, "&cAmount must be a whole number.");
                }
            }
            default -> msg(s, "/" + label + " <reload|status|reset|heal|sethearts>");
        }
        return true;
    }

    private void noPerm(CommandSender s) { msg(s, plugin.getConfig().getString("messages.no-permission")); }

    private Player target(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "Specify a player."); return null; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) msg(s, plugin.getConfig().getString("messages.player-not-found"));
        return t;
    }

    private void status(CommandSender s, Player t) {
        UUID id = t.getUniqueId();
        StringBuilder sb = new StringBuilder("&f" + t.getName() + "&7: &c" + health.hearts(id) + "&7/&c" + health.maxHearts() + " &7hearts");
        long next = health.nextRecovery(id);
        if (health.hearts(id) < health.maxHearts() && next > 0) {
            long sec = Math.max(0, (next - System.currentTimeMillis()) / 1000);
            sb.append(" &8| &7next heart in &f").append(sec / 60).append("m ").append(sec % 60).append("s");
        }
        msg(s, sb.toString());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase())) out.add(x);
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
