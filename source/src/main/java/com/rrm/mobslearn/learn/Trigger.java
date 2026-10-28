package com.rrm.mobslearn.learn;

import net.minecraft.network.chat.Component;

public enum Trigger {
    KILL_ANY,
    KILL_MELEE,
    KILL_BLADE,
    KILL_PROJECTILE,
    KILL_FIRE,
    KILL_FALL,
    KILL_DAY,
    KILL_IN_WATER,
    KILL_CALM,
    SUN_BURN,
    UNREACHABLE,
    ESCAPE,
    SHIELD_BLOCK,
    KILLED_PLAYER,
    EXPLODE,
    CROUCH_NEAR,
    WATER_HURT,
    MILK,
    SHEAR,
    FEED,
    TRADE,
    HIT_VILLAGER,
    HONEY,
    PLAYER_CRIT,
    PLAYER_HEAL,
    SEE_DIG,
    SEE_BUILD,
    DODGE,
    SEE_PILLAR,
    SEE_BRIDGE,
    TAUGHT_SWORD,
    TAUGHT_SHIELD,
    TAUGHT_TOOLS,
    SEE_ARMOR,
    PLAYER_JUMP,
    CHAT_NEAR,
    WORLD;

    public Component describe() {
        return Component.translatable("mobslearn.trigger." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
