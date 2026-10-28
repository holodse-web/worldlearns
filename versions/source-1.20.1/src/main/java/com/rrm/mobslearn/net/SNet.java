package com.rrm.mobslearn.net;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SNet {
    private SNet() {
    }

    public record Context(MinecraftServer server, ServerPlayer player) {
    }

    public static boolean canSend(ServerPlayer player, Net.PType<?> type) {
        return ServerPlayNetworking.canSend(player, type.id());
    }

    public static void send(ServerPlayer player, Net.Payload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, payload.ptype().id(), buf);
    }

    public static <T extends Net.Payload> void registerGlobalReceiver(Net.PType<T> type, BiConsumer<T, Context> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, net, buf, sender) -> {
            T value = type.reader().apply(buf);
            handler.accept(value, new Context(server, player));
        });
    }
}
