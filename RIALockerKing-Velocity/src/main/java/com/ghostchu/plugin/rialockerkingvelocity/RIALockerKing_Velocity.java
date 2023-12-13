package com.ghostchu.plugin.rialockerkingvelocity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "rialockerking-velocity",
        name = "RIALockerKing-Velocity",
        version = "1.0-SNAPSHOT",
        dependencies = {@Dependency(id="litebans")}
)
public class RIALockerKing_Velocity {
    private static final MinecraftChannelIdentifier MSG_IDENTIFIER = MinecraftChannelIdentifier.from( "rialockerking:data");
    @Inject
    private Logger logger;
    @Inject
    private ProxyServer server;
    private Cache<UUID, Boolean> QUERY_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(MSG_IDENTIFIER);
        Events.get().register(new Events.Listener(){
            @Override
            public void entryAdded(Entry entry) {
                if(entry.getUuid() == null) return;
                UUID uuid = UUID.fromString(entry.getUuid());
                QUERY_CACHE.invalidate(uuid);
            }

            @Override
            public void entryRemoved(Entry entry) {
                if(entry.getUuid() == null) return;
                UUID uuid = UUID.fromString(entry.getUuid());
                QUERY_CACHE.invalidate(uuid);
            }
        });
    }

    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        ServerConnection backend = (ServerConnection) event.getSource();
        // Ensure the identifier is what you expect before trying to handle the data
        if (event.getIdentifier() != MSG_IDENTIFIER) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        // handle packet data
        UUID requestId = UUID.fromString(in.readUTF());
        UUID owner = UUID.fromString(in.readUTF());
        try {
            boolean banned = QUERY_CACHE.get(owner, ()-> Database.get().isPlayerBanned(owner,null));
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(requestId.toString());
            out.writeBoolean(banned);
            ((ServerConnection) event.getSource()).sendPluginMessage(MSG_IDENTIFIER,out.toByteArray());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
