package com.rrm.mobslearn.net;

import com.rrm.mobslearn.MobsLearn;
import net.minecraft.network.FriendlyByteBuf;

public final class Net {
    private Net() {
    }

    public interface Payload {
        void write(FriendlyByteBuf buf);

        PType<?> ptype();
    }

    public record PType<T extends Payload>(net.minecraft.resources.ResourceLocation id, java.util.function.Function<FriendlyByteBuf, T> reader) {
    }

    public record SyncPayload(String json) implements Payload {
        public static final PType<SyncPayload> TYPE = new PType<>(MobsLearn.id("sync"), buf -> new SyncPayload(buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record LearnPayload(String skill, int level) implements Payload {
        public static final PType<LearnPayload> TYPE = new PType<>(MobsLearn.id("learn"), buf -> new LearnPayload(buf.readUtf(1 << 20), buf.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(skill, 1 << 20);
            buf.writeVarInt(level);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record AskPayload(int villager, String topic, String options) implements Payload {
        public static final PType<AskPayload> TYPE = new PType<>(MobsLearn.id("ask"), buf -> new AskPayload(buf.readVarInt(), buf.readUtf(1 << 20), buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(villager);
            buf.writeUtf(topic, 1 << 20);
            buf.writeUtf(options, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record AnswerPayload(int villager, int choice) implements Payload {
        public static final PType<AnswerPayload> TYPE = new PType<>(MobsLearn.id("answer"), buf -> new AnswerPayload(buf.readVarInt(), buf.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(villager);
            buf.writeVarInt(choice);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record ResultPayload(boolean correct, String topic) implements Payload {
        public static final PType<ResultPayload> TYPE = new PType<>(MobsLearn.id("result"), buf -> new ResultPayload(buf.readBoolean(), buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(correct);
            buf.writeUtf(topic, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record TradeOpenPayload(int chief, String kind, String offers) implements Payload {
        public static final PType<TradeOpenPayload> TYPE = new PType<>(MobsLearn.id("trade_open"), buf -> new TradeOpenPayload(buf.readVarInt(), buf.readUtf(1 << 20), buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(chief);
            buf.writeUtf(kind, 1 << 20);
            buf.writeUtf(offers, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record TradePayload(int chief, int offer) implements Payload {
        public static final PType<TradePayload> TYPE = new PType<>(MobsLearn.id("trade"), buf -> new TradePayload(buf.readVarInt(), buf.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(chief);
            buf.writeVarInt(offer);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record ColoniesRequestPayload(int dummy) implements Payload {
        public static final PType<ColoniesRequestPayload> TYPE = new PType<>(MobsLearn.id("colonies_request"), buf -> new ColoniesRequestPayload(buf.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(dummy);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record ForgetPayload(String id) implements Payload {
        public static final PType<ForgetPayload> TYPE = new PType<>(MobsLearn.id("forget"), buf -> new ForgetPayload(buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record ColoniesPayload(String json) implements Payload {
        public static final PType<ColoniesPayload> TYPE = new PType<>(MobsLearn.id("colonies"), buf -> new ColoniesPayload(buf.readUtf(1 << 20)));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 1 << 20);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record NoticePayload(net.minecraft.network.chat.Component text) implements Payload {
        public static final PType<NoticePayload> TYPE = new PType<>(MobsLearn.id("notice"), buf -> new NoticePayload(buf.readComponent()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(text);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public record SettingsPayload(double speed, boolean absurd, boolean combat, int difficulty, boolean enabled, int warp) implements Payload {
        public static final PType<SettingsPayload> TYPE = new PType<>(MobsLearn.id("settings"), buf -> new SettingsPayload(buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(speed);
            buf.writeBoolean(absurd);
            buf.writeBoolean(combat);
            buf.writeVarInt(difficulty);
            buf.writeBoolean(enabled);
            buf.writeVarInt(warp);
        }

        @Override
        public PType<?> ptype() {
            return TYPE;
        }
    }

    public static void notice(net.minecraft.server.level.ServerPlayer player, net.minecraft.network.chat.Component text) {
        if (SNet.canSend(player, NoticePayload.TYPE)) SNet.send(player, new NoticePayload(text));
        else player.displayClientMessage(text, true);
    }

    public static void register() {
        SNet.registerGlobalReceiver(ForgetPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.forget(context.player(), payload.id())));
        SNet.registerGlobalReceiver(AnswerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.VillageDefense.answer(context.player(), payload.villager(), payload.choice())));
        SNet.registerGlobalReceiver(TradePayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.trade(context.player(), payload.chief(), payload.offer())));
        SNet.registerGlobalReceiver(ColoniesRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.sendList(context.player())));
        SNet.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.learn.WorldBrain.configure(context.player(), payload)));
    }
}
