package com.rrm.mobslearn.net;

import net.minecraft.server.level.ServerPlayer;

public final class SNet {
    private SNet() {}

    public static boolean canSend(ServerPlayer player, Net.PType<?> type) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer || player.connection == null || player.connection.connection == null) return false;
        try {
            return LegacyNetwork.canSend(player.connection.connection);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void send(ServerPlayer player, Net.Payload payload) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        LegacyNetwork.send(player, payload);
    }
}
