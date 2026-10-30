package com.thepurge;

import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class PurgeListener implements Listener {
    private final PurgeManager manager;

    public PurgeListener(PurgeManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            if (manager.getState() != PurgeState.ACTIVE && !manager.isPvpWhenStopped()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        // During purge, modify mob spawning
        if (manager.getState() == PurgeState.ACTIVE) {
            // Cancel spawn based on spawn rate modifier
            // mobSpawnRateModifier: 0 = normal, negative = reduce, positive = increase
            int modifier = manager.getMobSpawnRateModifier();
            if (modifier <= 0) {
                // Negative or zero modifier: cancel spawn entirely
                event.setCancelled(true);
            } else if (modifier < 100) {
                // Positive modifier less than 100: reduced spawn chance
                double randomChance = Math.random() * 100;
                if (randomChance > modifier) {
                    event.setCancelled(true);
                }
            }
            // Apply mob health multiplier if the mob is a living entity
            // CreatureSpawnEvent.getEntity() returns LivingEntity directly
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity mob = (LivingEntity) event.getEntity();
                mob.setMaxHealth(mob.getMaxHealth() * manager.getMobHealthMultiplier());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (manager.getState() == PurgeState.ACTIVE) {
            manager.resetSurvivalTime(victim.getUniqueId());
            manager.recordDeath(victim.getUniqueId());
            if (killer != null && killer != victim) {
                manager.recordKill(killer.getUniqueId());
            }

            // Hide the killer's name with scribbled text
            String msg = event.getDeathMessage();
            if (msg != null && killer != null) {
                String obscured = ChatColor.MAGIC + "????" + ChatColor.RESET;
                msg = msg.replace(killer.getName(), obscured);
                event.setDeathMessage(msg);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (manager.getState() == PurgeState.ACTIVE) {
            manager.getBossBar().addPlayer(event.getPlayer());
            manager.assignRandomColor(event.getPlayer());
        }
    }
}