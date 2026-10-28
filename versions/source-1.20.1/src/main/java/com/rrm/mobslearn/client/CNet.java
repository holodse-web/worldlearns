package com.rrm.mobslearn.client;

import com.rrm.mobslearn.net.Net;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public final class CNet {
    private CNet() {
    }

    public record Context(Minecraft client) {
    }

    public static boolean canSend(Net.PType<?> type) {
        return ClientPlayNetworking.canSend(type.id());
    }

    public static void send(Net.Payload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(payload.ptype().id(), buf);
    }

    public static <T extends Net.Payload> void registerGlobalReceiver(Net.PType<T> type, BiConsumer<T, Context> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, net, buf, sender) -> {
            T value = type.reader().apply(buf);
            client.execute(() -> handler.accept(value, new Context(client)));
        });
    }
}
