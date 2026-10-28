package com.rrm.mobslearn.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Anim {
    private Anim() {
    }

    public static float ease(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    public static int fade(int argb, float alpha) {
        int base = (argb >>> 24) == 0 ? 0xFF : (argb >>> 24);
        int al = Math.round(base * Mth.clamp(alpha, 0f, 1f));
        if (al < 8) al = 8;
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(a1 + (a2 - a1) * t) << 24) | (Math.round(r1 + (r2 - r1) * t) << 16)
            | (Math.round(g1 + (g2 - g1) * t) << 8) | Math.round(b1 + (b2 - b1) * t);
    }

    public static float approach(float value, float target, float dt, float speed) {
        return value + (target - value) * (1f - (float) Math.exp(-dt * speed));
    }

    public static ItemStack stack(String id, int count) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }
}
