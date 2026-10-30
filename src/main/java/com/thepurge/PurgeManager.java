package com.thepurge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Difficulty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;

public class PurgeManager {

    private final ThePurge plugin;
    private PurgeState state = PurgeState.IDLE;
    private BukkitTask currentTask;
    private int ticksRemaining;
    private int totalTicksPhase;

    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Integer> survivedTicks = new HashMap<>();
    private final Map<UUID, Color> playerColors = new HashMap<>();

    private BossBar purgeBossBar;
    private Team butcherTeam;
    private Team innocentTeam;
    private Team preyTeam;

    // Config values
    private int idleMinPlayers;
    private int idleMinDays;
    private int idleMaxDays;
    private int idleMeanDays;
    private int idleStdDev;

    private int warningDurationTicks;
    private String warningStartSoundStr;
    private float warningStartVolume;
    private float warningStartPitch;
    private String warningHeartbeatSoundStr;
    private float warningHeartbeatVolume;
    private float warningHeartbeatPitch;
    private int heartbeatFastInterval;
    private int heartbeatMediumInterval;
    private int heartbeatSlowInterval;
    private int heartbeatFastThreshold;
    private int heartbeatMediumThreshold;

    private int purgeDurationTicks;
    private String purgeStartSoundStr;
    private float purgeStartVolume;
    private float purgeStartPitch;
    private String purgeEndSoundStr;
    private float purgeEndVolume;
    private float purgeEndPitch;
    private long purgeTimeOfDay;
    private long purgeEndTimeOfDay;

    private String bossBarTitle;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;

    // Team config
    private String butcherPrefix;
    private ChatColor butcherColor;
    private Team.OptionStatus butcherCollision;
    private String innocentPrefix;
    private ChatColor innocentColor;
    private Team.OptionStatus innocentCollision;
    private String preyPrefix;
    private ChatColor preyColor;
    private Team.OptionStatus preyCollision;

    // Global effect config
    private boolean globalGlowingEnabled;
    private PotionEffectType globalGlowingType;
    private int globalGlowingAmplifier;
    private int globalGlowingDuration;
    private boolean globalGlowingAmbient;
    private boolean globalGlowingParticles;
    private boolean globalGlowingIcon;

    // Mob spawning config - common hostile mobs during purge
    private int mobSpawnRateModifier; // <1 reduces spawns, >1 increases
    private double mobMobHealthMultiplier; // Multiplier for mob health
    private double mobMobDamageMultiplier; // Multiplier for mob damage

    // Difficulty config
    private boolean forceHardDifficulty; // Force hard difficulty during purge
    private Difficulty originalDifficulty; // Track original difficulty to restore later

    // Role effects config
    private List<Map<?, ?>> butcherEffects;
    private List<Map<?, ?>> innocentEffects;
    private List<Map<?, ?>> preyEffects;

    // Particle config
    private boolean trailEnabled;
    private String trailParticleStr;
    private int trailCount;
    private double trailOffsetX;
    private double trailOffsetY;
    private double trailOffsetZ;
    private double trailSpeed;
    private double trailSize;
    private boolean trailRandomColor;
    private Color trailFixedColor;
    private boolean skyEnabled;
    private String skyParticleStr;
    private int skyCount;
    private double skyOffsetX;
    private double skyOffsetY;
    private double skyOffsetZ;
    private double skySpeed;
    private double skySize;
    private double skyYOffset;
    private Color skyColor;

    // PvP config
    private boolean pvpWhenStopped;

    // Locator bar config
    private boolean locatorBarManaged;

    // Messages
    private String msgWarningStarted;
    private String msgPurgeStarted;
    private String msgPurgeEnded;
    private String msgButcherReward;
    private String msgInnocentReward;
    private String msgPreyReward;

    public PurgeManager(ThePurge plugin) {
        this.plugin = plugin;
        this.purgeBossBar = Bukkit.createBossBar("PURGE ACTIVE", BarColor.RED, BarStyle.SEGMENTED_10);
        loadConfig();
        setupTeams();
    }

    public void loadConfig() {
        FileConfiguration c = plugin.getConfig();

        // Idle
        idleMinPlayers = c.getInt("idle.min-players", 3);
        idleMinDays = c.getInt("idle.timing.min-days", 7);
        idleMaxDays = c.getInt("idle.timing.max-days", 30);
        idleMeanDays = c.getInt("idle.timing.mean-days", 15);
        idleStdDev = c.getInt("idle.timing.std-dev", 5);

        // Warning
        warningDurationTicks = c.getInt("warning.duration-ticks", 600);
        warningStartSoundStr = c.getString("warning.start-sound.sound", "ENTITY_WITHER_SPAWN");
        warningStartVolume = (float) c.getDouble("warning.start-sound.volume", 0.5);
        warningStartPitch = (float) c.getDouble("warning.start-sound.pitch", 0.5);
        warningHeartbeatSoundStr = c.getString("warning.heartbeat-sound.sound", "ENTITY_WARDEN_HEARTBEAT");
        warningHeartbeatVolume = (float) c.getDouble("warning.heartbeat-sound.volume", 1.0);
        warningHeartbeatPitch = (float) c.getDouble("warning.heartbeat-sound.pitch", 1.0);
        heartbeatFastInterval = c.getInt("warning.heartbeat.fast-interval", 10);
        heartbeatMediumInterval = c.getInt("warning.heartbeat.medium-interval", 20);
        heartbeatSlowInterval = c.getInt("warning.heartbeat.slow-interval", 40);
        heartbeatFastThreshold = c.getInt("warning.heartbeat.fast-threshold", 100);
        heartbeatMediumThreshold = c.getInt("warning.heartbeat.medium-threshold", 200);

        // Purge
        purgeDurationTicks = c.getInt("purge.duration-ticks", 0);
        purgeStartSoundStr = c.getString("purge.start-sound.sound", "ENTITY_ENDER_DRAGON_GROWL");
        purgeStartVolume = (float) c.getDouble("purge.start-sound.volume", 1.0);
        purgeStartPitch = (float) c.getDouble("purge.start-sound.pitch", 0.8);
        purgeEndSoundStr = c.getString("purge.end-sound.sound", "ENTITY_PLAYER_LEVELUP");
        purgeEndVolume = (float) c.getDouble("purge.end-sound.volume", 1.0);
        purgeEndPitch = (float) c.getDouble("purge.end-sound.pitch", 0.5);
        purgeTimeOfDay = c.getLong("purge.time-of-day", 18000);
        purgeEndTimeOfDay = c.getLong("purge.end-time-of-day", 0);

        // PvP
        pvpWhenStopped = c.getBoolean("pvp.enabled-when-stopped", false);

        // Mob spawning config
        mobSpawnRateModifier = c.getInt("mob-spawn-rate-modifier", 0); // 0 = normal, negative = reduce, positive = increase
        mobMobHealthMultiplier = c.getDouble("mob-health-multiplier", 1.0);
        mobMobDamageMultiplier = c.getDouble("mob-damage-multiplier", 1.5);

        // Difficulty config
        forceHardDifficulty = c.getBoolean("force-hard-difficulty", true);

        // Locator bar
        locatorBarManaged = c.getBoolean("locator-bar.enabled", true);

        // Boss bar
        bossBarTitle = ChatColor.translateAlternateColorCodes('&', c.getString("boss-bar.title", "&4&lPURGE ACTIVE"));
        bossBarColor = parseBarColor(c.getString("boss-bar.color", "RED"));
        bossBarStyle = parseBarStyle(c.getString("boss-bar.style", "SEGMENTED_10"));

        // Teams
        butcherPrefix = ChatColor.translateAlternateColorCodes('&', c.getString("teams.butcher.prefix", "&4&l[BUTCHER] "));
        butcherColor = parseChatColor(c.getString("teams.butcher.color", "DARK_RED"));
        butcherCollision = parseCollisionRule(c.getString("teams.butcher.collision-rule", "ALWAYS"));
        innocentPrefix = ChatColor.translateAlternateColorCodes('&', c.getString("teams.innocent.prefix", "&6&l[INNOCENT] "));
        innocentColor = parseChatColor(c.getString("teams.innocent.color", "GOLD"));
        innocentCollision = parseCollisionRule(c.getString("teams.innocent.collision-rule", "NEVER"));
        preyPrefix = ChatColor.translateAlternateColorCodes('&', c.getString("teams.prey.prefix", "&2&l[PREY] "));
        preyColor = parseChatColor(c.getString("teams.prey.color", "GREEN"));
        preyCollision = parseCollisionRule(c.getString("teams.prey.collision-rule", "NEVER"));

        // Global effects
        ConfigurationSection ge = c.getConfigurationSection("global-effects.glowing");
        if (ge != null) {
            globalGlowingEnabled = ge.getBoolean("enabled", true);
            globalGlowingType = PotionEffectType.getByName(ge.getString("type", "GLOWING"));
            globalGlowingAmplifier = ge.getInt("amplifier", 0);
            globalGlowingDuration = ge.getInt("duration", 40);
            globalGlowingAmbient = ge.getBoolean("ambient", false);
            globalGlowingParticles = ge.getBoolean("show-particles", false);
            globalGlowingIcon = ge.getBoolean("show-icon", false);
        } else {
            globalGlowingEnabled = true;
            globalGlowingType = PotionEffectType.GLOWING;
            globalGlowingAmplifier = 0;
            globalGlowingDuration = 40;
            globalGlowingAmbient = false;
            globalGlowingParticles = false;
            globalGlowingIcon = false;
        }

        // Role effects
        butcherEffects = c.getMapList("roles.butcher.effects");
        innocentEffects = c.getMapList("roles.innocent.effects");
        preyEffects = c.getMapList("roles.prey.effects");

        // Particles
        trailEnabled = c.getBoolean("particles.trail.enabled", true);
        trailParticleStr = c.getString("particles.trail.particle", "DUST");
        trailCount = c.getInt("particles.trail.count", 2);
        trailOffsetX = c.getDouble("particles.trail.offset-x", 0.3);
        trailOffsetY = c.getDouble("particles.trail.offset-y", 0.1);
        trailOffsetZ = c.getDouble("particles.trail.offset-z", 0.3);
        trailSpeed = c.getDouble("particles.trail.speed", 0);
        trailSize = c.getDouble("particles.trail.size", 1.5);
        trailRandomColor = c.getBoolean("particles.trail.random-color", true);
        int tR = c.getInt("particles.trail.fixed-color.red", 255);
        int tG = c.getInt("particles.trail.fixed-color.green", 255);
        int tB = c.getInt("particles.trail.fixed-color.blue", 255);
        trailFixedColor = Color.fromRGB(tR, tG, tB);

        skyEnabled = c.getBoolean("particles.sky.enabled", true);
        skyParticleStr = c.getString("particles.sky.particle", "DUST");
        skyCount = c.getInt("particles.sky.count", 10);
        skyOffsetX = c.getDouble("particles.sky.offset-x", 5);
        skyOffsetY = c.getDouble("particles.sky.offset-y", 2);
        skyOffsetZ = c.getDouble("particles.sky.offset-z", 5);
        skySpeed = c.getDouble("particles.sky.speed", 0);
        skySize = c.getDouble("particles.sky.size", 2.0);
        skyYOffset = c.getDouble("particles.sky.y-offset", 10);
        int sR = c.getInt("particles.sky.color.red", 255);
        int sG = c.getInt("particles.sky.color.green", 0);
        int sB = c.getInt("particles.sky.color.blue", 0);
        skyColor = Color.fromRGB(sR, sG, sB);

        // Messages
        msgWarningStarted = ChatColor.translateAlternateColorCodes('&', c.getString("messages.warning-started", "&4&oThe air grows still... animals flee... The Blood Moon rises."));
        msgPurgeStarted = ChatColor.translateAlternateColorCodes('&', c.getString("messages.purge-started", "&4&lLet the hunt begin"));
        msgPurgeEnded = ChatColor.translateAlternateColorCodes('&', c.getString("messages.purge-ended", "&a&oThe Purge has ended. Peace is restored."));
        msgButcherReward = ChatColor.translateAlternateColorCodes('&', c.getString("messages.butcher-reward", "%s &cwas the Butcher! Let the blood soak their name."));
        msgInnocentReward = ChatColor.translateAlternateColorCodes('&', c.getString("messages.innocent-reward", "%s &6survived with clean hands. They are the Innocent."));
        msgPreyReward = ChatColor.translateAlternateColorCodes('&', c.getString("messages.prey-reward", "%s &afell the most. They are the Prey."));

        // Apply live-updatable settings
        purgeBossBar.setTitle(bossBarTitle);
        purgeBossBar.setColor(bossBarColor);
        purgeBossBar.setStyle(bossBarStyle);
        setupTeams();
    }

    private ChatColor parseChatColor(String name) {
        try {
            return ChatColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return ChatColor.WHITE;
        }
    }

    private BarColor parseBarColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarColor.RED;
        }
    }

    private BarStyle parseBarStyle(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarStyle.SEGMENTED_10;
        }
    }

    private Team.OptionStatus parseCollisionRule(String name) {
        try {
            return Team.OptionStatus.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Team.OptionStatus.ALWAYS;
        }
    }

    @SuppressWarnings("deprecation")
    private Sound parseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (Exception e) {
            try {
                return Sound.valueOf("ENTITY_WITHER_SPAWN");
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Particle.DUST;
        }
    }

    private void setupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        butcherTeam = board.getTeam("purge_butcher");
        if (butcherTeam == null) butcherTeam = board.registerNewTeam("purge_butcher");
        butcherTeam.setColor(butcherColor);
        butcherTeam.setPrefix(butcherPrefix);
        butcherTeam.setOption(Team.Option.COLLISION_RULE, butcherCollision);

        innocentTeam = board.getTeam("purge_innocent");
        if (innocentTeam == null) innocentTeam = board.registerNewTeam("purge_innocent");
        innocentTeam.setColor(innocentColor);
        innocentTeam.setPrefix(innocentPrefix);
        innocentTeam.setOption(Team.Option.COLLISION_RULE, innocentCollision);

        preyTeam = board.getTeam("purge_prey");
        if (preyTeam == null) preyTeam = board.registerNewTeam("purge_prey");
        preyTeam.setColor(preyColor);
        preyTeam.setPrefix(preyPrefix);
        preyTeam.setOption(Team.Option.COLLISION_RULE, preyCollision);
    }

    private void applyLocatorBar(boolean enabled) {
        if (!locatorBarManaged) return;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.LOCATOR_BAR, enabled);
        }
    }

    private int generateRandomMinecraftDays() {
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        int days = (int) (idleMeanDays + (gaussian * idleStdDev));
        if (days < idleMinDays) days = idleMinDays;
        if (days > idleMaxDays) days = idleMaxDays;
        return days * 24000;
    }

    public void startIdleTimer() {
        if (currentTask != null) currentTask.cancel();
        state = PurgeState.IDLE;
        applyLocatorBar(false);

        ticksRemaining = generateRandomMinecraftDays();
        totalTicksPhase = ticksRemaining;

        currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                ticksRemaining--;
                if (ticksRemaining <= 0) {
                    if (Bukkit.getOnlinePlayers().size() >= idleMinPlayers) {
                        startWarning();
                    } else {
                        startIdleTimer();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void startWarning() {
        if (currentTask != null) currentTask.cancel();
        state = PurgeState.WARNING;
        ticksRemaining = warningDurationTicks;
        totalTicksPhase = warningDurationTicks;

        Bukkit.broadcast(Component.text(msgWarningStarted));

        Sound startSound = parseSound(warningStartSoundStr);
        if (startSound != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), startSound, warningStartVolume, warningStartPitch);
            }
        }

        currentTask = new BukkitRunnable() {
            int heartbeatInterval = heartbeatSlowInterval;
            @Override
            public void run() {
                ticksRemaining--;

                if (ticksRemaining > heartbeatMediumThreshold) heartbeatInterval = heartbeatSlowInterval;
                else if (ticksRemaining > heartbeatFastThreshold) heartbeatInterval = heartbeatMediumInterval;
                else heartbeatInterval = heartbeatFastInterval;

                if (ticksRemaining % heartbeatInterval == 0) {
                    Sound hb = parseSound(warningHeartbeatSoundStr);
                    if (hb != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), hb, warningHeartbeatVolume, warningHeartbeatPitch);
                        }
                    }
                }

                if (ticksRemaining <= 0) {
                    startPurge();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void assignRandomColor(Player p) {
        if (!playerColors.containsKey(p.getUniqueId())) {
            Color randomColor = Color.fromRGB(
                ThreadLocalRandom.current().nextInt(256),
                ThreadLocalRandom.current().nextInt(256),
                ThreadLocalRandom.current().nextInt(256)
            );
            playerColors.put(p.getUniqueId(), randomColor);
        }
    }

    public void startPurge() {
        if (currentTask != null) currentTask.cancel();
        state = PurgeState.ACTIVE;

        if (purgeDurationTicks > 0) {
            ticksRemaining = purgeDurationTicks;
        } else {
            ticksRemaining = generateRandomMinecraftDays();
        }
        totalTicksPhase = ticksRemaining;

        kills.clear();
        deaths.clear();
        survivedTicks.clear();
        playerColors.clear();

        Bukkit.broadcast(Component.text(msgPurgeStarted));
        applyLocatorBar(true);

        purgeBossBar.setTitle(bossBarTitle);
        purgeBossBar.setColor(bossBarColor);
        purgeBossBar.setStyle(bossBarStyle);
        purgeBossBar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                Component.text("PURGE ACTIVE").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            Sound startSound = parseSound(purgeStartSoundStr);
            if (startSound != null) {
                p.playSound(p.getLocation(), startSound, purgeStartVolume, purgeStartPitch);
            }
            purgeBossBar.addPlayer(p);
            assignRandomColor(p);
        }

        currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                ticksRemaining--;

                // Lock world time to midnight during the purge
                if (Bukkit.getWorlds().size() > 0) {
                    Bukkit.getWorlds().get(0).setTime(purgeTimeOfDay);
                }

                // Save original difficulty if we need to restore it later
                if (forceHardDifficulty && originalDifficulty == null) {
                    originalDifficulty = Bukkit.getWorlds().get(0).getDifficulty();
                }

                // Force hard difficulty during purge
                if (forceHardDifficulty) {
                    for (World world : Bukkit.getWorlds()) {
                        world.setDifficulty(Difficulty.HARD);
                    }
                }

                double progress = (double) ticksRemaining / totalTicksPhase;
                if (progress < 0) progress = 0;
                if (progress > 1) progress = 1;
                purgeBossBar.setProgress(progress);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    survivedTicks.put(p.getUniqueId(), survivedTicks.getOrDefault(p.getUniqueId(), 0) + 1);

                    // Global glowing effect
                    if (globalGlowingEnabled && globalGlowingType != null) {
                        p.addPotionEffect(new PotionEffect(globalGlowingType, globalGlowingDuration, globalGlowingAmplifier, globalGlowingAmbient, globalGlowingParticles, globalGlowingIcon));
                    }

                    // Role-based effects from config
                    if (butcherTeam.hasEntry(p.getName())) {
                        applyEffectsFromConfig(p, butcherEffects);
                    }
                    if (preyTeam.hasEntry(p.getName())) {
                        applyEffectsFromConfig(p, preyEffects);
                    }
                    if (innocentTeam.hasEntry(p.getName())) {
                        applyEffectsFromConfig(p, innocentEffects);
                    }

                    // Trail particles
                    if (trailEnabled) {
                        Particle trailParticle = parseParticle(trailParticleStr);
                        Color trailColor = trailRandomColor ? playerColors.getOrDefault(p.getUniqueId(), trailFixedColor) : trailFixedColor;
                        if (trailParticle == Particle.DUST) {
                            Particle.DustOptions trailDust = new Particle.DustOptions(trailColor, (float) trailSize);
                            p.getWorld().spawnParticle(trailParticle, p.getLocation().add(0, 0.2, 0), trailCount, trailOffsetX, trailOffsetY, trailOffsetZ, trailSpeed, trailDust);
                        } else {
                            p.getWorld().spawnParticle(trailParticle, p.getLocation().add(0, 0.2, 0), trailCount, trailOffsetX, trailOffsetY, trailOffsetZ, trailSpeed);
                        }
                    }

                    // Sky particles
                    if (skyEnabled) {
                        Particle skyParticle = parseParticle(skyParticleStr);
                        if (skyParticle == Particle.DUST) {
                            Particle.DustOptions skyDust = new Particle.DustOptions(skyColor, (float) skySize);
                            p.getWorld().spawnParticle(skyParticle, p.getLocation().add(0, skyYOffset, 0), skyCount, skyOffsetX, skyOffsetY, skyOffsetZ, skySpeed, skyDust);
                        } else {
                            p.getWorld().spawnParticle(skyParticle, p.getLocation().add(0, skyYOffset, 0), skyCount, skyOffsetX, skyOffsetY, skyOffsetZ, skySpeed);
                        }
                    }
                }

                if (ticksRemaining <= 0) {
                    endPurge();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @SuppressWarnings("unchecked")
    private void applyEffectsFromConfig(Player p, List<Map<?, ?>> effects) {
        for (Map<?, ?> entry : effects) {
            Map<String, Object> effect = (Map<String, Object>) entry;
            boolean enabled = (boolean) effect.getOrDefault("enabled", true);
            if (!enabled) continue;

            String typeName = (String) effect.getOrDefault("type", "");
            PotionEffectType type = PotionEffectType.getByName(typeName);
            if (type == null) continue;

            int amplifier = ((Number) effect.getOrDefault("amplifier", 0)).intValue();
            int duration = ((Number) effect.getOrDefault("duration", 40)).intValue();
            boolean ambient = (boolean) effect.getOrDefault("ambient", false);
            boolean particles = (boolean) effect.getOrDefault("show-particles", true);
            boolean icon = (boolean) effect.getOrDefault("show-icon", true);
            p.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }
    }

    public void endPurge() {
        if (currentTask != null) currentTask.cancel();

        Bukkit.broadcast(Component.text(msgPurgeEnded));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Sound endSound = parseSound(purgeEndSoundStr);
            if (endSound != null) {
                p.playSound(p.getLocation(), endSound, purgeEndVolume, purgeEndPitch);
            }
            purgeBossBar.removePlayer(p);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.removePotionEffect(PotionEffectType.STRENGTH);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        applyLocatorBar(false);

        // Restore original difficulty if it was forced
        if (forceHardDifficulty && originalDifficulty != null) {
            for (World world : Bukkit.getWorlds()) {
                world.setDifficulty(originalDifficulty);
            }
            originalDifficulty = null;
        }

        if (Bukkit.getWorlds().size() > 0) {
            World w = Bukkit.getWorlds().get(0);
            w.setTime(purgeEndTimeOfDay);
        }

        calculateRewards();
        startIdleTimer();
    }

    public void forceReset() {
        if (currentTask != null) currentTask.cancel();
        purgeBossBar.removeAll();
        kills.clear();
        survivedTicks.clear();
        playerColors.clear();
        deaths.clear();

        for (String entry : butcherTeam.getEntries()) butcherTeam.removeEntry(entry);
        for (String entry : innocentTeam.getEntries()) innocentTeam.removeEntry(entry);
        for (String entry : preyTeam.getEntries()) preyTeam.removeEntry(entry);

        if (Bukkit.getWorlds().size() > 0) {
            World w = Bukkit.getWorlds().get(0);
            w.setTime(purgeEndTimeOfDay);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.removePotionEffect(PotionEffectType.STRENGTH);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        startIdleTimer();
    }

    private void calculateRewards() {
        for (String entry : butcherTeam.getEntries()) butcherTeam.removeEntry(entry);
        for (String entry : innocentTeam.getEntries()) innocentTeam.removeEntry(entry);

        int maxKills = -1;
        UUID butcherId = null;

        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (entry.getValue() > maxKills && entry.getValue() > 0) {
                maxKills = entry.getValue();
                butcherId = entry.getKey();
            }
        }

        int maxSurvived = -1;
        UUID innocentId = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            int k = kills.getOrDefault(p.getUniqueId(), 0);
            if (k == 0) {
                int s = survivedTicks.getOrDefault(p.getUniqueId(), 0);
                if (s > maxSurvived && s > 0) {
                    maxSurvived = s;
                    innocentId = p.getUniqueId();
                }
            }
        }

        if (butcherId != null) {
            Player butcher = Bukkit.getPlayer(butcherId);
            if (butcher != null) {
                Bukkit.broadcast(Component.text(String.format(msgButcherReward, butcher.getName())));
                butcherTeam.addEntry(butcher.getName());
            }
        }

        if (innocentId != null) {
            Player innocent = Bukkit.getPlayer(innocentId);
            if (innocent != null) {
                Bukkit.broadcast(Component.text(String.format(msgInnocentReward, innocent.getName())));
                innocentTeam.addEntry(innocent.getName());
            }
        }

        for (String entry : preyTeam.getEntries()) preyTeam.removeEntry(entry);
        int maxDeaths = -1;
        UUID preyId = null;
        for (Map.Entry<UUID, Integer> entry : deaths.entrySet()) {
            if (entry.getValue() > maxDeaths && entry.getValue() > 0) {
                maxDeaths = entry.getValue();
                preyId = entry.getKey();
            }
        }
        if (preyId != null) {
            Player prey = Bukkit.getPlayer(preyId);
            if (prey != null) {
                Bukkit.broadcast(Component.text(String.format(msgPreyReward, prey.getName())));
                preyTeam.addEntry(prey.getName());
            }
        }
    }

    public void recordKill(UUID killer) {
        kills.put(killer, kills.getOrDefault(killer, 0) + 1);
    }

    public void recordDeath(UUID victim) {
        deaths.put(victim, deaths.getOrDefault(victim, 0) + 1);
    }

    public void resetSurvivalTime(UUID player) {
        survivedTicks.put(player, 0);
    }

    public Team getButcherTeam() { return butcherTeam; }
    public Team getInnocentTeam() { return innocentTeam; }
    public Team getPreyTeam() { return preyTeam; }

    public PurgeState getState() { return state; }
    public int getTicksRemaining() { return ticksRemaining; }
    public boolean isPvpWhenStopped() { return pvpWhenStopped; }

    public BossBar getBossBar() { return purgeBossBar; }

    public int getMobSpawnRateModifier() { return mobSpawnRateModifier; }
    public double getMobHealthMultiplier() { return mobMobHealthMultiplier; }
    public double getMobDamageMultiplier() { return mobMobDamageMultiplier; }

    public void shutdown() {
        if (currentTask != null) currentTask.cancel();
        purgeBossBar.removeAll();
    }
}
