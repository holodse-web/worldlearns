package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.WorldBrain;
import net.minecraft.world.entity.Entity;

public final class TraitTags {
    public static final String PREFIX = "mobslearn.";
    public static final String SEEN = "mobslearn.seen";

    private TraitTags() {
    }

    public static int get(Entity e, String skill) {
        Skill s = Skill.byId(skill);
        if (s != null && !WorldBrain.get().allows(s)) return 0;
        return raw(e, skill);
    }

    public static int raw(Entity e, String skill) {
        String p = PREFIX + skill + "=";
        for (String t : e.entityTags()) {
            if (t.startsWith(p)) {
                try {
                    return Integer.parseInt(t.substring(p.length()));
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 0;
    }

    public static void set(Entity e, String skill, int level) {
        String p = PREFIX + skill + "=";
        for (String t : new java.util.ArrayList<>(e.entityTags())) if (t.startsWith(p)) e.removeTag(t);
        e.addTag(p + level);
    }
}
