package com.rrm.mobslearn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import @DISTRIBUTOR_IMPORT@;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ClientNetwork {
    private ClientNetwork() {}

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, type.id());
    }

    public static void send(CustomPacketPayload payload) {
        @DISTRIBUTOR@.sendToServer(payload);
    }
}
