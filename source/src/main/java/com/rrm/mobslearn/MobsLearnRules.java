package com.rrm.mobslearn;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class MobsLearnRules {
    public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MOBS).buildAndRegister(MobsLearn.id("enabled"));
    private MobsLearnRules() {}
    public static void register() {}
}
