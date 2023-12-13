package com.ghostchu.plugin.rialockerkingbukkit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import nl.rutgerkok.blocklocker.BlockLockerPlugin;
import nl.rutgerkok.blocklocker.ProtectionSign;
import nl.rutgerkok.blocklocker.SearchMode;
import nl.rutgerkok.blocklocker.profile.PlayerProfile;
import nl.rutgerkok.blocklocker.profile.Profile;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RIALockerKing_Bukkit extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String DATA_CHANNEL = "rialockerking:data";
    private BlockLockerPlugin blockLocker;
    private Cache<UUID, Boolean> QUERY_CACHE;

    private Cache<UUID, Consumer<Boolean>> NETWORK_CALL_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        QUERY_CACHE = CacheBuilder.newBuilder()
                .expireAfterWrite(getConfig().getInt("cache-interval"), TimeUnit.SECONDS)
                .build();
        this.blockLocker = (BlockLockerPlugin) Bukkit.getPluginManager().getPlugin("BlockLocker");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, DATA_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, DATA_CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(DATA_CHANNEL)) return;
        DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(message));
        try {
            UUID requestId = UUID.fromString(msgin.readUTF());
            boolean result = msgin.readBoolean();
            getLogger().info("[DEBUG] Received request with reqId: " + requestId+", result: "+ result);
            Consumer<Boolean> callback = NETWORK_CALL_CACHE.getIfPresent(requestId);
            if (callback != null) {
                callback.accept(result);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProtectEvent(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Optional<Protection> protectionOptional = blockLocker.getProtectionFinder().findProtection(block, SearchMode.ALL);
        if (protectionOptional.isEmpty()) {
            getLogger().info("[DEBUG] ProtectionOption is null");
            return;
        }
        Protection protection = protectionOptional.get();
        Optional<Profile> profileOptional = protection.getOwner();
        if (profileOptional.isEmpty()) {
            return;
        }
        Profile profile = profileOptional.get();
        if (!(profile instanceof PlayerProfile)){
            return;
        }
        PlayerProfile playerProfile = (PlayerProfile) profile;
        Optional<UUID> uuidOptional = playerProfile.getUniqueId();
        if (uuidOptional.isEmpty()){
            return;
        }
        UUID owner = uuidOptional.get();
        postCheck(owner, event.getPlayer(), protection.getSigns());
    }

    private void postCheck(UUID owner, Player clicker, Collection<ProtectionSign> signs) {
        Boolean result = QUERY_CACHE.getIfPresent(owner);
        if (result == null) {
            startQuery(owner, clicker, signs);
        } else {
            handleResult(owner, clicker, signs, result);
        }
    }

    private void handleResult(UUID owner, Player clicker, Collection<ProtectionSign> signs, boolean result) {
        try {
            QUERY_CACHE.get(owner, () -> result);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (result) {
            Bukkit.getScheduler().runTask(this, () -> {
                signs.forEach(s -> s.getLocation().getBlock().breakNaturally());
                clicker.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("sign-break")));
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.2f, 0.0f);
            });
        }
    }

    private void startQuery(UUID owner, Player clicker, Collection<ProtectionSign> signs) {
        UUID requestId = UUID.randomUUID();
        NETWORK_CALL_CACHE.put(requestId, (result) -> handleResult(owner, clicker, signs, result));
        Iterator<? extends Player> players = Bukkit.getOnlinePlayers().iterator();
        if (players.hasNext()) {
            Player p = players.next();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(requestId.toString());
            out.writeUTF(owner.toString());
            p.sendPluginMessage(this, DATA_CHANNEL, out.toByteArray());
        }
    }
}
