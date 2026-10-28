import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.mappingio.*;
import net.fabricmc.mappingio.adapter.*;
import net.fabricmc.mappingio.format.*;
import net.fabricmc.mappingio.format.proguard.*;
import net.fabricmc.mappingio.tree.*;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

public class MapTool {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("merge")) {
            MemoryMappingTree tree = new MemoryMappingTree();
            MappingReader.read(Paths.get(a[1]), tree);
            MemoryMappingTree pg = new MemoryMappingTree();
            try (Reader r = Files.newBufferedReader(Paths.get(a[2]))) { ProGuardFileReader.read(r, "named", "official", pg); }
            pg.accept(new MappingSourceNsSwitch(tree, "official"));
            try (MappingWriter w = MappingWriter.create(Paths.get(a[3]), MappingFormat.TINY_2_FILE)) { tree.accept(w); }
            return;
        }
        if (a[0].equals("batch")) {
            Path map = Paths.get(a[1]); String from = a[2], to = a[3]; Path outDir = Paths.get(a[4]);
            List<Path> ins = new ArrayList<>(), cp = new ArrayList<>();
            boolean inCp = false;
            for (int i = 5; i < a.length; i++) {
                if (a[i].equals("--")) { inCp = true; continue; }
                (inCp ? cp : ins).add(Paths.get(a[i]));
            }
            TinyRemapper r = TinyRemapper.newRemapper().withMappings(TinyUtils.createTinyMappingProvider(map, from, to))
                .renameInvalidLocals(true).ignoreConflicts(true).build();
            Map<Path, InputTag> tags = new LinkedHashMap<>();
            for (Path in : ins) { InputTag t = r.createInputTag(); tags.put(in, t); r.readInputs(t, in); }
            for (Path c : cp) r.readClassPath(c);
            for (Map.Entry<Path, InputTag> e : tags.entrySet()) {
                try (OutputConsumerPath oc = new OutputConsumerPath.Builder(outDir.resolve(e.getKey().getFileName())).build()) {
                    oc.addNonClassFiles(e.getKey(), NonClassCopyMode.FIX_META_INF, r);
                    r.apply(oc, e.getValue());
                }
            }
            r.finish();
            return;
        }
        if (a[0].equals("remap")) {
            Path in = Paths.get(a[1]), out = Paths.get(a[2]), map = Paths.get(a[3]);
            String from = a[4], to = a[5];
            boolean mixin = a.length > 6 && a[6].equals("mixin");
            TinyRemapper.Builder b = TinyRemapper.newRemapper().withMappings(TinyUtils.createTinyMappingProvider(map, from, to))
                .renameInvalidLocals(true).rebuildSourceFilenames(true).ignoreConflicts(true).keepInputData(false)
                .fixPackageAccess(false);
            if (mixin) b.extension(new MixinExtension());
            TinyRemapper r = b.build();
            try (OutputConsumerPath oc = new OutputConsumerPath.Builder(out).build()) {
                oc.addNonClassFiles(in, NonClassCopyMode.FIX_META_INF, r);
                r.readInputs(in);
                for (int i = 7; i < a.length; i++) r.readClassPath(Paths.get(a[i]));
                r.apply(oc);
            } finally { r.finish(); }
        }
    }
}
