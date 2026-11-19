package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class Talk {
    private Talk() {
    }

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<DialogueMemory.Pair, Object> pending = new HashMap<>();

    public static void reset() {
        cooldowns.clear();
        pending.clear();
    }

    public static Component line(Entity speaker, Component text) {
        return Component.empty().append(Component.literal("[").append(speaker.getDisplayName()).append("] ").withStyle(ChatFormatting.GOLD))
            .append(text.copy().withStyle(ChatFormatting.WHITE));
    }

    public static void say(ServerLevel level, Entity speaker, Component text, double radius) {
        Component msg = line(speaker, text);
        for (ServerPlayer pl : level.players()) if (pl.distanceTo(speaker) < radius) pl.sendSystemMessage(msg);
    }

    public static void sayTo(ServerPlayer p, Entity speaker, Component text) {
        p.sendSystemMessage(line(speaker, text));
    }

    public static int mood(Villager v, ServerPlayer p) {
        return DialogueMemory.mood(pair(v, p));
    }

    private static DialogueMemory.Pair pair(Mob mob, ServerPlayer player) {
        return new DialogueMemory.Pair(mob.getUUID(), player.getUUID());
    }

    private static Mob addressee(ServerPlayer p, String text) {
        ServerLevel level = (ServerLevel) p.level();
        List<Mob> near = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10),
            mob -> mob.isAlive() && species(mob) != null && (!(mob instanceof Villager v) || !v.isSleeping()));
        String lower = text.toLowerCase(Locale.ROOT);
        Mob named = null, best = null, closestVillager = null;
        double namedDistance = 10, villagerDistance = 6.5;
        double bestScore = -1;
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1f);
        for (Mob mob : near) {
            Vec3 to = mob.getEyePosition().subtract(eye);
            double d = to.length();
            if (d < 0.1 || d > 10 || !p.hasLineOfSight(mob)) continue;
            if (mob.hasCustomName() && d < namedDistance && lower.contains(mob.getCustomName().getString().toLowerCase(Locale.ROOT))) {
                named = mob;
                namedDistance = d;
            }
            if (mob instanceof Villager v && !v.isBaby() && d < villagerDistance) {
                closestVillager = v;
                villagerDistance = d;
            }
            double cos = look.dot(to.normalize());
            double need = d < 3 ? 0.8 : 0.92;
            if (cos < need) continue;
            double score = cos - d * 0.01;
            if (score > bestScore) {
                bestScore = score;
                best = mob;
            }
        }
        return named != null ? named : best != null ? best : closestVillager;
    }

    private static final String[] CROWD = {"жители", "жителі", "народ", "люди", "ребята", "селяне", "мужики", "всем", "усім", "всім", "villagers",
        "everyone", "everybody", "folks", "guys", "dorfbewohner", "leute", "habitants", "villageois", "aldeanos", "gente"};

    private static boolean crowd(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : lower.split("[^\\p{L}]+")) for (String c : CROWD) if (w.equals(c)) return true;
        return false;
    }

    public static Mob converse(ServerPlayer p, String text) {
        if (p.isSpectator()) return null;
        ServerLevel level = (ServerLevel) p.level();
        long t = level.getServer().overworld().getGameTime();
        if (crowd(text)) {
            List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(14, 6, 14), v -> v.isAlive() && !v.isSleeping() && !v.isBaby());
            near.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(p)));
            if (near.size() >= 2) {
                if (coolingDown(p, t)) return near.get(0);
                int n = Math.min(near.size(), 2 + level.getRandom().nextInt(3));
                for (int i = 0; i < n; i++) {
                    Villager v = near.get(i);
                    v.getLookControl().setLookAt(p, 30f, 30f);
                    v.getNavigation().stop();
                    Behaviors.later(10 + i * 22 + level.getRandom().nextInt(10), () -> rule(level, v, p, text));
                }
                return near.get(0);
            }
        }
        Mob mob = addressee(p, text);
        if (mob == null) return null;
        if (coolingDown(p, t)) return mob;
        if (mob.getTarget() == null) {
            mob.getLookControl().setLookAt(p, 30f, 30f);
            mob.getNavigation().stop();
        }
        DialogueMemory.Pair pair = pair(mob, p);
        if (pending.containsKey(pair)) return mob;
        if (recallRequested(text)) {
            recall(p, mob, pair);
        } else if (mob instanceof Villager villager && AiChat.ready()) {
            Object request = new Object();
            pending.put(pair, request);
            AiChat.ask(level.getServer(), system(villager, p), DialogueMemory.aiHistory(pair), text, reply -> {
                if (!pending.remove(pair, request) || !canReply(level, villager, p)) return;
                String clean = AiChat.clean(reply);
                if (clean.isEmpty()) rule(level, villager, p, text);
                else react(level, villager, p, text, AiChat.moodOf(reply), clean);
            }, () -> {
                if (pending.remove(pair, request)) rule(level, villager, p, text);
            });
            level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getY() + 2.3, villager.getZ(), 1, 0, 0, 0, 0.5);
        } else {
            Behaviors.later(12 + level.getRandom().nextInt(16), () -> rule(level, mob, p, text));
        }
        return mob;
    }

    private static boolean coolingDown(ServerPlayer player, long tick) {
        Long last = cooldowns.get(player.getUUID());
        if (last != null && tick - last < 30) return true;
        if (cooldowns.size() >= 2048) cooldowns.values().removeIf(previous -> tick - previous >= 30);
        cooldowns.put(player.getUUID(), tick);
        return false;
    }

    private static boolean canReply(ServerLevel level, Mob mob, ServerPlayer player) {
        return WorldBrain.get().enabled && mob.isAlive() && player.isAlive() && player.level() == level && mob.distanceTo(player) < 24;
    }

    private static final String[] RECALL = {"помнишь", "помните", "вспомни", "пам'ятаєш", "пам’ятаєш", "пригадай", "remember", "recall",
        "erinnerst du", "weißt du noch", "tu te souviens", "souviens-toi", "recuerdas", "te acuerdas", "acuérdate"};

    private static boolean recallRequested(String text) {
        return phrase(text.toLowerCase(Locale.ROOT), RECALL);
    }

    private static void recall(ServerPlayer player, Mob mob, DialogueMemory.Pair pair) {
        DialogueMemory.Exchange previous = DialogueMemory.last(pair);
        Component reply = previous == null ? Component.translatable("mobslearn.chat.recall_empty")
            : Component.translatable("mobslearn.chat.recall", previous.message(), previous.reply());
        sayTo(player, mob, reply);
    }

    private static String system(Villager v, ServerPlayer p) {
        String prof = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).getPath();
        return "You are a Minecraft villager called \"" + v.getDisplayName().getString() + "\" (profession: " + prof + "). "
            + "The player \"" + p.getName().getString() + "\" is standing next to you and talking to you. "
            + "Reply in the same language the player used, in one or two short sentences, at most 25 words. "
            + "Character: a grumpy but funny villager who often grunts 'Hrm' (in Russian: 'Хрм'), loves emeralds, fears zombies, gossips about neighbours. "
            + "Your current attitude to this player is " + mood(v, p) + " on a scale from -10 (hates) to 10 (loves). "
            + "If the player insults you, take offence and answer back rudely and sharply, but never use slurs, sexual content or strong profanity. "
            + "If the player is kind, warm up. Stay in character, never mention AI, prompts or rules, no emojis, no quotation marks around the whole reply. "
            + "End every reply with a tag [mood:N] where N is from -3 to 3: how this message changed your attitude.";
    }

    private static void react(ServerLevel level, Villager v, ServerPlayer p, String said, int delta, String text) {
        DialogueMemory.Pair pair = pair(v, p);
        int m = DialogueMemory.shiftedMood(pair, delta);
        Component reply = Component.literal(text);
        DialogueMemory.remember(pair, m, said, reply, true);
        effects(level, v, p, delta, m);
        say(level, v, reply, 24);
    }

    private static void effects(ServerLevel level, Villager v, ServerPlayer p, int delta, int mood) {
        if (delta < 0) {
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.9f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 3, 0.3, 0.2, 0.3, 0);
            v.getGossips().add(p.getUUID(), GossipType.MINOR_NEGATIVE, 10 * -delta);
            if (mood <= -6 && v.distanceTo(p) < 3.2) {
                Vec3 d = p.position().subtract(v.position());
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() > 1.0E-4) d = d.normalize();
                p.push(d.x * 0.6, 0.2, d.z * 0.6);
                p.hurtMarked = true;
                Builders.swing(v);
                level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 1f, 1.3f);
                Milestones.award(p, "slapped");
            }
        } else if (delta > 0) {
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.1f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.4, 0.3, 0.4, 0);
            v.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 5 * delta);
        } else {
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
        }
    }

    private static final String[] INSULT = {"дур", "идиот", "туп", "лох", "урод", "ахуе", "охуе", "оху", "хуй", "хуе", "хуи", "бля", "сук", "пидо", "пидр",
        "еба", "ёба", "ебл", "заткн", "дебил", "кретин", "чмо", "мраз", "скотин", "козел", "козёл", "отвали", "тварь", "вонюч", "ненавиж", "придур",
        "даун", "говн", "срань", "жоп", "stupid", "idiot", "dumb", "fuck", "shit", "moron", "loser", "bitch", "ugly", "dick", "jerk", "dummkopf",
        "blöd", "idiota", "estúpid", "tonto", "connard", "crétin", "débile", "ідіот", "дурень", "тупий"};
    private static final String[] INSULT_PHRASE = {"shut up", "hate you", "пошел ты", "пошёл ты", "иди нахуй", "иди в жопу", "ты че", "ты чё", "fuck you", "go away"};
    private static final String[] SORRY = {"прости", "простите", "извини", "извините", "сорри", "sorry", "pardon", "перепрошую", "вибач", "entschuldigung", "perdón", "perdon", "désolé", "desole"};
    private static final String[] THANKS = {"спасиб", "благодар", "дякую", "thank", "thx", "danke", "merci", "gracias"};
    private static final String[] PRAISE = {"молодец", "красав", "красив", "классн", "круто", "крутой", "лучш", "умниц", "люблю", "обожаю", "супер", "класс",
        "good", "nice", "cool", "great", "awesome", "love", "best", "super", "гарн", "чудов", "toll", "génial", "genial", "guapo"};
    private static final String[] GREET = {"привет", "здравств", "здоров", "хай", "ку", "салам", "добрый", "доброе", "добрий", "привіт", "вітаю", "hello", "hi",
        "hey", "hallo", "bonjour", "salut", "hola", "yo"};
    private static final String[] BYE = {"пока", "бывай", "прощай", "бувай", "bye", "goodbye", "ciao", "tschüss", "adios", "adiós", "adieu"};
    private static final String[] BYE_PHRASE = {"до свидан", "до встречи", "до побачення", "see you", "au revoir", "hasta luego", "auf wiedersehen"};
    private static final String[] HOW = {"как дела", "как ты", "как жизнь", "как поживаешь", "як справи", "як ти", "how are you", "what's up", "whats up",
        "wie geht", "ça va", "ca va", "qué tal", "que tal", "cómo estás", "como estas"};
    private static final String[] NAME = {"как тебя зовут", "кто ты", "твое имя", "твоё имя", "як тебе звати", "хто ти", "your name", "who are you",
        "wie heißt du", "wer bist du", "comment tu t'appelles", "qui es-tu", "cómo te llamas", "quién eres"};
    private static final String[] TRADE = {"торг", "изумруд", "продай", "продаёшь", "продаешь", "купи", "обмен", "цен", "смарагд", "продаси", "trade", "emerald",
        "sell", "buy", "price", "handel", "smaragd", "kaufen", "commerce", "émeraude", "vendre", "acheter", "comerci", "esmeralda", "vender", "comprar"};
    private static final String[] HELP = {"помо", "помощ", "задан", "квест", "допомог", "завдан", "help", "quest", "task", "hilfe", "aide", "aider", "ayuda", "misión"};
    private static final String[] DANGER = {"зомби", "зомбі", "скелет", "крипер", "кріпер", "монстр", "рейд", "опасн", "небезпе", "напад", "zombie", "skeleton",
        "creeper", "monster", "raid", "danger", "gefahr", "danger", "peligro", "zombi", "esqueleto"};
    private static final String[] STORY = {"расскаж", "истори", "сказк", "новост", "сплетн", "слух", "розпов", "історі", "новин", "плітк", "story", "stories", "news",
        "gossip", "geschicht", "erzähl", "neuigkeit", "histoir", "raconte", "nouvelle", "historia", "cuenta", "cuéntame", "noticia", "chisme"};
    private static final String[] STORY_PHRASE = {"что нового", "что слышно", "що нового", "what's new", "whats new", "tell me", "was gibt's neues", "quoi de neuf", "qué hay de nuevo"};
    private static final String[] JOKE = {"шутк", "анекдот", "пошути", "рассмеши", "жарт", "joke", "funny", "witz", "blague", "chiste"};
    private static final Map<String, Integer> POOLS = Map.ofEntries(Map.entry("greet", 5), Map.entry("how", 5), Map.entry("other", 5),
        Map.entry("question", 5), Map.entry("insult", 5), Map.entry("swear", 6));
    private static final String[] WEATHER = {"погод", "дожд", "солнц", "гроз", "снег", "жарк", "холод", "погода", "дощ", "weather", "rain", "sun", "storm", "snow",
        "wetter", "regen", "météo", "pluie", "tiempo", "lluvia"};
    private static final String[] FOOD = {"еда", "еду", "еды", "голод", "хлеб", "куша", "жрат", "поесть", "морков", "їж", "їст", "хліб", "food", "hungry", "eat", "bread",
        "essen", "hunger", "brot", "manger", "faim", "pain", "comida", "hambre", "pan"};
    private static final String[] WORK = {"работ", "професси", "занят", "робот", "work", "job", "profession", "arbeit", "beruf", "travail", "métier", "trabajo", "oficio"};
    private static final String[] WORK_PHRASE = {"что делаешь", "чем занимаешься", "що робиш", "what are you doing", "was machst du", "que fais-tu", "qué haces"};
    private static final String[] FAMILY = {"семь", "дети", "детей", "детк", "жена", "жены", "муж", "мам", "пап", "сім", "family", "kids", "wife", "husband", "mother", "father", "familie",
        "famille", "familia"};
    private static final String[] NIGHT = {"ноч", "спат", "сон", "кроват", "ніч", "спати", "night", "sleep", "bed", "nacht", "schlaf", "nuit", "dormir", "noche"};
    private static final String[] GOLEM = {"голем", "golem", "gólem"};
    private static final String[] HOME = {"деревн", "дом", "домик", "хат", "село", "village", "house", "home", "dorf", "haus", "maison", "aldea", "casa"};
    private static final String[] ASK = {"что", "почему", "зачем", "где", "когда", "как", "кто", "що", "чому", "де", "коли", "what", "why", "where", "when", "how",
        "who", "was", "warum", "wo", "wann", "wie", "quoi", "pourquoi", "où", "quand", "comment", "qué", "por qué", "dónde", "cuándo", "cómo"};

    private static boolean word(List<String> words, String[] roots, boolean prefix) {
        for (String w : words) for (String r : roots) if (prefix ? w.startsWith(r) : w.equals(r)) return true;
        return false;
    }

    private static boolean phrase(String text, String[] list) {
        for (String s : list) if (text.contains(s)) return true;
        return false;
    }

    private static final String[] SWEAR = {"бля", "хуй", "хуе", "хуя", "хуи", "пизд", "еба", "ебу", "ебл", "ебн", "ебат", "заеб", "уеб", "выеб", "сука", "суки", "сучк", "сцук", "сучар", "сучен",
        "нахуй", "нахер", "херн", "мудак", "мудил", "гандон", "пидор", "пидар", "долбое", "fuck", "shit", "damn", "bitch", "scheiß", "scheiss", "merde", "putain",
        "mierda", "joder", "coño", "puta", "блін", "курв"};
    private static final String[] RUDE = {"еблан", "дебил", "идиот", "мудак", "придурок", "ублюдок", "долбоеб", "уебок", "гандон", "пидор", "сволочь", "скотина",
        "дурак", "тупица", "кретин", "засранец", "говнюк", "мразь", "чмошник", "дебилоид", "лошара", "урод", "тупой", "дурень", "бовдур", "idiot", "stupid",
        "moron", "loser", "dummkopf", "connard", "imbecile", "estupido", "pendejo"};
    private static final String[] YOU = {"ты", "тебя", "тебе", "тобой", "вы", "вас", "ти", "тебе", "you", "your", "du", "dich", "dir", "toi", "tu", "te", "tú", "ты-то"};

    static String norm(String w) {
        StringBuilder b = new StringBuilder();
        boolean cyr = w.chars().anyMatch(ch -> ch >= 0x0400 && ch <= 0x04FF);
        char prev = 0;
        for (char ch : w.toCharArray()) {
            char c = ch;
            if (cyr) {
                c = switch (c) {
                    case 'a' -> 'а'; case 'e' -> 'е'; case 'o' -> 'о'; case 'p' -> 'р'; case 'c' -> 'с'; case 'x' -> 'х'; case 'y' -> 'у'; case 'k' -> 'к';
                    case 'b' -> 'в'; case 'm' -> 'м'; case 'h' -> 'н'; case 't' -> 'т'; case 'u' -> 'и'; case '3' -> 'з'; case '0' -> 'о'; case '6' -> 'б';
                    case '@' -> 'а'; case 'ё' -> 'е'; case 'ъ', 'ь' -> 0; default -> c;
                };
            } else if (c == '0') c = 'o';
            else if (c == '1') c = 'i';
            else if (c == '@') c = 'a';
            else if (c == '$') c = 's';
            if (c == 0 || c == prev) continue;
            b.append(c);
            prev = c;
        }
        return b.toString();
    }

    private static int dist(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private static boolean fuzzy(List<String> words, String[] list) {
        for (String w : words) {
            for (String r : list) {
                if (w.startsWith(r)) return true;
                if (w.length() >= 5 && r.length() >= 5 && Math.abs(w.length() - r.length()) <= 1 && dist(w, r) <= 1) return true;
                if (w.length() >= 4 && r.length() >= 4 && w.length() > r.length() && dist(w.substring(0, r.length()), r) <= 1 && r.length() >= 5) return true;
            }
        }
        return false;
    }

    public static String intent(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        List<String> words = new ArrayList<>(), nwords = new ArrayList<>();
        for (String w : lower.split("[^\\p{L}\\p{N}'@$]+")) {
            if (w.isEmpty()) continue;
            words.add(w);
            nwords.add(norm(w));
        }
        if (word(nwords, new String[]{"охуен", "ахуен", "офиген", "афиген", "заебис", "зашибис"}, true)) return "praise";
        boolean you = word(words, YOU, false);
        if (fuzzy(nwords, RUDE) || phrase(lower, INSULT_PHRASE)) return "insult";
        if (fuzzy(nwords, SWEAR)) return you ? "insult" : "swear";
        if (word(words, INSULT, true)) return "insult";
        if (word(words, STORY, true) || phrase(lower, STORY_PHRASE)) return "story";
        if (word(words, SORRY, false)) return "sorry";
        if (word(words, THANKS, true)) return "thanks";
        if (phrase(lower, HOW)) return "how";
        if (word(words, WEATHER, true)) return "weather";
        if (word(words, FOOD, true)) return "food";
        if (word(words, WORK, true) || phrase(lower, WORK_PHRASE)) return "work";
        if (word(words, FAMILY, true)) return "family";
        if (word(words, NIGHT, true)) return "night";
        if (word(words, GOLEM, true)) return "golem";
        if (word(words, HOME, true)) return "home";
        if (phrase(lower, NAME)) return "name";
        if (word(words, PRAISE, true)) return "praise";
        if (word(words, GREET, false)) return "greet";
        if (word(words, BYE, false) || phrase(lower, BYE_PHRASE)) return "bye";
        if (word(words, TRADE, true)) return "trade";
        if (word(words, HELP, true)) return "help";
        if (word(words, DANGER, true)) return "danger";
        if (word(words, JOKE, true)) return "joke";
        if (lower.trim().endsWith("?") || (!words.isEmpty() && word(List.of(words.get(0)), ASK, false))) return "question";
        return "other";
    }

    private static void rule(ServerLevel level, Mob mob, ServerPlayer p, String text) {
        if (!canReply(level, mob, p)) return;
        RandomSource r = level.getRandom();
        String intent = intent(text);
        DialogueMemory.Pair pair = pair(mob, p);
        int before = DialogueMemory.mood(pair);
        int delta = switch (intent) {
            case "insult" -> -2;
            case "swear" -> -1;
            case "praise" -> 2;
            case "thanks", "greet" -> 1;
            case "sorry" -> before < 0 ? 3 : 0;
            default -> 0;
        };
        int after = DialogueMemory.shiftedMood(pair, delta);
        if (!(mob instanceof Villager v)) {
            Component reply = mobReply(mob, p, intent, after);
            DialogueMemory.remember(pair, after, text, reply, false);
            say(level, mob, reply, 24);
            return;
        }
        String pool = intent;
        if (intent.equals("insult") && after <= -5) pool = "insult_hard";
        else if (intent.equals("sorry") && before >= 0) pool = "sorry_none";
        else if (intent.equals("trade") && after <= -3) pool = "trade_no";
        else if (delta == 0 && after <= -3 && !intent.equals("sorry")) pool = "grumpy";
        String quote = text.length() > 24 ? text.substring(0, 24) + "..." : text;
        Component reply;
        if (pool.equals("story")) {
            reply = r.nextBoolean() ? Chatter.story(level, v, r) : Chatter.gossip(level, v, p.getName(), r);
        } else {
            reply = Component.translatable("mobslearn.chat." + pool + "." + r.nextInt(POOLS.getOrDefault(pool, 3)), p.getName(), v.getDisplayName(), quote);
        }
        if (WorldBrain.levelAt("villager_tease", level, v.blockPosition()) <= 0 && r.nextFloat() < 0.6f) {
            reply = Component.translatable("mobslearn.chat.mumble." + r.nextInt(6)).append(reply);
        }
        DialogueMemory.remember(pair, after, text, reply, false);
        effects(level, v, p, delta, after);
        say(level, v, reply, 24);
        if (intent.equals("help")) VillageDefense.offer(level, v, p);
        if (intent.equals("sorry")) Colonies.apologize(level, v, p);
    }

    private static Component mobReply(Mob mob, ServerPlayer player, String intent, int mood) {
        String topic = switch (intent) {
            case "name", "bye", "sorry", "insult", "swear", "thanks", "praise" -> intent;
            default -> mood <= -3 ? "grumpy" : species(mob);
        };
        return Component.translatableWithFallback("mobslearn.chat.mob." + topic, "I hear you, %1$s.", player.getName(), mob.getDisplayName());
    }

    private static String species(Mob mob) {
        var type = mob.getType();
        if (type == EntityType.PIGLIN || type == EntityType.ZOMBIFIED_PIGLIN) return "piglin";
        if (type == EntityType.BLAZE) return "blaze";
        if (type == EntityType.WITHER_SKELETON) return "skeleton";
        MobGroup group = MobGroup.of(type);
        return group == null ? null : group.key();
    }
}
