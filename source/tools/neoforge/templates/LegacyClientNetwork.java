package com.rrm.mobslearn.client;

import com.rrm.mobslearn.net.LegacyNetwork;
import com.rrm.mobslearn.net.Net;
import net.minecraft.client.Minecraft;

public final class CNet {
    private CNet() {}

    public static boolean canSend(Net.PType<?> type) {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && LegacyNetwork.canSend(listener.getConnection());
    }

    public static void send(Net.Payload payload) {
        LegacyNetwork.sendToServer(payload);
    }
}
