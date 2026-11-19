import json
from pathlib import Path
import re
import shutil
import sys


TEMPLATES = Path(__file__).with_name('templates')
SERVER_PAYLOADS = {
    'Forget': 'com.rrm.mobslearn.mob.Colonies.forget(player, payload.id())',
    'Answer': 'com.rrm.mobslearn.mob.VillageDefense.answer(player, payload.villager(), payload.choice())',
    'Trade': 'com.rrm.mobslearn.mob.Colonies.trade(player, payload.chief(), payload.offer())',
    'ColoniesRequest': 'com.rrm.mobslearn.mob.Colonies.sendList(player)',
    'Settings': 'com.rrm.mobslearn.learn.WorldBrain.configure(player, payload)',
}
CLIENT_PAYLOADS = ('Notice', 'Ask', 'Result', 'TradeOpen', 'Colonies', 'Sync', 'Learn')


def render(template, output, fields=None):
    content = (TEMPLATES / template).read_text(encoding='utf-8')
    for key, value in (fields or {}).items():
        content = content.replace(f'@{key}@', value)
    unresolved = re.findall(r'@[A-Z_]+@', content)
    if unresolved:
        raise ValueError(f'{template}: {unresolved}')
    output.write_text(content, encoding='utf-8')


def transform(file, replacements):
    content = file.read_text(encoding='utf-8')
    for old, new in replacements.items():
        if old not in content:
            raise ValueError(f'{file.name}: missing {old}')
        content = content.replace(old, new)
    file.write_text(content, encoding='utf-8')


def write_entrypoints(java, minecraft, legacy, current):
    fml = 'net.minecraftforge.fml' if legacy else 'net.neoforged.fml'
    loader = 'net.minecraftforge' if legacy else 'net.neoforged.neoforge'
    bus = 'net.minecraftforge.eventbus.api' if legacy else 'net.neoforged.bus.api'
    event_bus = f'{loader}.common.' + ('MinecraftForge.EVENT_BUS' if legacy else 'NeoForge.EVENT_BUS')
    distribution = f'{fml}.loading.FMLEnvironment.' + ('getDist()' if current else 'dist')
    bus_import = f'{bus}.IEventBus'
    mod_bus = 'IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();' if legacy else ''
    client = 'com.rrm.mobslearn.client.MobsLearnClient'
    end_tick = 'if (event.phase != TickEvent.Phase.END) return;' if legacy else ''
    modern_tick = f'import {loader}.event.tick.'
    render('MobsLearn.java', java / 'MobsLearn.java', {
        'IDENTIFIER': 'Identifier' if current else 'ResourceLocation',
        'MAKE_ID': 'Identifier.fromNamespaceAndPath(MOD_ID, path)' if current else
            'new ResourceLocation(MOD_ID, path)' if legacy else 'ResourceLocation.fromNamespaceAndPath(MOD_ID, path)',
        'MOD_IMPORT': f'{fml}.common.Mod',
        'BUS_IMPORT': bus_import,
        'LOADER': loader,
        'CONSTRUCTOR_ARG': '' if legacy else 'IEventBus modBus',
        'CONSTRUCTOR_BODY': mod_bus,
        'RULE_REGISTRATION': 'modBus.addListener(MobsLearnRules::register);' if current else 'MobsLearnRules.register();',
        'NET_REGISTRATION': 'Net.register();' if legacy else 'modBus.addListener(Net::register);',
        'EVENT_BUS': event_bus,
        'CLIENT_REGISTRATION': f'if ({distribution}.isClient()) {client}.register(modBus, bus);',
        'CHUNK_IMPORT': 'import net.minecraft.world.level.chunk.LevelChunk;' if minecraft in ('1.21.1', '1.20.1') else '',
        'LOAD_CHUNK': 'var chunk = event.getChunk();\n        levelTasks.add(() -> Colonies.chunkLoaded(level, chunk, true));'
            if minecraft in ('26.3', '1.21.4') else
            'if (event.getChunk() instanceof LevelChunk chunk) levelTasks.add(() -> Colonies.chunkLoaded(level, chunk, true));',
        'BREAK_EVENT_IMPORT': f'{loader}.event.level.block.BreakBlockEvent' if current else f'{loader}.event.level.BlockEvent',
        'BREAK_EVENT': 'BreakBlockEvent' if current else 'BlockEvent.BreakEvent',
        'INCOMING_DAMAGE': 'LivingAttackEvent' if legacy else 'LivingIncomingDamageEvent',
        'DAMAGE_TYPE': '' if legacy else '.Post',
        'DAMAGE_AMOUNTS': 'event.getAmount(), event.getAmount(), false' if legacy else
            'event.getOriginalDamage(), event.getInflictedDamage(), event.getBlockedDamage() > 0' if current else
            'event.getOriginalDamage(), event.getNewDamage(), event.getBlockedDamage() > 0',
        'TICK_IMPORTS': f'import {loader}.event.TickEvent;' if legacy else
            f'{modern_tick}LevelTickEvent;\n{modern_tick}ServerTickEvent;',
        'SERVER_TICK_TYPE': 'TickEvent.ServerTickEvent' if legacy else 'ServerTickEvent.Post',
        'LEVEL_TICK_TYPE': 'TickEvent.LevelTickEvent' if legacy else 'LevelTickEvent.Post',
        'END_TICK_CHECK': end_tick,
        'TICK_LEVEL': 'event.level' if legacy else 'event.getLevel()',
        'CHAT_MESSAGE': 'PlayerChatMessage.unsigned(player.getUUID(), event.getRawText())',
    })

    client_registrations = [
        f'        event.register(Net.{name}Payload.TYPE, (payload, context) -> handle(payload));'
        for name in CLIENT_PAYLOADS
    ]
    register_client = ('private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {\n'
        + '\n'.join(client_registrations) + '\n    }')
    render('MobsLearnClient.java', java / 'client/MobsLearnClient.java', {
        'BUS_IMPORT': bus_import,
        'LOADER': loader,
        'FML': fml,
        'CLIENT_TICK_IMPORT': f'import {loader}.event.TickEvent;' if legacy else f'import {loader}.client.event.ClientTickEvent;',
        'CLIENT_PAYLOAD_IMPORT': f'import {loader}.client.network.event.RegisterClientPayloadHandlersEvent;' if current else '',
        'CLIENT_PAYLOAD_LISTENER': 'modBus.addListener(MobsLearnClient::registerPayloads);' if current else '',
        'CLIENT_PAYLOAD_REGISTRATION': register_client if current else '',
        'CATEGORY_TYPE': 'KeyMapping.Category' if current else 'String',
        'CATEGORY_VALUE': 'new KeyMapping.Category(MobsLearn.id("main"))' if current else '"key.category.mobslearn.main"',
        'CATEGORY_REGISTRATION': 'event.registerCategory(CATEGORY);' if current else '',
        'KEY_TYPE': 'KEYBOARD' if current else 'KEYSYM',
        'GET_SCREEN': 'gui.screen()' if current else 'screen',
        'SET_SCREEN': 'gui.setScreen' if current else 'setScreen',
        'CLIENT_TICK_TYPE': 'TickEvent.ClientTickEvent' if legacy else 'ClientTickEvent.Post',
        'END_TICK_CHECK': end_tick,
    })


def write_network(java, legacy, current):
    client = 'com.rrm.mobslearn.client.MobsLearnClient'
    net = java / 'net/Net.java'
    content = net.read_text(encoding='utf-8')
    registrations = []
    for name, action in SERVER_PAYLOADS.items():
        record = f'{name}Payload'
        if legacy:
            registrations.append(f'        LegacyNetwork.toServer({record}.class, {record}.TYPE, (payload, player) -> {action});')
        else:
            registrations.extend([
                f'        registrar.playToServer({record}.TYPE, {record}.CODEC, (payload, context) -> {{',
                f'            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {action};',
                '        });',
            ])
    for name in CLIENT_PAYLOADS:
        record = f'{name}Payload'
        if legacy:
            registrations.append(f'        LegacyNetwork.toClient({record}.class, {record}.TYPE, payload -> {client}.handle(payload));')
        else:
            handler = '' if current else f', (payload, context) -> {client}.handle(payload)'
            registrations.append(f'        registrar.playToClient({record}.TYPE, {record}.CODEC{handler});')
    if legacy:
        signature = 'public static void register() {'
        render('LegacyNetwork.java', java / 'net/LegacyNetwork.java')
        render('LegacyServerNetwork.java', java / 'net/SNet.java')
        render('LegacyClientNetwork.java', java / 'client/CNet.java')
    else:
        signature = 'public static void register(RegisterPayloadHandlersEvent event) {'
        registrations.insert(0, '        var registrar = event.registrar("1").optional();')
        content = content.replace('import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;',
            'import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;')
        distributor = 'ClientPacketDistributor' if current else 'PacketDistributor'
        package = 'client.network' if current else 'network'
        render('ModernServerNetwork.java', java / 'net/ServerNetwork.java')
        render('ModernClientNetwork.java', java / 'client/ClientNetwork.java', {
            'DISTRIBUTOR_IMPORT': f'net.neoforged.neoforge.{package}.{distributor}',
            'DISTRIBUTOR': distributor,
        })
    replacement = '    ' + signature + '\n' + '\n'.join(registrations) + '\n    }'
    content, count = re.subn(r'    public static void register\(\) \{.*?\n    }', replacement, content, count=1, flags=re.S)
    if count != 1:
        raise ValueError('Net.java: missing packet registration')
    net.write_text(content, encoding='utf-8')


def write_rules(java, current):
    rules = java / 'MobsLearnRules.java'
    if current:
        render('ModernGameRule.java', rules)
        return
    transform(rules, {
        'import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;\n': '',
        'import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;\n': '',
        'GameRuleRegistry.register': 'GameRules.register',
        'GameRuleFactory.createBooleanRule': 'GameRules.BooleanValue.create',
    })


def write_metadata(resources, minecraft, version, legacy):
    fabric = resources / 'fabric.mod.json'
    metadata = json.loads(fabric.read_text(encoding='utf-8'))
    fabric.unlink()
    target = 'forge' if legacy else 'neoforge'
    loader_range = '[47,)' if legacy else '[1,)'
    dependency_type = 'mandatory = true' if legacy else 'type = "required"'
    pinned_version = version.partition('-')[2] if legacy else version
    version_range = f'[{pinned_version},)'
    minecraft_range = '[26.3,26.4)' if minecraft == '26.3' else f'[{minecraft}]'
    config = [
        'modLoader = "javafml"',
        f'loaderVersion = "{loader_range}"',
        f'license = {json.dumps(metadata["license"])}',
        '',
        '[[mods]]',
        'modId = "mobslearn"',
        f'version = {json.dumps(metadata["version"] + "-neoforge")}',
        f'displayName = {json.dumps(metadata["name"])}',
        f'{"iconFile" if minecraft == "26.3" else "logoFile"} = {json.dumps(metadata["icon"])}',
        f'authors = {json.dumps(", ".join(metadata["authors"]))}',
        f'description = {json.dumps(metadata["description"])}',
    ]
    if legacy:
        config.append('displayTest = "IGNORE_ALL_VERSION"')
        for mixin in ('mobslearn.mixins.json', 'mobslearn.client.mixins.json'):
            path = resources / mixin
            data = json.loads(path.read_text(encoding='utf-8'))
            data['refmap'] = 'mobslearn.refmap.json'
            path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    else:
        for mixin in ('mobslearn.mixins.json', 'mobslearn.client.mixins.json'):
            config.extend(['', '[[mixins]]', f'config = "{mixin}"'])
    for mod_id, supported in ((target, version_range), ('minecraft', minecraft_range)):
        config.extend(['', '[[dependencies.mobslearn]]', f'modId = "{mod_id}"', dependency_type,
            f'versionRange = "{supported}"', 'ordering = "NONE"', 'side = "BOTH"'])
    output = resources / 'META-INF' / ('mods.toml' if legacy else 'neoforge.mods.toml')
    output.parent.mkdir(exist_ok=True)
    output.write_text('\n'.join(config) + '\n', encoding='utf-8')
    if minecraft == '26.3':
        pack_format = {'min_format': [97, 1], 'max_format': [121, 0]}
    else:
        resource_version, data_version = {'1.21.4': (46, 61), '1.21.1': (34, 48), '1.20.1': (15, 15)}[minecraft]
        pack_format = {'pack_format': data_version}
        if resource_version != data_version:
            pack_format['supported_formats'] = [resource_version, data_version]
    pack = {'pack': {'description': metadata['name'], **pack_format}}
    (resources / 'pack.mcmeta').write_text(json.dumps(pack, indent=2) + '\n', encoding='utf-8')


def prepare(minecraft, version, source, output):
    if minecraft not in ('26.3', '1.21.4', '1.21.1', '1.20.1'):
        raise ValueError(f'Unsupported Minecraft version: {minecraft}')
    legacy = minecraft == '1.20.1'
    current = minecraft == '26.3'
    expected = TEMPLATES.resolve().parent / 'build' / minecraft / 'sources'
    if output != expected:
        raise ValueError(f'Unexpected output path: {output}')
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(source / 'src/main', output)
    java = output / 'java/com/rrm/mobslearn'
    write_entrypoints(java, minecraft, legacy, current)
    write_network(java, legacy, current)
    write_rules(java, current)
    fml = 'net.minecraftforge.fml' if legacy else 'net.neoforged.fml'
    for file in java.rglob('*.java'):
        content = file.read_text(encoding='utf-8')
        if 'net.fabricmc' not in content and 'FabricLoader' not in content:
            continue
        content = content.replace('import net.fabricmc.loader.api.FabricLoader;\n', '')
        content = content.replace('net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(', f'{fml}.ModList.get().isLoaded(')
        content = content.replace('FabricLoader.getInstance().getConfigDir()', f'{fml}.loading.FMLPaths.CONFIGDIR.get()')
        content = content.replace('net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking', 'com.rrm.mobslearn.net.ServerNetwork')
        content = content.replace('net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking', 'com.rrm.mobslearn.client.ClientNetwork')
        content = content.replace('ServerPlayNetworking.', 'ServerNetwork.').replace('ClientPlayNetworking.', 'ClientNetwork.')
        if 'net.fabricmc' in content or 'FabricLoader' in content:
            raise ValueError(f'{file}: unresolved Fabric dependency')
        file.write_text(content, encoding='utf-8')
    write_metadata(output / 'resources', minecraft, version, legacy)


if __name__ == '__main__':
    minecraft, version, source, output = sys.argv[1:]
    prepare(minecraft, version, Path(source), Path(output).resolve())
