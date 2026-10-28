import re, os, sys
root = sys.argv[1]
R = [
    (r'\.create\(level, EntitySpawnReason\.\w+\)', '.create(level)'),
    (r'import net\.minecraft\.world\.entity\.EntitySpawnReason;\n', ''),
    (r'BuiltInRegistries\.(\w+)\.getValue\(', r'BuiltInRegistries.\1.get('),
    (r'\.hurtServer\([^,()]+,\s*', '.hurt('),
    (r'(\w+)\.has\((?:net\.minecraft\.core\.component\.)?DataComponents\.EQUIPPABLE\)', r'(\1.getItem() instanceof net.minecraft.world.item.ArmorItem)'),
    (r'new ThrownEgg\(level, mob, new ItemStack\(Items\.EGG\)\)', 'new ThrownEgg(level, mob)'),
    (r'\.doHurtTarget\(level, ', '.doHurtTarget('),
    (r'SoundEvents\.GENERIC_EAT\.value\(\)', 'SoundEvents.GENERIC_EAT'),
    (r'AnimationMetadataSection\.TYPE', 'AnimationMetadataSection.SERIALIZER'),
    (r'g\.blit\(net\.minecraft\.client\.renderer\.RenderType::guiTextured, tex, ', 'g.blit(tex, '),
    (r'return switch \(slot\.getType\(\)\) \{\s*case HAND -> mob\.getHandDropChances\(\)\[slot\.getIndex\(\)\];\s*case HUMANOID_ARMOR -> mob\.getArmorDropChances\(\)\[slot\.getIndex\(\)\];\s*default -> 0\.085f;\s*\};', 'return 0.085f;'),
]
for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java'): continue
        p = os.path.join(dp, f); s = open(p, encoding='utf-8').read(); o = s
        for a, b in R: s = re.sub(a, b, s)
        if s != o: open(p, 'w', encoding='utf-8').write(s)
