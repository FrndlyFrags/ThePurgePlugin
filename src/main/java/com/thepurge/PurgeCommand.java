package com.thepurge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

public class PurgeCommand implements CommandExecutor {
    private final ThePurge plugin;
    private final PurgeManager manager;

    public PurgeCommand(ThePurge plugin, PurgeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /purge <start|stop|status|roles|reset|reload|testrole|help>");
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.GOLD + "=== ThePurge Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/purge start " + ChatColor.WHITE + "- Force start the purge warning phase");
            sender.sendMessage(ChatColor.YELLOW + "/purge stop " + ChatColor.WHITE + "- Force end the purge");
            sender.sendMessage(ChatColor.YELLOW + "/purge status " + ChatColor.WHITE + "- Show current purge state and time remaining");
            sender.sendMessage(ChatColor.YELLOW + "/purge roles " + ChatColor.WHITE + "- Show butcher/prey/innocent team members");
            sender.sendMessage(ChatColor.YELLOW + "/purge reset " + ChatColor.WHITE + "- Reset all stats, timers, and titles");
            sender.sendMessage(ChatColor.YELLOW + "/purge reload " + ChatColor.WHITE + "- Reload config.yml settings");
            sender.sendMessage(ChatColor.YELLOW + "/purge testrole <role> [player] " + ChatColor.WHITE + "- Temporarily assign a test role");
            sender.sendMessage(ChatColor.YELLOW + "/purge help " + ChatColor.WHITE + "- Show this help menu");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (manager.getState() == PurgeState.ACTIVE) {
                sender.sendMessage(ChatColor.RED + "A purge is already active!");
            } else {
                manager.startWarning();
                sender.sendMessage(ChatColor.GREEN + "[Admin] The Purge has been forcefully initiated!");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("stop")) {
            if (manager.getState() == PurgeState.IDLE) {
                sender.sendMessage(ChatColor.RED + "[Admin] No active purge to stop.");
            } else {
                manager.endPurge();
                sender.sendMessage(ChatColor.GREEN + "[Admin] The Purge has been forcefully terminated!");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("status")) {
            int ticks = manager.getTicksRemaining();
            int days = ticks / 24000;
            int hours = (ticks % 24000) / 1000;
            int realSeconds = ticks / 20;
            sender.sendMessage(ChatColor.GOLD + "=== Purge Status ===");
            sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + manager.getState().name());
            if (manager.getState() == PurgeState.WARNING) {
                sender.sendMessage(ChatColor.YELLOW + "Time Remaining: " + ChatColor.WHITE + realSeconds + " real seconds.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Time Remaining: " + ChatColor.WHITE + days + " MC Days, " + hours + " MC Hours (" + realSeconds + " real seconds).");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("roles")) {
            sender.sendMessage(ChatColor.GOLD + "=== Role Overview ===");
            Team butcher = manager.getButcherTeam();
            Team innocent = manager.getInnocentTeam();
            Team prey = manager.getPreyTeam();
            sender.sendMessage(ChatColor.RED + "Butcher: " + ChatColor.WHITE + String.join(", ", butcher.getEntries()));
            sender.sendMessage(ChatColor.YELLOW + "Innocent: " + ChatColor.WHITE + String.join(", ", innocent.getEntries()));
            sender.sendMessage(ChatColor.GREEN + "Prey: " + ChatColor.WHITE + String.join(", ", prey.getEntries()));
            return true;
        } else if (args[0].equalsIgnoreCase("reset")) {
            manager.forceReset();
            sender.sendMessage(ChatColor.GREEN + "[Admin] The Purge stats, timers, and titles have been completely reset.");
            return true;
        } else if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            manager.loadConfig();
            sender.sendMessage(ChatColor.GREEN + "[Admin] Configuration reloaded from config.yml.");
            return true;
        } else if (args[0].equalsIgnoreCase("testrole")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /purge testrole <butcher|prey|innocent|clear> [player]");
                return true;
            }

            String roleName = args[1].toLowerCase();
            Player target;

            if (args.length >= 3) {
                target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' not found or offline.");
                    return true;
                }
            } else if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /purge testrole <role> <player>");
                return true;
            }

            manager.getButcherTeam().removeEntry(target.getName());
            manager.getInnocentTeam().removeEntry(target.getName());
            manager.getPreyTeam().removeEntry(target.getName());
            target.removePotionEffect(PotionEffectType.STRENGTH);
            target.removePotionEffect(PotionEffectType.WEAKNESS);
            target.removePotionEffect(PotionEffectType.INVISIBILITY);
            target.removePotionEffect(PotionEffectType.GLOWING);

            switch (roleName) {
                case "butcher":
                    manager.getButcherTeam().addEntry(target.getName());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, false, false));
                    sender.sendMessage(ChatColor.DARK_RED + "[Test] " + target.getName() + " is now BUTCHER (Strength I + Glowing for 30s)");
                    target.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "[Test] You have been assigned BUTCHER role!");
                    break;
                case "prey":
                    manager.getPreyTeam().addEntry(target.getName());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, false, false));
                    sender.sendMessage(ChatColor.GREEN + "[Test] " + target.getName() + " is now PREY (Weakness I + Glowing for 30s)");
                    target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[Test] You have been assigned PREY role!");
                    break;
                case "innocent":
                    manager.getInnocentTeam().addEntry(target.getName());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, false, false));
                    sender.sendMessage(ChatColor.GOLD + "[Test] " + target.getName() + " is now INNOCENT (Invisibility + Glowing for 30s)");
                    target.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Test] You have been assigned INNOCENT role!");
                    break;
                case "clear":
                    sender.sendMessage(ChatColor.GREEN + "[Test] Cleared all roles and effects from " + target.getName());
                    target.sendMessage(ChatColor.GREEN + "[Test] Your test roles have been cleared.");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown role. Use: butcher, prey, innocent, or clear");
                    break;
            }
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /purge help for available commands.");
            return true;
        }
    }
}
