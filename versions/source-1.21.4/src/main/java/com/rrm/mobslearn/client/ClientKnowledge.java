package com.rrm.mobslearn.client;

import com.rrm.mobslearn.learn.Knowledge;

public final class ClientKnowledge {
    private static final Knowledge knowledge = new Knowledge();
    private static boolean synced;
    private static boolean canConfigure;
    private static long revision;

    private ClientKnowledge() {
    }

    public static Knowledge get() {
        return knowledge;
    }

    public static boolean synced() {
        return synced;
    }

    public static boolean canConfigure() {
        return synced && canConfigure;
    }

    public static long revision() {
        return revision;
    }

    public static void update(String json) {
        try {
            knowledge.readJson(json);
            com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            canConfigure = data.has("canConfigure") && data.get("canConfigure").getAsBoolean();
            synced = true;
            revision++;
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        knowledge.points.clear();
        knowledge.journal.clear();
        knowledge.speed = 1.0;
        knowledge.absurd = knowledge.combat = knowledge.enabled = true;
        knowledge.difficulty = 1;
        canConfigure = false;
        synced = false;
    }
}
