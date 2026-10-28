import re, os, sys
root = sys.argv[1]
C = 'com.rrm.mobslearn.mob.Compat.'
R = [
    (r'net\.minecraft\.resources\.new ResourceLocation\(', 'new net.minecraft.resources.ResourceLocation('),
    (r"([\w.()]+?)\.has\(DataComponents\.FOOD\)", r"\1.isEdible()"),
    (r'Blocks\.SHORT_GRASS\b', 'Blocks.GRASS'),
    (r'\.isSameItemSameComponents\(', '.isSameItemSameTags('),
    (r'\.getFirst\(\)', '.get(0)'), (r'\.removeFirst\(\)', '.remove(0)'),
    (r'\.getAdvancements\(\)\.get\(', '.getAdvancements().getAdvancement('),
    (r'new Arrow\(level, mob, new ItemStack\(Items\.ARROW\), new ItemStack\(Items\.BOW\)\)', 'new Arrow(level, mob)'),
    (r', EntityType\.BOGGED', ''),
    (r'equipSaddle\(new ItemStack\(Items\.SADDLE\), null\)', 'equipSaddle(null)'),
    (r'import net\.minecraft\.client\.DeltaTracker;\n', ''),
    (r'Attributes\.EXPLOSION_KNOCKBACK_RESISTANCE, Attributes\.JUMP_STRENGTH, Attributes\.SCALE, ', 'Attributes.JUMP_STRENGTH, '),
    (r'\n[^\n]*Attributes\.(?:SCALE|EXPLOSION_KNOCKBACK_RESISTANCE)\b[^\n]*', ''),
    (r'new AttributeModifier\(', C + 'mod('),
    (r'(\w+)\.addOrUpdateTransientModifier\(', C + r'addOrUpdate(\1, '),
    (r'\.removeModifier\(([^;]*?)\);', r'.removeModifier(' + C + r'uuid(\1));'),
    (r'\.getModifier\(([^;]*?)\);', r'.getModifier(' + C + r'uuid(\1));'),
    (r'\.hasModifier\((MobsLearn\.id\(\w+\))\)', r'.getModifier(' + C + r'uuid(\1)) != null'),
    (r'\.amount\(\)', '.getAmount()'),
    (r'Operation\.ADD_VALUE', 'Operation.ADDITION'), (r'Operation\.ADD_MULTIPLIED_BASE', 'Operation.MULTIPLY_BASE'), (r'Operation\.ADD_MULTIPLIED_TOTAL', 'Operation.MULTIPLY_TOTAL'),
    (r'Holder<Attribute>', 'Attribute'), (r'Holder<MobEffect>', 'MobEffect'),
    (r'ResourceKey<Enchantment> key, int lvl\) \{', 'Enchantment ench, int lvl) {'),
    (r'\n\s*Holder<Enchantment> ench = level\.registryAccess\(\)\.lookupOrThrow\(Registries\.ENCHANTMENT\)\.getOrThrow\(key\);', ''),
    (r'Enchantments\.POWER\b', 'Enchantments.POWER_ARROWS'), (r'Enchantments\.FLAME\b', 'Enchantments.FLAMING_ARROWS'), (r'Enchantments\.PUNCH\b', 'Enchantments.PUNCH_ARROWS'),
    (r'ServerChunkEvents\.CHUNK_GENERATE\.register\(\(level, chunk\) -> com\.rrm\.mobslearn\.mob\.Colonies\.chunkLoaded\(level, chunk, true\)\);',
     'ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> com.rrm.mobslearn.mob.Colonies.chunkLoaded(level, chunk, chunk.getInhabitedTime() == 0));'),
]
for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java') or f == 'Compat.java': continue
        p = os.path.join(dp, f); s = open(p, encoding='utf-8').read(); o = s
        for a, b in R: s = re.sub(a, b, s)
        if s != o: open(p, 'w', encoding='utf-8').write(s)
