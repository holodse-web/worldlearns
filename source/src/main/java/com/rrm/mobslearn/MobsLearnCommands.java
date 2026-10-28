package com.rrm.mobslearn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.WorldBrain;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class MobsLearnCommands {
    private MobsLearnCommands() {
    }

    private static int settle(CommandSourceStack src, String kind) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(src.getPosition());
        if (!com.rrm.mobslearn.mob.Colonies.found(src.getLevel(), pos, kind, false)) {
            src.sendFailure(Component.translatable("mobslearn.cmd.colony.taken"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("mobslearn.cmd.colony.created." + kind), true);
        if (!com.rrm.mobslearn.mob.Builders.griefing(src.getLevel())) src.sendSuccess(() -> Component.translatable("mobslearn.cmd.colony.griefing"), false);
        return 1;
    }

    private static int aiStatus(CommandSourceStack src) {
        com.rrm.mobslearn.mob.AiChat.ready();
        var ai = new Object() {
            final boolean on = com.rrm.mobslearn.mob.AiChat.enabled;
            final String model = com.rrm.mobslearn.mob.AiChat.model, url = com.rrm.mobslearn.mob.AiChat.url, err = com.rrm.mobslearn.mob.AiChat.lastError;
            final boolean key = !com.rrm.mobslearn.mob.AiChat.key.isBlank();
        };
        src.sendSuccess(() -> Component.translatable("mobslearn.cmd.ai.status", Component.translatable(ai.on ? "options.on" : "options.off"), ai.model, ai.url,
            Component.translatable(ai.key ? "mobslearn.cmd.ai.key_set" : "mobslearn.cmd.ai.key_none")), false);
        if (!ai.err.isEmpty()) src.sendSuccess(() -> Component.translatable("mobslearn.cmd.ai.error", ai.err), false);
        if (!ai.key && !com.rrm.mobslearn.mob.AiChat.local()) src.sendSuccess(() -> Component.translatable("mobslearn.cmd.ai.hint"), false);
        return 1;
    }

    private static int aiToggle(CommandSourceStack src, boolean on) {
        com.rrm.mobslearn.mob.AiChat.ready();
        com.rrm.mobslearn.mob.AiChat.enabled = on;
        com.rrm.mobslearn.mob.AiChat.save();
        return aiStatus(src);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mobslearn")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("ai")
                .executes(ctx -> aiStatus(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> aiToggle(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> aiToggle(ctx.getSource(), false)))
                .then(Commands.literal("preset").then(Commands.argument("name", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"groq", "openrouter", "ollama"}, b))
                    .executes(ctx -> {
                        com.rrm.mobslearn.mob.AiChat.preset(StringArgumentType.getString(ctx, "name"));
                        return aiStatus(ctx.getSource());
                    })))
                .then(Commands.literal("key").then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    com.rrm.mobslearn.mob.AiChat.key = StringArgumentType.getString(ctx, "value").trim();
                    com.rrm.mobslearn.mob.AiChat.save();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.ai.key"), false);
                    return 1;
                })))
                .then(Commands.literal("model").then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    com.rrm.mobslearn.mob.AiChat.model = StringArgumentType.getString(ctx, "value").trim();
                    com.rrm.mobslearn.mob.AiChat.save();
                    return aiStatus(ctx.getSource());
                })))
                .then(Commands.literal("url").then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    com.rrm.mobslearn.mob.AiChat.url = StringArgumentType.getString(ctx, "value").trim();
                    com.rrm.mobslearn.mob.AiChat.save();
                    return aiStatus(ctx.getSource());
                }))))
            .then(Commands.literal("info").executes(ctx -> {
                Knowledge k = WorldBrain.get();
                ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.info", k.intelligence(), k.speed), false);
                for (MobGroup g : MobGroup.values()) {
                    StringBuilder sb = new StringBuilder();
                    for (Skill s : Skill.of(g)) {
                        int l = k.level(s);
                        if (l > 0) sb.append(s.id()).append('=').append(l).append(' ');
                    }
                    if (!sb.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("  " + g.key() + ": " + sb), false);
                }
                return 1;
            }))
            .then(Commands.literal("set")
                .then(Commands.argument("skill", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Skill.ALL.stream().filter(s -> s.group() != MobGroup.WORLD).map(Skill::id), b))
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                        Skill s = Skill.byId(StringArgumentType.getString(ctx, "skill"));
                        if (s == null || s.group() == MobGroup.WORLD) {
                            ctx.getSource().sendFailure(Component.translatable("mobslearn.cmd.unknown"));
                            return 0;
                        }
                        int lvl = IntegerArgumentType.getInteger(ctx, "level");
                        WorldBrain.setLevel(s, lvl);
                        ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.set", s.displayName(), lvl), true);
                        return 1;
                    }))))
            .then(Commands.literal("maxall").executes(ctx -> {
                for (Skill s : Skill.ALL) if (s.group() != MobGroup.WORLD) WorldBrain.get().points.put(s.id(), (double) s.thresholds()[2]);
                WorldBrain.save();
                WorldBrain.syncAll();
                ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.maxall"), true);
                return 1;
            }))
            .then(Commands.literal("reset").executes(ctx -> {
                WorldBrain.reset();
                ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.reset"), true);
                return 1;
            }))
            .then(Commands.literal("settlement")
                .then(Commands.literal("zombie").executes(ctx -> settle(ctx.getSource(), "zombie")))
                .then(Commands.literal("village").executes(ctx -> settle(ctx.getSource(), "village")))
                .then(Commands.literal("skeleton").executes(ctx -> settle(ctx.getSource(), "skeleton")))
                .then(Commands.literal("creeper").executes(ctx -> settle(ctx.getSource(), "creeper")))
                .then(Commands.literal("remove").executes(ctx -> {
                    boolean ok = com.rrm.mobslearn.mob.Colonies.remove(ctx.getSource().getLevel(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
                    if (ok) ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.colony.removed"), true);
                    else ctx.getSource().sendFailure(Component.translatable("mobslearn.cmd.colony.none"));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    var list = com.rrm.mobslearn.mob.Colonies.describe(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.colony.list", list.size()), false);
                    for (String line : list) ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
                    return list.size();
                })))
            .then(Commands.literal("timewarp")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.timewarp.now", com.rrm.mobslearn.mob.Colonies.warp()), false);
                    return com.rrm.mobslearn.mob.Colonies.warp();
                })
                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 100000)).executes(ctx -> {
                    int x = IntegerArgumentType.getInteger(ctx, "multiplier");
                    com.rrm.mobslearn.mob.Colonies.setWarp(x);
                    ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.timewarp.set", x), true);
                    return 1;
                })))
            .then(Commands.literal("speed")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 100000)).executes(ctx -> {
                    double x = DoubleArgumentType.getDouble(ctx, "multiplier");
                    WorldBrain.setSpeed(x);
                    ctx.getSource().sendSuccess(() -> Component.translatable("mobslearn.cmd.speed", x), true);
                    return 1;
                }))));
    }
}
