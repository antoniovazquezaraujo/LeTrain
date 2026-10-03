import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates {@code docs/developer/architecture/ClassIndex.md} from the source tree.
 *
 * <p>
 * Run with {@code java tools/GenClassIndex.java} from the repository root (works on any OS with
 * JDK 17, no extra tooling). Entries are {@code [[package.Class|Class]] (module)} wikilinks, so the
 * index is useful in the Obsidian vault and to locate code (per {@code GEMINI.md}). CI runs it and
 * fails if the committed file is out of date.
 */
public class GenClassIndex {

    private static final List<String> MODULES = List.of("core", "soundscape", "game-audio",
            "ui-terminal", "ui-graphic", "launcher-terminal", "launcher-graphic",
            "launcher-check");

    private record Entry(String group, String fqcn, String shortName, String module) {}

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        Path out = root.resolve("docs/developer/architecture/ClassIndex.md");

        List<Entry> entries = new ArrayList<>();
        for (String module : MODULES) {
            Path base = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> !f.getFileName().toString().equals("package-info.java"))
                        .sorted()
                        .toList()) {
                    Path rel = base.relativize(file);
                    String relPath = rel.toString().replace('\\', '/');
                    String fqcn = relPath.substring(0, relPath.length() - ".java".length())
                            .replace('/', '.');
                    String dir = rel.getParent() == null ? ""
                            : rel.getParent().toString().replace('\\', '/');
                    entries.add(new Entry(groupOf(dir), fqcn,
                            fqcn.substring(fqcn.lastIndexOf('.') + 1), module));
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::group).thenComparing(Entry::fqcn));

        StringBuilder sb = new StringBuilder();
        sb.append("# LeTrain class index\n\n");
        sb.append(
                "> **Generated automatically** by `java tools/GenClassIndex.java`. Do not edit by hand.\n");
        sb.append(
                "> Format: `[[package.Class|Class]] (module)`. Useful in Obsidian and to locate code.\n");
        String last = null;
        for (Entry e : entries) {
            if (!e.group().equals(last)) {
                if (last != null) {
                    sb.append('\n');
                }
                sb.append("## ").append(e.group()).append('\n');
                last = e.group();
            }
            sb.append("- [[").append(e.fqcn()).append('|').append(e.shortName()).append("]] (")
                    .append(e.module()).append(")\n");
        }
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out);
    }

    private static String groupOf(String dir) {
        if (dir.equals("letrain")) {
            return "letrain (root)";
        }
        if (dir.startsWith("letrain/")) {
            return "letrain." + dir.substring("letrain/".length()).split("/")[0];
        }
        return "(others)";
    }

    private GenClassIndex() {}
}
