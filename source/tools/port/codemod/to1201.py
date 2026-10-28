import re, os, sys
root = sys.argv[1]
R = [
    (r'import net\.fabricmc\.fabric\.api\.networking\.v1\.ServerPlayNetworking;\n', ''),
    (r'import net\.fabricmc\.fabric\.api\.client\.networking\.v1\.ClientPlayNetworking;\n', ''),
    (r'\bServerPlayNetworking\.', 'com.rrm.mobslearn.net.SNet.'),
    (r'\bClientPlayNetworking\.', 'com.rrm.mobslearn.client.CNet.'),
    (r'ResourceLocation\.fromNamespaceAndPath\(', 'new ResourceLocation('),
    (r'ResourceLocation\.withDefaultNamespace\(', 'new ResourceLocation('),
    (r'mouseScrolled\(double (\w+), double (\w+), double (\w+), double (\w+)\)', r'mouseScrolled(double \1, double \2, double \4)'),
    (r'\n\s*@Override\n(\s*)public void renderBackground\(GuiGraphics g, int mx, int my, float \w+\) \{\n\s*\}\n', '\n'),
    (r'(GuiGraphics \w+), (?:net\.minecraft\.client\.)?DeltaTracker (\w+)\)', r'\1, float \2)'),
]
for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java') or f in ('SNet.java', 'CNet.java'): continue
        p = os.path.join(dp, f); s = open(p, encoding='utf-8').read(); o = s
        for a, b in R: s = re.sub(a, b, s)
        if s != o: open(p, 'w', encoding='utf-8').write(s)
