import zipfile, json, io, re, sys, os
from PIL import Image

JAR = '/home/claude/env/bundle/mc/client.jar'
OUT = '/home/claude/mobslearn/src/main/resources/assets/mobslearn/textures/gui/icon/'
SRC = '/home/claude/mobslearn/src/main/java/com/rrm/mobslearn/learn/'
S = 96
z = zipfile.ZipFile(JAR)
names = set(z.namelist())


def jload(path):
    return json.loads(z.read('assets/minecraft/' + path))


def strip(ns):
    return ns.split(':', 1)[1] if ':' in ns else ns


def find_model(node):
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:model' and isinstance(node.get('model'), str):
            return node
        for key in ('fallback', 'on_false'):
            if key in node:
                r = find_model(node[key])
                if r:
                    return r
        for c in node.get('entries', []):
            r = find_model(c.get('model'))
            if r:
                return r
        for c in node.get('cases', []):
            r = find_model(c.get('model'))
            if r:
                return r
        if 'model' in node:
            return find_model(node['model'])
        if 'on_true' in node:
            return find_model(node['on_true'])
    return None


def chain(model_path):
    textures = {}
    parents = []
    cur = strip(model_path)
    while cur:
        key = 'models/' + cur + '.json'
        if 'assets/minecraft/' + key not in names:
            break
        d = jload(key)
        for k, v in d.get('textures', {}).items():
            if isinstance(v, dict):
                v = v.get('sprite')
            textures.setdefault(k, v)
        parents.append(cur)
        cur = strip(d['parent']) if 'parent' in d else None
    return textures, parents


def resolve(tex, key, depth=0):
    v = tex.get(key)
    while isinstance(v, str) and v.startswith('#') and depth < 10:
        v = tex.get(v[1:])
        depth += 1
    return v


def load_tex(ref):
    if ref is None:
        return None
    p = 'assets/minecraft/textures/' + strip(ref) + '.png'
    if p not in names:
        return None
    im = Image.open(io.BytesIO(z.read(p))).convert('RGBA')
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    if im.width != 16:
        im = im.resize((16, 16), Image.NEAREST)
    return im


def tint(im, argb):
    r, g, b = (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
    px = im.load()
    out = im.copy()
    po = out.load()
    for y in range(im.height):
        for x in range(im.width):
            pr, pg, pb, pa = px[x, y]
            po[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def signed(v):
    return v & 0xFFFFFFFF


def tint_value(t):
    if t.get('type') == 'minecraft:dye':
        return signed(t['default'])
    if t.get('type') == 'minecraft:constant':
        return signed(t['value'])
    if t.get('type') == 'minecraft:grass':
        return 0xFF79C05A
    return None


def shade(im, f):
    return tint(im, 0xFF000000 | (int(255 * f) << 16) | (int(255 * f) << 8) | int(255 * f))


def flat(layers):
    canvas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for im in layers:
        canvas.alpha_composite(im.resize((S, S), Image.NEAREST))
    return canvas


def face(canvas, im, origin, du, dv):
    ox, oy = origin
    ax, ay = du[0] / 16.0, du[1] / 16.0
    bx, by = dv[0] / 16.0, dv[1] / 16.0
    det = ax * by - ay * bx
    ia, ib = by / det, -bx / det
    id_, ie = -ay / det, ax / det
    sup = 8
    big = im.resize((16 * sup, 16 * sup), Image.NEAREST)
    k = 1.0 / sup
    a = ia * 1
    data = (ia * 1, ib * 1, -(ia * ox + ib * oy), id_ * 1, ie * 1, -(id_ * ox + ie * oy))
    data = tuple(v * sup for v in data)
    layer = big.transform((S, S), Image.AFFINE, data, resample=Image.NEAREST)
    canvas.alpha_composite(layer)


def cube(top, left, right):
    c = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    face(c, top, (6, 28), (42, 22), (42, -22))
    face(c, shade(left, 0.78), (6, 28), (42, 22), (0, 44))
    face(c, shade(right, 0.58), (48, 50), (42, -22), (0, 44))
    return c


def shield_icon():
    rows = [
        "................",
        "..oooooooooooo..",
        ".obbbbbbbbbbbbo.",
        ".obwwwwwwwwwwbo.",
        ".obwwdwwwwdwwbo.",
        ".obwwwwwwwwwwbo.",
        ".obwdwwwwwwdwbo.",
        ".obwwwwwwwwwwbo.",
        ".obwwwwddwwwwbo.",
        "..obwwwwwwwwbo..",
        "..obwdwwwwdwbo..",
        "...obwwwwwwbo...",
        "....obwwwwbo....",
        ".....obwwbo.....",
        "......obbo......",
        ".......oo.......",
    ]
    pal = {'o': (36, 30, 30, 255), 'b': (150, 150, 152, 255), 'w': (162, 120, 66, 255), 'd': (136, 98, 52, 255), '.': (0, 0, 0, 0)}
    im = Image.new('RGBA', (16, 16))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            im.putpixel((x, y), pal[ch])
    return flat([im])


HEADS = {'zombie_head': 'entity/zombie/zombie', 'skeleton_skull': 'entity/skeleton/skeleton', 'creeper_head': 'entity/creeper/creeper'}


def head(path):
    t = Image.open(io.BytesIO(z.read('assets/minecraft/textures/' + path + '.png'))).convert('RGBA')
    def part(x, y):
        return t.crop((x, y, x + 8, y + 8)).resize((16, 16), Image.NEAREST)
    return cube(part(8, 0), part(8, 8), part(0, 8))


def bake(item):
    if item in HEADS:
        return head(HEADS[item])
    if item == 'shield':
        return shield_icon()
    if item == 'grass_block':
        top = tint(load_tex('block/grass_block_top'), 0xFF79C05A)
        side = load_tex('block/grass_block_side').copy()
        ov = tint(load_tex('block/grass_block_side_overlay'), 0xFF79C05A)
        side.alpha_composite(ov)
        return cube(top, side, side)
    ipath = 'items/' + item + '.json'
    node = jload(ipath) if 'assets/minecraft/' + ipath in names else None
    m = find_model(node['model']) if node else None
    if not m:
        t = load_tex('item/' + item) or load_tex('block/' + item)
        return flat([t])
    textures, parents = chain(m['model'])
    tints = [tint_value(t) for t in m.get('tints', [])]
    is_flat = any(p in ('item/generated', 'builtin/generated') for p in parents)
    if is_flat or 'layer0' in textures:
        layers = []
        i = 0
        while 'layer%d' % i in textures:
            im = load_tex(resolve(textures, 'layer%d' % i))
            if im is not None:
                if i < len(tints) and tints[i] is not None:
                    im = tint(im, tints[i])
                layers.append(im)
            i += 1
        return flat(layers)
    def pick(*keys):
        for k in keys:
            im = load_tex(resolve(textures, k))
            if im is not None:
                return im
        return None
    top = pick('up', 'top', 'end', 'all', 'texture', 'particle')
    left = pick('north', 'front', 'side', 'all', 'texture', 'particle', 'up')
    right = pick('west', 'east', 'side', 'all', 'texture', 'particle', 'up')
    if top is None:
        t = load_tex('item/' + item)
        return flat([t])
    if tints and tints[0] is not None:
        top, left, right = tint(top, tints[0]), tint(left, tints[0]), tint(right, tints[0])
    return cube(top, left, right)


def ids():
    out = set()
    s = open(SRC + 'Skill.java').read()
    out |= set(re.findall(r'(?:add|absurd)\("[a-z_]+",[^;]*?,\s*"([a-z_]+)"\);', s))
    g = open(SRC + 'MobGroup.java').read()
    out |= set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"', g, re.M))
    return sorted(out)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    extra = sys.argv[1:] + list(HEADS) + ['bell', 'campfire', 'cobweb', 'clock', 'iron_door', 'oak_door', 'brewing_stand', 'map', 'filled_map', 'experience_bottle', 'lightning_rod', 'trial_key', 'recovery_compass', 'echo_shard', 'redstone_torch', 'torch', 'oak_sapling', 'wheat', 'skeleton_spawn_egg', 'creeper_spawn_egg', 'zombie_spawn_egg', 'tripwire_hook', 'moss_block', 'stone_bricks', 'ladder', 'bone', 'gunpowder', 'iron_axe', 'oak_sapling']
    lst = sorted(set(ids()) | set(extra))
    for i in lst:
        try:
            bake(i).save(OUT + i + '.png')
        except Exception as e:
            print('FAIL', i, e)
    print(len(lst))
