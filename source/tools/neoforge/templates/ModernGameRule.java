package com.rrm.mobslearn;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class MobsLearnRules {
    public static final GameRule<Boolean> ENABLED = new GameRule<>(GameRuleCategory.MOBS,
        GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
        Codec.BOOL, value -> value ? 1 : 0, true, FeatureFlagSet.of());

    private MobsLearnRules() {}

    static void register(RegisterEvent event) {
        event.register(Registries.GAME_RULE, registry -> registry.register(MobsLearn.id("enabled"), ENABLED));
    }
}
