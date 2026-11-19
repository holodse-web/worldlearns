package com.rrm.mobslearn.net;

import com.rrm.mobslearn.MobsLearn;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class LegacyNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        MobsLearn.id("play"), () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    private static int nextId;

    private LegacyNetwork() {}

    public static boolean canSend(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    public static void send(ServerPlayer player, Net.Payload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Net.Payload payload) {
        CHANNEL.sendToServer(payload);
    }

    static <T extends Net.Payload> void toServer(Class<T> type, Net.PType<T> payload, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(Net.Payload::write).decoder(payload.reader())
            .consumerMainThread((value, context) -> {
                var sender = context.get().getSender();
                if (sender != null) handler.accept(value, sender);
            }).add();
    }

    static <T extends Net.Payload> void toClient(Class<T> type, Net.PType<T> payload, Consumer<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Net.Payload::write).decoder(payload.reader())
            .consumerMainThread((value, context) -> handler.accept(value)).add();
    }
}
