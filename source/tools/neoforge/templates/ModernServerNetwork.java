package com.rrm.mobslearn.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ServerNetwork {
    private ServerNetwork() {}

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null) return false;
        try {
            return NetworkRegistry.hasChannel(player.connection, type.id());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
