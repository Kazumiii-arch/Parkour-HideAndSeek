package com.kazumiii.parkourhideseek;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ParkourHideAndSeek extends JavaPlugin implements Listener, CommandExecutor {
    private enum State { WAITING, ACTIVE }

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> seekers = new HashSet<>();
    private final Map<UUID, GameMode> previousModes = new HashMap<>();
    private State state = State.WAITING;
    private BukkitTask roundTask;
    private BukkitTask pulseTask;
    private int remainingSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("phs").setExecutor(this);
        getLogger().info("Parkour Hide & Seek is ready. Use /phs help.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        restorePlayers();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("join")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(color("&cOnly players can join.")); return true; }
            if (state != State.WAITING) { player.sendMessage(color("&cA round is already running.")); return true; }
            if (!participants.add(player.getUniqueId())) { player.sendMessage(color("&eYou are already queued.")); return true; }
            previousModes.put(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.ADVENTURE);
            Location lobby = getLocation("locations.lobby");
            if (lobby != null) player.teleport(lobby);
            broadcast("&a" + player.getName() + " joined! &7(" + participants.size() + " players)");
            return true;
        }
        if (sub.equals("leave")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(color("&cOnly players can leave.")); return true; }
            UUID id = player.getUniqueId();
            if (!participants.remove(id)) { player.sendMessage(color("&cYou are not in the game.")); return true; }
            hiders.remove(id);
            seekers.remove(id);
            restorePlayer(player);
            player.sendMessage(color("&eYou left the game."));
            checkWinner();
            return true;
        }
        if (sub.equals("status")) {
            sender.sendMessage(color("&6Parkour Hide & Seek &7| " + (state == State.ACTIVE ? "&cRound active" : "&aWaiting")
                    + " &7| Players: &f" + participants.size() + " &7| Hiders: &f" + hiders.size() + " &7| Seekers: &f" + seekers.size()));
            return true;
        }
        if (sub.equals("start")) {
            if (!sender.hasPermission("parkourhideseek.admin")) { sender.sendMessage(color("&cYou need parkourhideseek.admin.")); return true; }
            startRound(sender);
            return true;
        }
        if (sub.equals("stop")) {
            if (!sender.hasPermission("parkourhideseek.admin")) { sender.sendMessage(color("&cYou need parkourhideseek.admin.")); return true; }
            if (state == State.WAITING) sender.sendMessage(color("&eNo round is running.")); else finishRound("&eThe round was stopped by an admin.");
            return true;
        }
        if (sub.startsWith("set")) {
            if (!sender.hasPermission("parkourhideseek.admin")) { sender.sendMessage(color("&cYou need parkourhideseek.admin.")); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(color("&cRun this command in-game.")); return true; }
            String key = switch (sub) {
                case "setlobby" -> "lobby";
                case "sethider" -> "hider-spawn";
                case "setseeker" -> "seeker-spawn";
                default -> null;
            };
            if (key == null) { sendHelp(sender); return true; }
            saveLocation("locations." + key, player.getLocation());
            sender.sendMessage(color("&aSaved " + key + " location."));
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void startRound(CommandSender sender) {
        if (state != State.WAITING) { sender.sendMessage(color("&cA round is already active.")); return; }
        int minimum = Math.max(2, getConfig().getInt("min-players", 2));
        if (participants.size() < minimum) { sender.sendMessage(color("&cNeed at least " + minimum + " joined players.")); return; }
        Location hiderSpawn = getLocation("locations.hider-spawn");
        Location seekerSpawn = getLocation("locations.seeker-spawn");
        if (hiderSpawn == null || seekerSpawn == null) { sender.sendMessage(color("&cSet both spawns first: /phs sethider and /phs setseeker.")); return; }

        state = State.ACTIVE;
        remainingSeconds = Math.max(30, getConfig().getInt("round-seconds", 180));
        ArrayList<UUID> players = new ArrayList<>(participants);
        UUID chosenSeeker = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) { participants.remove(id); continue; }
            if (id.equals(chosenSeeker)) {
                seekers.add(id);
                player.teleport(seekerSpawn);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15 * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 15 * 20, 5));
                player.sendTitle(color("&cSEEKER"), color("&7Released in 15 seconds!"), 5, 40, 10);
            } else {
                hiders.add(id);
                player.teleport(hiderSpawn);
                player.sendTitle(color("&aHIDE!"), color("&7Use parkour to escape. Find a route and keep moving!"), 5, 50, 10);
            }
        }
        broadcast("&6Round started! &eHiders get 15 seconds to hide before the seeker is released.");
        roundTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (state != State.ACTIVE) return;
            remainingSeconds--;
            if (remainingSeconds <= 0) { finishRound("&aHiders win! They survived the round."); return; }
            if (remainingSeconds % 30 == 0 || remainingSeconds <= 10) broadcast("&7Time left: &f" + remainingSeconds + "s");
        }, 20L, 20L);
        pulseTask = Bukkit.getScheduler().runTaskTimer(this, this::riftPulse, 45L * 20L, 45L * 20L);
    }

    private void riftPulse() {
        if (state != State.ACTIVE || hiders.isEmpty()) return;
        for (UUID id : hiders) {
            Player hider = Bukkit.getPlayer(id);
            if (hider != null) hider.setGlowing(true);
        }
        broadcast("&d&lRIFT PULSE! &7Hiders are briefly revealed—use your parkour routes!");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (UUID id : hiders) {
                Player hider = Bukkit.getPlayer(id);
                if (hider != null) hider.setGlowing(false);
            }
        }, 4L * 20L);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (state != State.ACTIVE || !(event.getEntity() instanceof Player target) || !(event.getDamager() instanceof Player attacker)) return;
        UUID targetId = target.getUniqueId();
        UUID attackerId = attacker.getUniqueId();
        if (seekers.contains(attackerId) && hiders.contains(targetId)) {
            event.setCancelled(true);
            hiders.remove(targetId);
            seekers.add(targetId);
            target.setGlowing(false);
            target.sendTitle(color("&cCAUGHT!"), color("&7You are now a seeker—help find the rest!"), 5, 40, 10);
            attacker.sendMessage(color("&aYou caught " + target.getName() + "!"));
            target.teleport(getLocation("locations.seeker-spawn") == null ? target.getLocation() : getLocation("locations.seeker-spawn"));
            checkWinner();
        } else if (participants.contains(targetId) && participants.contains(attackerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!participants.contains(player.getUniqueId())) return;
        event.getDrops().clear();
        Bukkit.getScheduler().runTask(this, () -> {
            if (state == State.ACTIVE && participants.contains(player.getUniqueId())) {
                player.spigot().respawn();
                player.teleport(getLocation("locations.lobby") == null ? player.getWorld().getSpawnLocation() : getLocation("locations.lobby"));
                hiders.remove(player.getUniqueId());
                seekers.remove(player.getUniqueId());
                player.sendMessage(color("&cYou were eliminated!"));
                checkWinner();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        participants.remove(id);
        hiders.remove(id);
        seekers.remove(id);
        previousModes.remove(id);
        checkWinner();
    }

    private void checkWinner() {
        if (state == State.ACTIVE && hiders.isEmpty()) finishRound("&cSeekers win! All hiders were caught or eliminated.");
    }

    private void finishRound(String message) {
        broadcast(message);
        cancelTasks();
        state = State.WAITING;
        hiders.clear();
        seekers.clear();
        for (UUID id : new HashSet<>(participants)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Location lobby = getLocation("locations.lobby");
                if (lobby != null) player.teleport(lobby);
                restorePlayer(player);
            }
        }
        participants.clear();
    }

    private void restorePlayers() {
        for (UUID id : new HashSet<>(participants)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restorePlayer(player);
        }
        participants.clear(); hiders.clear(); seekers.clear();
    }

    private void restorePlayer(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.setGlowing(false);
        GameMode oldMode = previousModes.remove(player.getUniqueId());
        player.setGameMode(oldMode == null ? GameMode.SURVIVAL : oldMode);
    }

    private void cancelTasks() {
        if (roundTask != null) { roundTask.cancel(); roundTask = null; }
        if (pulseTask != null) { pulseTask.cancel(); pulseTask = null; }
    }

    private void saveLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
        saveConfig();
    }

    private Location getLocation(String path) {
        String worldName = getConfig().getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"), (float) getConfig().getDouble(path + ".yaw"), (float) getConfig().getDouble(path + ".pitch"));
    }

    private void broadcast(String text) {
        String message = color(text);
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(message);
        }
        getLogger().info(ChatColor.stripColor(message));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&6&lParkour Hide & Seek &7- parkour-powered multiplayer hide & seek"));
        sender.sendMessage(color("&e/phs join &7join the next round   &e/phs leave &7leave"));
        sender.sendMessage(color("&e/phs status &7show game status   &e/phs help &7show this help"));
        if (sender.hasPermission("parkourhideseek.admin")) {
            sender.sendMessage(color("&c/phs setlobby|sethider|setseeker &7configure spawns"));
            sender.sendMessage(color("&c/phs start|stop &7manage a round"));
        }
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
