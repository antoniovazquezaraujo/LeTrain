package letrain.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingsCatalog;

/**
 * Quick-reference trees for the DSL. Commands are grouped by the editor tab they belong to so each
 * tab shows only what makes sense there:
 *
 * <ul>
 * <li>{@link Group#CONSOLE} — console/app commands (save, load, ls, info, journal, undo...).</li>
 * <li>{@link Group#BUILD} — the scenario recipe ({@code on build} + {@code on start}): turtle
 * navigation/construction and infrastructure elements.</li>
 * <li>{@link Group#PROGRAM} — the {@code program { ... }} operator: itineraries, triggers and
 * actions.</li>
 * <li>{@link Group#CONFIG} — the {@code configuration { ... }} settings keys
 * ({@code key=value}).</li>
 * </ul>
 *
 * <p>
 * This is the single source of truth for the human-facing command metadata: the editor quick
 * reference and the console {@code help} command both render these trees.
 *
 * <p>
 * <b>Model (v2, #828).</b> Every node has a {@link Kind}. Only {@link Kind#COMMAND} nodes are
 * insertable and only they carry a {@code snippet}; {@link Kind#INFO} nodes carry human prose in
 * {@link Node#info} and are <b>never</b> inserted (the old model put prose in {@code snippet},
 * which produced invalid code). A COMMAND also declares its {@link Scope}: {@code BODY_ONLY}
 * (top-level statement), {@code BLOCK_ONLY} (only inside a trigger/at/every {@code { }}) or
 * {@code BOTH}. {@link Kind#GROUP} and {@link Kind#HEADING} are structural labels.
 */
public class GrammarReference {

    /** A reference tree / console help section. */
    public enum Group {
        CONSOLE, BUILD, PROGRAM, CONFIG
    }

    /** What a tree node is. Only {@link #COMMAND} is insertable. */
    public enum Kind {
        HEADING, GROUP, INFO, COMMAND
    }

    /**
     * Where a {@link Kind#COMMAND} is valid: a top-level statement ({@code BODY_ONLY}), a command
     * inside a trigger/{@code at}/{@code every} block ({@code BLOCK_ONLY}), or both. {@code null}
     * for non-COMMAND nodes.
     */
    public enum Scope {
        BODY_ONLY, BLOCK_ONLY, BOTH
    }

    public static class Node {
        public String label;
        /** Insertable text. Non-null only for {@link Kind#COMMAND}. */
        public String snippet;
        /** Human prose. Non-null only for {@link Kind#INFO}. */
        public String info;
        public Kind kind;
        /** Valid scope. Non-null only for {@link Kind#COMMAND}. */
        public Scope scope;
        public List<Node> children;
        public boolean expanded;

        private Node(Kind kind, String label, String snippet, String info, Scope scope) {
            this.kind = kind;
            this.label = label;
            this.snippet = snippet;
            this.info = info;
            this.scope = scope;
            this.children = new ArrayList<>();
        }

        public static Node heading(String label) {
            return new Node(Kind.HEADING, label, null, null, null);
        }

        public static Node group(String label) {
            return new Node(Kind.GROUP, label, null, null, null);
        }

        /** A non-insertable note: {@code label} is the short title, {@code text} the prose. */
        public static Node info(String label, String text) {
            return new Node(Kind.INFO, label, null, text, null);
        }

        public static Node command(String label, String snippet, Scope scope) {
            return new Node(Kind.COMMAND, label, snippet, null, scope);
        }

        public Node add(Node child) {
            this.children.add(child);
            return this;
        }

        public Node setExpanded(boolean expanded) {
            this.expanded = expanded;
            return this;
        }
    }

    private static final Map<Group, List<Node>> cached = new EnumMap<>(Group.class);

    private GrammarReference() {}

    /** Reference tree for a single editor tab. */
    public static List<Node> getReferenceTree(Group group) {
        return cached.computeIfAbsent(group, GrammarReference::buildGroup);
    }

    /** Full reference tree (all groups), for views without tabs. */
    public static List<Node> getReferenceTree() {
        List<Node> all = new ArrayList<>();
        for (Group group : Group.values()) {
            all.addAll(getReferenceTree(group));
        }
        return all;
    }

    private static List<Node> buildGroup(Group group) {
        switch (group) {
            case CONSOLE:
                return consoleTree();
            case BUILD:
                return buildTree();
            case PROGRAM:
                return programTree();
            case CONFIG:
                return configTree();
            default:
                return new ArrayList<>();
        }
    }

    /** Console/app commands: the "special" commands that are not edits. */
    private static List<Node> consoleTree() {
        List<Node> root = new ArrayList<>();
        root.add(Node.heading("CONSOLE"));

        Node info = Node.group("info");
        info.add(Node.command("all", "info;", Scope.BODY_ONLY));
        info.add(Node.command("by type", "info station;", Scope.BODY_ONLY));
        info.add(Node.command("by id/name", "info station 1;", Scope.BODY_ONLY));
        root.add(info);

        Node ls = Node.group("ls");
        ls.add(Node.command("all", "ls;", Scope.BODY_ONLY));
        ls.add(Node.command("by type", "ls station;", Scope.BODY_ONLY));
        root.add(ls);

        root.add(Node.command("journal", "journal;", Scope.BODY_ONLY));
        root.add(Node.command("undo / redo", "undo; redo;", Scope.BODY_ONLY));
        root.add(Node.command("version", "version;", Scope.BODY_ONLY));

        Node time = Node.group("time");
        time.add(Node.command("show", "time;", Scope.BODY_ONLY));
        time.add(Node.command("set (h:m)", "time set 9:30;", Scope.BODY_ONLY));
        time.add(Node.command("set (hour)", "time set 9;", Scope.BODY_ONLY));
        root.add(time);
        root.add(Node.info("record/edit mode", "record/edit mode (R, Shift+R)"));
        root.add(Node.command("new game", "new game;", Scope.BODY_ONLY));
        root.add(Node.command("save / load", "save backup; load backup;", Scope.BODY_ONLY));
        root.add(Node.command("export / import", "export my_network; import my_network;",
                Scope.BODY_ONLY));
        root.add(Node.command("quit", "quit;", Scope.BODY_ONLY));

        return root;
    }

    /**
     * Scenario recipe: turtle navigation/construction plus infrastructure (on build + on start).
     */
    private static List<Node> buildTree() {
        List<Node> root = new ArrayList<>();
        root.add(Node.heading("ON BUILD"));

        Node go = Node.group("go");
        go.add(Node.command("to coords", "go 0,0;", Scope.BODY_ONLY));
        go.add(Node.command("to element", "go station 1;", Scope.BODY_ONLY));
        go.add(Node.command("to mark", "go mark home;", Scope.BODY_ONLY));
        go.add(Node.command("next/prev", "go next sensor;", Scope.BODY_ONLY));
        root.add(go);

        Node face = Node.group("face");
        face.add(Node.command("direction", "face e;", Scope.BODY_ONLY));
        face.add(Node.command("to element", "face station 1;", Scope.BODY_ONLY));
        root.add(face);

        Node mark = Node.group("mark");
        mark.add(Node.command("set", "mark home;", Scope.BODY_ONLY));
        root.add(mark);

        Node track = Node.group("track");
        track.add(Node.command("write n", "write 5;", Scope.BODY_ONLY));
        track.add(Node.command("write path", "write 3,r,2,l,4;", Scope.BODY_ONLY));
        track.add(Node.command("move path", "move 5;", Scope.BODY_ONLY));
        track.add(Node.command("del", "del;", Scope.BODY_ONLY));
        track.add(Node.command("clear", "clear;", Scope.BODY_ONLY));
        root.add(track);

        Node elements = Node.group("new element").setExpanded(true);
        elements.add(Node.command("station", "new st;", Scope.BODY_ONLY));
        elements.add(Node.command("sensor", "new sn;", Scope.BODY_ONLY));
        elements.add(Node.command("semaphore", "new sm;", Scope.BODY_ONLY));
        elements.add(Node.command("signal", "new sg;", Scope.BODY_ONLY));
        elements.add(Node.command("del element", "del station 1;", Scope.BODY_ONLY));
        root.add(elements);

        Node ops = Node.group("element ops").setExpanded(true);
        ops.add(Node.command("invert", "semaphore 1 invert;", Scope.BOTH));
        ops.add(Node.command("toggle", "semaphore 1 toggle;", Scope.BOTH));
        ops.add(Node.command("fork branch", "fork 1 set curved;", Scope.BOTH));
        ops.add(Node.command("signal limit", "signal 1 set limit 40;", Scope.BODY_ONLY));
        ops.add(Node.command("signal mode", "signal 1 set mode max;", Scope.BODY_ONLY));
        ops.add(Node.command("slide", "slide station 1 fw 2;", Scope.BODY_ONLY));
        ops.add(Node.command("set name", "station 1 set name \"Central\";", Scope.BODY_ONLY));
        root.add(ops);

        Node trains = Node.group("trains");
        trains.add(Node.command("new loco", "new loco A red;", Scope.BODY_ONLY));
        trains.add(Node.command("new wagon", "new wagon b coal;", Scope.BODY_ONLY));
        trains.add(Node.command("clear train", "clear train 1;", Scope.BODY_ONLY));
        root.add(trains);

        root.add(Node.heading("ON START"));
        Node start = Node.group("initial state").setExpanded(true);
        start.add(Node.command("semaphore", "semaphore 1 set closed;", Scope.BOTH));
        start.add(Node.command("fork", "fork 1 set curved;", Scope.BOTH));
        start.add(Node.command("train speed", "train 1 set speed 3;", Scope.BOTH));
        start.add(Node.command("train engine", "train 1 set engine on;", Scope.BOTH));
        root.add(start);

        return root;
    }

    /** Operator program: itineraries, triggers and actions. */
    private static List<Node> programTree() {
        List<Node> root = new ArrayList<>();
        Node program = Node.group("PROGRAM").setExpanded(true);
        program.add(Node.info("placeholders",
                "Placeholders look like <station>. Tab jumps to the next one; # starts a comment."));
        program.add(bodySection());
        program.add(waypointsSection());
        program.add(triggersSection());
        program.add(timeTriggersSection());
        program.add(insideBlocksSection());
        program.add(topLevelSection());
        root.add(program);

        return root;
    }

    private static Node bodySection() {
        Node body = Node.group("BODY — top level").setExpanded(true);
        body.add(Node.command("create itinerary", "create itinerary \"<name>\" {\n"
                + "  add station <station>, load\n" + "  add station <station>, unload\n" + "}",
                Scope.BODY_ONLY));
        body.add(Node.info("waypoint rules",
                "At least two waypoints. Commas are mandatory between plan items; order: arrival, "
                        + "actions, departure."));
        body.add(Node.command("assign itinerary", "assign itinerary \"<name>\" to train <train>;",
                Scope.BODY_ONLY));
        body.add(Node.command("set autopilot true", "train <train> set autopilot true;",
                Scope.BODY_ONLY));
        body.add(Node.command("set autopilot false", "train <train> set autopilot false;",
                Scope.BODY_ONLY));

        Node names = Node.group("SET NAMES").setExpanded(true);
        names.add(
                Node.command("station", "station <station> set name \"<name>\";", Scope.BODY_ONLY));
        names.add(Node.command("sensor", "sensor <sensor> set name \"<name>\";", Scope.BODY_ONLY));
        names.add(Node.command("train", "train <train> set name \"<name>\";", Scope.BOTH));
        names.add(Node.info("train name in blocks", "Train name also works inside blocks."));
        body.add(names);
        return body;
    }

    private static Node waypointsSection() {
        Node waypoints = Node.group("WAYPOINTS");

        Node station = Node.group("add station");
        addWaypointCommands(station, "add station <station>");
        waypoints.add(station);

        waypoints.add(Node.info("waypoint notes",
                "Without a count, couple/uncouple act on every vehicle on that side; a trailing ; "
                        + "after } is optional."));

        Node sensor = Node.group("add sensor");
        addWaypointCommands(sensor, "add sensor <sensor>");
        waypoints.add(sensor);
        return waypoints;
    }

    /**
     * The same 21 plan items for {@code add station} and {@code add sensor} (the grammar shares
     * them).
     */
    private static void addWaypointCommands(Node parent, String waypoint) {
        parent.add(Node.command("load", waypoint + ", load", Scope.BODY_ONLY));
        parent.add(Node.command("unload", waypoint + ", unload", Scope.BODY_ONLY));
        parent.add(Node.command("reverse", waypoint + ", reverse", Scope.BODY_ONLY));
        parent.add(Node.command("stop", waypoint + ", stop", Scope.BODY_ONLY));
        parent.add(Node.command("park", waypoint + ", park", Scope.BODY_ONLY));
        parent.add(Node.command("wait n", waypoint + ", wait <count>", Scope.BODY_ONLY));
        parent.add(Node.command("speed n", waypoint + ", speed <speed>", Scope.BODY_ONLY));
        parent.add(Node.command("stop at sensor",
                waypoint + ", stop at sensor <sensor> speed <speed>", Scope.BODY_ONLY));
        parent.add(Node.command("stop at end", waypoint + ", stop at end speed <speed>",
                Scope.BODY_ONLY));
        parent.add(Node.command("stop when blocked", waypoint + ", stop when blocked speed <speed>",
                Scope.BODY_ONLY));
        parent.add(Node.command("stop on contact", waypoint + ", stop on contact speed <speed>",
                Scope.BODY_ONLY));
        parent.add(Node.command("uncouple", waypoint + ", uncouple backward 1", Scope.BODY_ONLY));
        parent.add(Node.command("uncouple all", waypoint + ", uncouple backward all",
                Scope.BODY_ONLY));
        parent.add(Node.command("couple", waypoint + ", couple forward 1", Scope.BODY_ONLY));
        parent.add(Node.command("couple all", waypoint + ", couple forward all", Scope.BODY_ONLY));
        parent.add(Node.command("fork curved", waypoint + ", fork <fork> set curved",
                Scope.BODY_ONLY));
        parent.add(Node.command("fork flip", waypoint + ", fork <fork> flip", Scope.BODY_ONLY));
        parent.add(Node.command("arrival hh:mm", waypoint + ", arrival <time>", Scope.BODY_ONLY));
        parent.add(Node.command("arrival hh", waypoint + ", arrival 9", Scope.BODY_ONLY));
        parent.add(
                Node.command("departure hh:mm", waypoint + ", departure <time>", Scope.BODY_ONLY));
        parent.add(Node.command("timed stop", waypoint + ", arrival 9:20, load, departure 9:30",
                Scope.BODY_ONLY));
    }

    private static Node triggersSection() {
        Node triggers = Node.group("TRIGGERS — events");
        triggers.add(triggerGroup("sensor", "sensor", true));
        triggers.add(triggerGroup("station", "station", true));
        triggers.add(triggerGroup("fork", "fork", false));
        triggers.add(triggerGroup("semaphore", "semaphore", false));
        triggers.add(trainTriggers());
        return triggers;
    }

    /** Event triggers for sensor/station (and fork/semaphore, no by-name/forward variants). */
    private static Node triggerGroup(String label, String selector, boolean namedAndSense) {
        Node group = Node.group(label);
        group.add(Node.command("enter", selector + " <" + selector + "> on train enter {\n  \n}",
                Scope.BODY_ONLY));
        group.add(Node.command("exit", selector + " <" + selector + "> on train exit {\n  \n}",
                Scope.BODY_ONLY));
        if (namedAndSense) {
            group.add(Node.command("enter fwd",
                    selector + " <" + selector + "> on train enter forward {\n  \n}",
                    Scope.BODY_ONLY));
            group.add(Node.command("exit bwd",
                    selector + " <" + selector + "> on train exit backward {\n  \n}",
                    Scope.BODY_ONLY));
            group.add(Node.command("enter by name",
                    selector + " \"<name>\" on train enter {\n  \n}", Scope.BODY_ONLY));
        }
        return group;
    }

    private static Node trainTriggers() {
        Node train = Node.group("train");
        train.add(Node.command("enter", "train <train> on enter {\n  \n}", Scope.BODY_ONLY));
        train.add(Node.command("exit", "train <train> on exit {\n  \n}", Scope.BODY_ONLY));
        train.add(Node.command("crash", "train <train> on crash {\n  \n}", Scope.BODY_ONLY));
        train.add(Node.command("contact", "train <train> on contact {\n  \n}", Scope.BODY_ONLY));
        train.add(Node.command("crash fwd", "train <train> on crash forward {\n  \n}",
                Scope.BODY_ONLY));
        train.add(Node.command("contact bwd", "train <train> on contact backward {\n  \n}",
                Scope.BODY_ONLY));
        return train;
    }

    private static Node timeTriggersSection() {
        Node time = Node.group("TIME TRIGGERS").setExpanded(true);
        time.add(Node.command("at <time>", "at <time> {\n  semaphore <semaphore> set open;\n}",
                Scope.BODY_ONLY));
        time.add(Node.command("every 30m", "every 30m {\n  semaphore <semaphore> toggle;\n}",
                Scope.BODY_ONLY));
        time.add(Node.command("every 2h", "every 2h {\n  train at station <station> load;\n}",
                Scope.BODY_ONLY));
        time.add(Node.command("every 1d", "every 1d {\n  train <train> set engine on;\n}",
                Scope.BODY_ONLY));
        time.add(Node.command("every 30m from 6:00",
                "every 30m from 6:00 {\n  semaphore <semaphore> invert;\n}", Scope.BODY_ONLY));
        time.add(Node.info("time trigger rules",
                "`at` fires daily; `every` on a fixed grid; max 64; no catch-up after load."));
        return time;
    }

    private static Node insideBlocksSection() {
        Node blocks = Node.group("INSIDE { } BLOCKS").setExpanded(true);
        blocks.add(trainActions());
        blocks.add(Node.info("couple/uncouple",
                "Without a count, couple/uncouple act on every vehicle on that side."));
        blocks.add(trainAtActions());
        blocks.add(forkActions());
        blocks.add(semaphoreActions());
        blocks.add(Node.info("open/close", "`open`/`close` are also accepted inside blocks."));
        blocks.add(Node.info("top-level actions",
                "Signal, station and sensor actions are top-level only (next section)."));
        return blocks;
    }

    private static Node trainActions() {
        Node train = Node.group("train").setExpanded(true);
        train.add(Node.command("set speed", "train <train> set speed <speed>;", Scope.BOTH));
        train.add(Node.command("accelerate", "train <train> accelerate;", Scope.BOTH));
        train.add(Node.command("decelerate", "train <train> decelerate;", Scope.BOTH));
        train.add(Node.command("stop", "train <train> stop;", Scope.BOTH));
        train.add(Node.command("park", "train <train> park;", Scope.BOTH));
        train.add(Node.command("stop at station",
                "train <train> stop at station <station> speed <speed>;", Scope.BOTH));
        train.add(Node.command("stop at sensor",
                "train <train> stop at sensor <sensor> speed <speed>;", Scope.BOTH));
        train.add(Node.command("stop at end", "train <train> stop at end speed <speed>;",
                Scope.BOTH));
        train.add(Node.command("stop when blocked",
                "train <train> stop when blocked speed <speed>;", Scope.BOTH));
        train.add(Node.command("stop on contact", "train <train> stop on contact speed <speed>;",
                Scope.BOTH));
        train.add(Node.command("invert", "train <train> invert;", Scope.BOTH));
        train.add(Node.command("reverse", "train <train> reverse;", Scope.BOTH));
        train.add(Node.command("set forward", "train <train> set forward;", Scope.BOTH));
        train.add(Node.command("set backward", "train <train> set backward;", Scope.BOTH));
        train.add(Node.command("engine on", "train <train> set engine on;", Scope.BOTH));
        train.add(Node.command("engine off", "train <train> set engine off;", Scope.BOTH));
        train.add(Node.command("load", "train <train> load;", Scope.BOTH));
        train.add(Node.command("unload", "train <train> unload;", Scope.BOTH));
        train.add(Node.command("couple forward 1", "train <train> couple forward 1;", Scope.BOTH));
        train.add(Node.command("couple forward all", "train <train> couple forward all;",
                Scope.BOTH));
        train.add(Node.command("uncouple backward 1", "train <train> uncouple backward 1;",
                Scope.BOTH));
        train.add(Node.command("uncouple backward all", "train <train> uncouple backward all;",
                Scope.BOTH));
        train.add(Node.command("set name", "train <train> set name \"<name>\";", Scope.BOTH));
        return train;
    }

    private static Node trainAtActions() {
        Node trainAt = Node.group("train at");
        trainAt.add(Node.command("station", "train at station <station> stop;", Scope.BLOCK_ONLY));
        trainAt.add(Node.command("sensor", "train at sensor <sensor> load;", Scope.BLOCK_ONLY));
        trainAt.add(Node.command("station by name", "train at station \"<name>\" stop;",
                Scope.BLOCK_ONLY));
        trainAt.add(Node.command("sensor by name", "train at sensor \"<name>\" load;",
                Scope.BLOCK_ONLY));
        trainAt.add(Node.command("fork", "train at fork <fork> stop;", Scope.BLOCK_ONLY));
        trainAt.add(Node.command("semaphore", "train at semaphore <semaphore> stop;",
                Scope.BLOCK_ONLY));
        return trainAt;
    }

    private static Node forkActions() {
        Node fork = Node.group("fork");
        fork.add(Node.command("set straight", "fork <fork> set straight;", Scope.BOTH));
        fork.add(Node.command("set curved", "fork <fork> set curved;", Scope.BOTH));
        fork.add(Node.command("set flip", "fork <fork> set flip;", Scope.BOTH));
        fork.add(Node.command("flip", "fork <fork> flip;", Scope.BOTH));
        fork.add(Node.command("set <dir>", "fork <fork> set <dir>;", Scope.BOTH));
        return fork;
    }

    private static Node semaphoreActions() {
        Node semaphore = Node.group("semaphore");
        semaphore.add(Node.command("open", "semaphore <semaphore> set open;", Scope.BOTH));
        semaphore.add(Node.command("closed", "semaphore <semaphore> set closed;", Scope.BOTH));
        semaphore.add(Node.command("toggle", "semaphore <semaphore> toggle;", Scope.BOTH));
        semaphore.add(Node.command("invert", "semaphore <semaphore> invert;", Scope.BOTH));
        return semaphore;
    }

    private static Node topLevelSection() {
        Node top = Node.group("TOP-LEVEL ONLY").setExpanded(true);
        Node signal = Node.group("signal");
        signal.add(Node.command("limit", "signal <signal> set limit <limit>;", Scope.BODY_ONLY));
        signal.add(Node.command("mode max", "signal <signal> set mode max;", Scope.BODY_ONLY));
        signal.add(Node.command("mode min", "signal <signal> set mode min;", Scope.BODY_ONLY));
        signal.add(Node.command("invert", "signal <signal> invert;", Scope.BODY_ONLY));
        top.add(signal);
        top.add(Node.command("station invert", "station <station> invert;", Scope.BODY_ONLY));
        top.add(Node.command("sensor invert", "sensor <sensor> invert;", Scope.BODY_ONLY));
        top.add(Node.info("direct commands",
                "Direct commands: they do not work inside a trigger or at/every block. "
                        + "Train/fork/semaphore actions above also work here."));
        return top;
    }

    /**
     * Scenario settings generated from the catalogue ({@code key=value} lines), grouped by category
     * with {@code price.*} / {@code cargo.*} subgroups. Every catalogue key except {@code seed}
     * appears once, using its default as the example value (issue #782).
     */
    private static List<Node> configTree() {
        List<Node> root = new ArrayList<>();
        root.add(Node.heading("CONFIGURATION"));

        for (SettingCategory category : SettingCategory.values()) {
            Node categoryNode = Node.group(category.displayName()).setExpanded(true);
            Node prices = Node.group("prices");
            Node cargo = Node.group("cargo values");
            for (SettingDefinition definition : SettingsCatalog.definitions()) {
                if (definition.category() != category || "seed".equals(definition.key())) {
                    continue;
                }
                Node leaf = Node.command(definition.displayName(),
                        definition.key() + "=" + definition.defaultValue(), Scope.BODY_ONLY);
                if (definition.key().startsWith("price.")) {
                    prices.add(leaf);
                } else if (definition.key().startsWith("cargo.")) {
                    cargo.add(leaf);
                } else {
                    categoryNode.add(leaf);
                }
            }
            if (!prices.children.isEmpty()) {
                categoryNode.add(prices);
            }
            if (!cargo.children.isEmpty()) {
                categoryNode.add(cargo);
            }
            root.add(categoryNode);
        }

        return root;
    }

    /** Console help for everything, grouped by section. */
    public static String helpText() {
        return helpText(null);
    }

    /**
     * Console help for {@code topic}: a section name ({@code console}, {@code build},
     * {@code program}, {@code config}) or a single command (e.g. {@code ls}, {@code fork}). Returns
     * a hint when the topic is unknown.
     */
    public static String helpText(String topic) {
        List<Node> tree;
        if (topic == null || topic.isBlank()) {
            tree = getReferenceTree();
        } else {
            tree = treeForTopic(topic.trim().toLowerCase(java.util.Locale.ROOT));
            if (tree == null) {
                return "No help for '" + topic.trim()
                        + "'. Try: console, build, program, config (or a command like 'ls').";
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Node node : tree) {
            renderHelp(node, sb, "");
        }
        return sb.toString().stripTrailing();
    }

    private static List<Node> treeForTopic(String topic) {
        switch (topic) {
            case "console":
            case "app":
                return getReferenceTree(Group.CONSOLE);
            case "build":
            case "on build":
            case "on start":
                return getReferenceTree(Group.BUILD);
            case "program":
            case "script":
                return getReferenceTree(Group.PROGRAM);
            case "config":
            case "configuration":
            case "settings":
                return getReferenceTree(Group.CONFIG);
            default:
                break;
        }
        Node match = findCommand(topic);
        return match == null ? null : List.of(match);
    }

    private static Node findCommand(String topic) {
        for (Group group : Group.values()) {
            Node found = findCommand(getReferenceTree(group), topic);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Node findCommand(List<Node> nodes, String topic) {
        for (Node node : nodes) {
            if (node.kind == Kind.COMMAND && node.snippet != null && node.children.isEmpty()
                    && (firstWord(node.snippet).equals(topic)
                            || node.label.equalsIgnoreCase(topic))) {
                return node;
            }
            Node child = findCommand(node.children, topic);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private static String firstWord(String snippet) {
        int i = 0;
        while (i < snippet.length() && !Character.isWhitespace(snippet.charAt(i))
                && snippet.charAt(i) != ';') {
            i++;
        }
        return snippet.substring(0, i).toLowerCase(java.util.Locale.ROOT);
    }

    private static void renderHelp(Node node, StringBuilder sb, String indent) {
        switch (node.kind) {
            case HEADING -> {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(node.label).append('\n');
            }
            case INFO -> sb.append("  ").append(indent).append("// ").append(oneLine(node.info))
                    .append('\n');
            case COMMAND -> {
                sb.append("  ").append(indent).append(oneLine(node.snippet));
                if (node.label != null && !node.label.isBlank()) {
                    sb.append("  // ").append(node.label);
                }
                sb.append('\n');
            }
            case GROUP -> {
                sb.append("  ").append(indent).append(node.label).append('\n');
                for (Node child : node.children) {
                    renderHelp(child, sb, indent + "  ");
                }
            }
        }
    }

    /** Collapses a (possibly multi-line) snippet into a single line for help output. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s*\\R\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    public static List<String[]> getFlatReferenceList() {
        List<String[]> flat = new ArrayList<>();
        for (Node node : getReferenceTree()) {
            if (node.kind == Kind.HEADING) {
                if (!flat.isEmpty()) {
                    flat.add(new String[] {"", ""});
                }
                flat.add(new String[] {node.label, ""});
            } else {
                flatten(node, flat, "  ");
            }
        }
        return flat;
    }

    private static void flatten(Node node, List<String[]> list, String indent) {
        switch (node.kind) {
            case HEADING -> {
                if (!list.isEmpty()) {
                    list.add(new String[] {"", ""});
                }
                list.add(new String[] {node.label, ""});
            }
            case INFO -> list.add(new String[] {indent + node.label, ""});
            case COMMAND -> list.add(new String[] {indent + node.label, node.snippet});
            case GROUP -> {
                list.add(new String[] {indent + node.label, ""});
                for (Node child : node.children) {
                    flatten(child, list, indent + "  ");
                }
            }
        }
    }
}
