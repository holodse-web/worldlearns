package com.rrm.mobslearn.net;

import com.rrm.mobslearn.MobsLearn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class Net {
    private Net() {
    }

    public record SyncPayload(String json) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(MobsLearn.id("sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SyncPayload::json, SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LearnPayload(String skill, int level) implements CustomPacketPayload {
        public static final Type<LearnPayload> TYPE = new Type<>(MobsLearn.id("learn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LearnPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LearnPayload::skill, ByteBufCodecs.VAR_INT, LearnPayload::level, LearnPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AskPayload(int villager, String topic, String options) implements CustomPacketPayload {
        public static final Type<AskPayload> TYPE = new Type<>(MobsLearn.id("ask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AskPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AskPayload::villager, ByteBufCodecs.STRING_UTF8, AskPayload::topic, ByteBufCodecs.STRING_UTF8, AskPayload::options, AskPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AnswerPayload(int villager, int choice) implements CustomPacketPayload {
        public static final Type<AnswerPayload> TYPE = new Type<>(MobsLearn.id("answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnswerPayload::villager, ByteBufCodecs.VAR_INT, AnswerPayload::choice, AnswerPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResultPayload(boolean correct, String topic) implements CustomPacketPayload {
        public static final Type<ResultPayload> TYPE = new Type<>(MobsLearn.id("result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ResultPayload::correct, ByteBufCodecs.STRING_UTF8, ResultPayload::topic, ResultPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TradeOpenPayload(int chief, String kind, String offers) implements CustomPacketPayload {
        public static final Type<TradeOpenPayload> TYPE = new Type<>(MobsLearn.id("trade_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradeOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeOpenPayload::chief, ByteBufCodecs.STRING_UTF8, TradeOpenPayload::kind,
            ByteBufCodecs.STRING_UTF8, TradeOpenPayload::offers, TradeOpenPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TradePayload(int chief, int offer) implements CustomPacketPayload {
        public static final Type<TradePayload> TYPE = new Type<>(MobsLearn.id("trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradePayload::chief, ByteBufCodecs.VAR_INT, TradePayload::offer, TradePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ColoniesRequestPayload(int dummy) implements CustomPacketPayload {
        public static final Type<ColoniesRequestPayload> TYPE = new Type<>(MobsLearn.id("colonies_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ColoniesRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ColoniesRequestPayload::dummy, ColoniesRequestPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ForgetPayload(String id) implements CustomPacketPayload {
        public static final Type<ForgetPayload> TYPE = new Type<>(MobsLearn.id("forget"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ForgetPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ForgetPayload::id, ForgetPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ColoniesPayload(String json) implements CustomPacketPayload {
        public static final Type<ColoniesPayload> TYPE = new Type<>(MobsLearn.id("colonies"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ColoniesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ColoniesPayload::json, ColoniesPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NoticePayload(net.minecraft.network.chat.Component text) implements CustomPacketPayload {
        public static final Type<NoticePayload> TYPE = new Type<>(MobsLearn.id("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> CODEC = StreamCodec.composite(
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC, NoticePayload::text, NoticePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void notice(net.minecraft.server.level.ServerPlayer player, net.minecraft.network.chat.Component text) {
        if (ServerPlayNetworking.canSend(player, NoticePayload.TYPE)) ServerPlayNetworking.send(player, new NoticePayload(text));
        else player.displayClientMessage(text, true);
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(NoticePayload.TYPE, NoticePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AskPayload.TYPE, AskPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResultPayload.TYPE, ResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TradeOpenPayload.TYPE, TradeOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ColoniesPayload.TYPE, ColoniesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnswerPayload.TYPE, AnswerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TradePayload.TYPE, TradePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ColoniesRequestPayload.TYPE, ColoniesRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ForgetPayload.TYPE, ForgetPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ForgetPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.forget(context.player(), payload.id())));
        ServerPlayNetworking.registerGlobalReceiver(AnswerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.VillageDefense.answer(context.player(), payload.villager(), payload.choice())));
        ServerPlayNetworking.registerGlobalReceiver(TradePayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.trade(context.player(), payload.chief(), payload.offer())));
        ServerPlayNetworking.registerGlobalReceiver(ColoniesRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.mob.Colonies.sendList(context.player())));
        PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LearnPayload.TYPE, LearnPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> com.rrm.mobslearn.learn.WorldBrain.configure(context.player(), payload)));
    }

    public record SettingsPayload(double speed, boolean absurd, boolean combat, int difficulty, boolean enabled, int warp) implements CustomPacketPayload {
        public static final Type<SettingsPayload> TYPE = new Type<>(MobsLearn.id("settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SettingsPayload::speed, ByteBufCodecs.BOOL, SettingsPayload::absurd,
            ByteBufCodecs.BOOL, SettingsPayload::combat, ByteBufCodecs.VAR_INT, SettingsPayload::difficulty,
            ByteBufCodecs.BOOL, SettingsPayload::enabled, ByteBufCodecs.VAR_INT, SettingsPayload::warp, SettingsPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
