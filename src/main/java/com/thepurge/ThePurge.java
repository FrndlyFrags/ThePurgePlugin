package com.thepurge;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class ThePurge extends JavaPlugin {

    private PurgeManager purgeManager;
    private PurgeListener purgeListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.purgeManager = new PurgeManager(this);
        this.purgeListener = new PurgeListener(this.purgeManager);

        getServer().getPluginManager().registerEvents(this.purgeListener, this);
        getCommand("purge").setExecutor(new PurgeCommand(this, this.purgeManager));

        this.purgeManager.startIdleTimer();

        getLogger().info(ChatColor.GREEN + "[The Purge] Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        this.purgeManager.shutdown();
        getLogger().info(ChatColor.RED + "[The Purge] Plugin disabled.");
    }

    public PurgeManager getPurgeManager() {
        return purgeManager;
    }
}
