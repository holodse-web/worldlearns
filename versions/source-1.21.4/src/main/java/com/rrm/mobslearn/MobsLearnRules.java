package com.rrm.mobslearn;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

public final class MobsLearnRules {
    public static final GameRules.Key<GameRules.BooleanValue> ENABLED =
        GameRuleRegistry.register("mobslearn:enabled", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));

    private MobsLearnRules() {
    }

    public static void register() {
    }

    public static boolean get(GameRules rules) {
        return rules.getBoolean(ENABLED);
    }

    public static void set(GameRules rules, boolean value, MinecraftServer server) {
        rules.getRule(ENABLED).set(value, server);
    }
}
