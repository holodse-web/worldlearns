import re, os, sys, json
root = sys.argv[1]
idx = json.load(open('/home/claude/port/idx1214.json'))

def split_args(s, start):
    depth = 0; args = []; cur = start; i = start
    while i < len(s):
        c = s[i]
        if c in '([{': depth += 1
        elif c in ')]}':
            if depth == 0:
                args.append(s[cur:i]); return args, i
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(s[cur:i]); cur = i + 1
        elif c == '"':
            i += 1
            while s[i] != '"':
                if s[i] == '\\': i += 1
                i += 1
        i += 1
    raise Exception('unbalanced')

def rewrite_calls(s, name, fn):
    out = []; i = 0
    pat = re.compile(name)
    while True:
        m = pat.search(s, i)
        if not m: out.append(s[i:]); break
        out.append(s[i:m.start()])
        args, end = split_args(s, m.end())
        out.append(fn(m, args))
        i = end + 1
    return ''.join(out)

SIMPLE = [
    (r'\bGuiGraphicsExtractor\b', 'GuiGraphics'),
    (r'\bIdentifier\b', 'ResourceLocation'),
    (r'\bEntityTypes\b', 'EntityType'),
    (r'\.pushMatrix\(\)', '.pushPose()'),
    (r'\.popMatrix\(\)', '.popPose()'),
    (r'\bextractRenderState\(', 'render('),
    (r'\bextractBackground\(', 'renderBackground('),
    (r'\bextractContents\(', 'renderWidget('),
    (r'\.sendOverlayMessage\(([^;]*)\);', r'.displayClientMessage(\1, true);'),
    (r'\.snapTo\(', '.moveTo('),
    (r'\.needsSync = true', '.hurtMarked = true'),
    (r'\.isBrightOutside\(\)', '.isDay()'),
    (r'\.isDarkOutside\(\)', '.isNight()'),
    (r'\.getOverworldClockTime\(\)', '.getDayTime()'),
    (r'Blocks\.(BED|WOOL|CARPET|STAINED_GLASS|BANNER|CANDLE_CAKE)\.(\w+)\(\)', lambda m: 'Blocks.' + re.sub('([a-z])([A-Z])', r'\1_\2', m.group(2)).upper() + '_' + m.group(1)),
    (r'\.requires\(Commands\.hasPermission\(Commands\.LEVEL_GAMEMASTERS\)\)', '.requires(src -> src.hasPermission(2))'),
    (r'PayloadTypeRegistry\.clientboundPlay\(\)', 'PayloadTypeRegistry.playS2C()'),
    (r'PayloadTypeRegistry\.serverboundPlay\(\)', 'PayloadTypeRegistry.playC2S()'),
    (r',\s*net\.minecraft\.world\.item\.component\.SwingAnimation\.DEFAULT,\s*true\)', ', true)'),
    (r'\bnet\.minecraft\.world\.level\.gamerules\.GameRules\b', 'net.minecraft.world.level.GameRules'),
    (r'getGameRules\(\)\.get\(GameRules\.MOB_GRIEFING\)', 'getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)'),
    (r'getGameRules\(\)\.get\(net\.minecraft\.world\.level\.GameRules\.MOB_GRIEFING\)', 'getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)'),
    (r'([\w.()]+?)\.getGameRules\(\)\.get\(MobsLearnRules\.ENABLED\)', r'MobsLearnRules.get(\1.getGameRules())'),
    (r'([\w.()]+?)\.getGameRules\(\)\.set\(MobsLearnRules\.ENABLED, ', r'MobsLearnRules.set(\1.getGameRules(), '),
    (r'\bg\.nextStratum\(\);', 'g.pose().translate(0, 0, 400);'),
    (r'BlockTags\.SUBSTRATE_OVERWORLD\b', 'BlockTags.DIRT'),
    (r'\.identifier\(\)\.getPath\(\)', '.location().getPath()'),
    (r'(\b(?:g|gg|graphics)\b)\.text\(', r'\1.drawString('),
    (r'(\b(?:g|gg|graphics)\b)\.item\(', r'\1.renderItem('),
    (r'MouseButtonEvent event, boolean doubleClick', 'double mouseX, double mouseY, int button'),
    (r'super\.mouseClicked\(event, doubleClick\)', 'super.mouseClicked(mouseX, mouseY, button)'),
    (r'\bevent\.x\(\)', 'mouseX'), (r'\bevent\.y\(\)', 'mouseY'), (r'\bevent\.button\(\)', 'button'),
    (r'(?:net\.minecraft\.client\.input\.)?KeyEvent event', 'int keyCode, int scanCode, int modifiers'),
    (r'super\.keyPressed\(event\)', 'super.keyPressed(keyCode, scanCode, modifiers)'),
    (r'\bevent\.key\(\)', 'keyCode'),
    (r'onPress\(InputWithModifiers input\)', 'onPress()'),
    (r'import net\.minecraft\.client\.input\.\w+;\n', ''),
]

def fix_pose(s):
    def tr(m, args, extra):
        if len(args) == 2: args = args + [' ' + extra]
        return m.group(0) + ','.join(args) + ')'
    s = rewrite_calls(s, r'\.pose\(\)\.translate\(', lambda m, a: tr(m, a, '0'))
    s = rewrite_calls(s, r'\.pose\(\)\.scale\(', lambda m, a: tr(m, a, '1'))
    return s

def fix_blit(s):
    def b(m, args):
        if len(args) == 9:
            return 'com.rrm.mobslearn.client.Ui.blitUV(' + m.group(1) + ',' + ','.join(args) + ')'
        return m.group(0) + ','.join(args) + ')'
    return rewrite_calls(s, r'\b(g|gg|graphics)\.blit\(', b)

def fix_imports(s):
    def rep(m):
        fq = m.group(1)
        if fq.startswith('net.minecraft.') and not fq.startswith('net.minecraft.client.input'):
            simple = fq.rsplit('.', 1)[1]
            cands = idx.get(simple, [])
            if fq not in cands and len(cands) == 1:
                return 'import ' + cands[0] + ';'
        return m.group(0)
    return re.sub(r'import ([\w.]+);', rep, s)

def fix_fq(s):
    def rep(m):
        fq = m.group(0)
        simple = fq.rsplit('.', 1)[1]
        cands = idx.get(simple, [])
        if fq not in cands and len(cands) == 1: return cands[0]
        return fq
    return re.sub(r'\bnet\.minecraft\.(?:[a-z0-9_]+\.)+[A-Z]\w*', rep, s)

for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java'): continue
        p = os.path.join(dp, f)
        s = open(p, encoding='utf-8').read()
        o = s
        for a, b in SIMPLE: s = re.sub(a, b, s)
        s = fix_pose(s)
        s = fix_blit(s)
        s = fix_imports(s)
        s = fix_fq(s)
        if s != o: open(p, 'w', encoding='utf-8').write(s)
