import re, os, sys
root = sys.argv[1]
R = [
    (r'\.entityTags\(\)', '.getTags()'),
    (r'MobEffects\.SPEED\b', 'MobEffects.MOVEMENT_SPEED'),
    (r'MobEffects\.JUMP_BOOST\b', 'MobEffects.JUMP'),
    (r'MobEffects\.STRENGTH\b', 'MobEffects.DAMAGE_BOOST'),
    (r'MobEffects\.SLOWNESS\b', 'MobEffects.MOVEMENT_SLOWDOWN'),
    (r'MobEffects\.RESISTANCE\b', 'MobEffects.DAMAGE_RESISTANCE'),
    (r'MobEffects\.NAUSEA\b', 'MobEffects.CONFUSION'),
    (r'\.gui\.setScreen\(', '.setScreen('),
    (r'\.gui\.screen\(\)', '.screen'),
    (r'\bEnderman\b', 'EnderMan'),
    (r'\.identifier\(\)', '.location()'),
    (r'SoundEvents\.SHIELD_BLOCK\.value\(\)', 'SoundEvents.SHIELD_BLOCK'),
    (r'(\w+)\.getDropChances\(\)\.byEquipment\(([^)]*)\)', r'com.rrm.mobslearn.mob.Compat.dropChance(\1, \2)'),
    (r'equip\(mob, EquipmentSlot\.SADDLE, Items\.SADDLE\);', 'if (mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) horse.equipSaddle(new ItemStack(Items.SADDLE), null);'),
    (r'MapDecorationTypes\.ABANDONED_CAMP', 'MapDecorationTypes.TARGET_POINT'),
    (r'server\.isSingleplayerOwner\(player\.nameAndId\(\)\)', 'server.isSingleplayerOwner(player.getGameProfile())'),
    (r'Commands\.hasPermission\(Commands\.LEVEL_GAMEMASTERS\)\.test\(player\.createCommandSourceStack\(\)\)', 'player.hasPermissions(2)'),
    (r'e\.getLastHurtByPlayer\(\)', '(e.getLastHurtByMob() instanceof Player lastP ? lastP : null)'),
    (r'\.pose\(\)\.rotate\(([^;]*)\);', r'.pose().mulPose(com.mojang.math.Axis.ZP.rotation(\1));'),
    (r'public static final KeyMapping\.Category CATEGORY = KeyMapping\.Category\.register\([^;]*\);', 'public static final String CATEGORY = "key.category.mobslearn.main";'),
    (r'KeyMappingHelper\.registerKeyMapping', 'KeyBindingHelper.registerKeyBinding'),
    (r'net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper', 'net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper'),
    (r'InputConstants\.Type\.KEYBOARD', 'InputConstants.Type.KEYSYM'),
    (r'HudElementRegistry\.addLast\([^,]*,\s*([\w.:]+)\);', r'HudRenderCallback.EVENT.register(\1);'),
    (r'import net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.hud\.HudElementRegistry;', 'import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;'),
    (r'END_LEVEL_TICK', 'END_WORLD_TICK'), (r'START_LEVEL_TICK', 'START_WORLD_TICK'),
    (r'EntityReference\.of\((\w+)\)', r'\1.getUUID()'),
    (r'import net\.minecraft\.world\.entity\.EntityReference;\n', ''),
    (r'v\.getVillagerData\(\)\.profession\(\)\.unwrapKey\(\)\.map\(k -> k\.location\(\)\.getPath\(\)\)\.orElse\("none"\)',
     'net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).getPath()'),
]
for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java'): continue
        p = os.path.join(dp, f); s = open(p, encoding='utf-8').read(); o = s
        for a, b in R: s = re.sub(a, b, s)
        if s != o: open(p, 'w', encoding='utf-8').write(s)
