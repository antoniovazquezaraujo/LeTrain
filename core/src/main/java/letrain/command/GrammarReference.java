package letrain.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 */
public class GrammarReference {

    /** A reference tree / console help section. */
    public enum Group {
        CONSOLE, BUILD, PROGRAM, CONFIG
    }

    public static class Node {
        public String label;
        public String snippet;
        public List<Node> children;
        public boolean expanded;
        public boolean isHeading;

        public Node(String label, String snippet) {
            this.label = label;
            this.snippet = snippet;
            this.children = new ArrayList<>();
        }

        public Node(String label) {
            this(label, null);
        }

        public Node add(Node child) {
            this.children.add(child);
            return this;
        }

        public Node setExpanded(boolean expanded) {
            this.expanded = expanded;
            return this;
        }

        public Node setHeading(boolean heading) {
            this.isHeading = heading;
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
        root.add(new Node("CONSOLE").setHeading(true));

        Node info = new Node("info");
        info.add(new Node("all", "info;"));
        info.add(new Node("by type", "info station;"));
        info.add(new Node("by id/name", "info station 1;"));
        root.add(info);

        Node ls = new Node("ls");
        ls.add(new Node("all", "ls;"));
        ls.add(new Node("by type", "ls station;"));
        root.add(ls);

        root.add(new Node("journal", "journal;"));
        root.add(new Node("undo / redo", "undo; redo;"));

        Node time = new Node("time");
        time.add(new Node("show", "time;"));
        time.add(new Node("set", "time set HH:MM;"));
        root.add(time);
        root.add(new Node("record/edit mode", "R (Shift+R)"));
        root.add(new Node("save / load", "save backup; load backup;"));
        root.add(new Node("export / import", "export my-network; import my-network;"));
        root.add(new Node("quit", "quit;"));

        return root;
    }

    /**
     * Scenario recipe: turtle navigation/construction plus infrastructure (on build + on start).
     */
    private static List<Node> buildTree() {
        List<Node> root = new ArrayList<>();
        root.add(new Node("ON BUILD").setHeading(true));

        Node go = new Node("go");
        go.add(new Node("to coords", "go 0,0;"));
        go.add(new Node("to element", "go station 1;"));
        go.add(new Node("to mark", "go mark home;"));
        go.add(new Node("next/prev", "go next sensor;"));
        root.add(go);

        Node face = new Node("face");
        face.add(new Node("direction", "face e;"));
        face.add(new Node("to element", "face station 1;"));
        root.add(face);

        Node mark = new Node("mark");
        mark.add(new Node("set", "mark home;"));
        root.add(mark);

        Node track = new Node("track");
        track.add(new Node("write n", "write 5;"));
        track.add(new Node("write path", "write 3,r,2,l,4;"));
        track.add(new Node("move path", "move 5;"));
        track.add(new Node("del", "del;"));
        track.add(new Node("clear", "clear;"));
        root.add(track);

        Node elements = new Node("new element").setExpanded(true);
        elements.add(new Node("station", "new st;"));
        elements.add(new Node("sensor", "new sn;"));
        elements.add(new Node("semaphore", "new sm;"));
        elements.add(new Node("signal", "new sg;"));
        elements.add(new Node("del element", "del station 1;"));
        root.add(elements);

        Node ops = new Node("element ops").setExpanded(true);
        ops.add(new Node("invert", "semaphore 1 invert;"));
        ops.add(new Node("fork branch", "fork 1 set curved;"));
        ops.add(new Node("signal limit", "signal 1 set limit 40;"));
        ops.add(new Node("signal mode", "signal 1 set mode max;"));
        ops.add(new Node("slide", "slide station 1 fw 2;"));
        ops.add(new Node("set name", "station 1 set name \"Central\";"));
        root.add(ops);

        Node trains = new Node("trains");
        trains.add(new Node("new loco", "new loco A red;"));
        trains.add(new Node("new wagon", "new wagon b coal;"));
        trains.add(new Node("clear train", "clear train 1;"));
        root.add(trains);

        root.add(new Node("ON START").setHeading(true));
        Node start = new Node("initial state").setExpanded(true);
        start.add(new Node("semaphore", "semaphore 1 set closed;"));
        start.add(new Node("fork", "fork 1 set curved;"));
        start.add(new Node("train speed", "train 1 set speed 3;"));
        start.add(new Node("train engine", "train 1 set engine on;"));
        root.add(start);

        return root;
    }

    /** Operator program: itineraries, triggers and actions. */
    private static List<Node> programTree() {
        List<Node> root = new ArrayList<>();

        Node itinHeading = new Node("ITINERARY DSL").setHeading(true);
        root.add(itinHeading);

        Node itin = new Node("create itinerary");
        itin.add(new Node("template", "create itinerary \"\" {\n  add station #\n}"));
        itin.add(new Node("block terminator", "} ends the block; a trailing ; is optional"));
        itin.add(new Node("waypoint rules",
                "waypoints: commas between actions are mandatory; order: arrival, actions, "
                        + "departure"));
        itin.setExpanded(true);
        root.add(itin);

        Node addSt = new Node("add station [cmd]");
        addSt.add(new Node("load", "add station # load"));
        addSt.add(new Node("unload", "add station # unload"));
        addSt.add(new Node("reverse", "add station # reverse"));
        addSt.add(new Node("stop", "add station # stop"));
        addSt.add(new Node("wait n", "add station # wait #"));
        addSt.add(new Node("speed n", "add station # speed #"));
        addSt.add(new Node("arrival hh:mm", "add station # arrival 9:20"));
        addSt.add(new Node("departure hh:mm", "add station # departure 9:30"));
        addSt.add(new Node("timed stop", "add station # arrival 9:20, load, departure 9:30"));
        root.add(addSt);

        Node addSe = new Node("add sensor [cmd]");
        addSe.add(new Node("load", "add sensor # load"));
        addSe.add(new Node("unload", "add sensor # unload"));
        addSe.add(new Node("wait n", "add sensor # wait #"));
        addSe.add(new Node("arrival hh:mm", "add sensor # arrival 10:37"));
        addSe.add(new Node("departure hh:mm", "add sensor # departure 10:40"));
        root.add(addSe);

        root.add(new Node("assign itinerary", "assign itinerary \"\" to train #;"));
        root.add(new Node("set autopilot", "train # set autopilot true;"));

        // TRIGGERS
        Node trigHeading = new Node("TRIGGERS").setHeading(true);
        root.add(trigHeading);

        Node sensor = new Node("sensor");
        Node snOn = new Node("on train").setExpanded(true);
        snOn.add(new Node("enter", "sensor # on train enter {\n  \n}"));
        snOn.add(new Node("exit", "sensor # on train exit {\n  \n}"));
        snOn.add(new Node("enter fwd", "sensor # on train enter forward {\n  \n}"));
        snOn.add(new Node("exit bwd", "sensor # on train exit backward {\n  \n}"));
        sensor.add(snOn);
        root.add(sensor);

        Node station = new Node("station");
        Node stOn = new Node("on train").setExpanded(true);
        stOn.add(new Node("enter", "station # on train enter {\n  \n}"));
        stOn.add(new Node("exit", "station # on train exit {\n  \n}"));
        stOn.add(new Node("enter fwd", "station # on train enter forward {\n  \n}"));
        stOn.add(new Node("exit bwd", "station # on train exit backward {\n  \n}"));
        station.add(stOn);
        root.add(station);

        Node fork = new Node("fork");
        Node fkOn = new Node("on train");
        fkOn.add(new Node("enter", "fork # on train enter {\n  \n}"));
        fkOn.add(new Node("exit", "fork # on train exit {\n  \n}"));
        fork.add(fkOn);
        root.add(fork);

        Node semaphore = new Node("semaphore");
        Node smOn = new Node("on train");
        smOn.add(new Node("enter", "semaphore # on train enter {\n  \n}"));
        smOn.add(new Node("exit", "semaphore # on train exit {\n  \n}"));
        semaphore.add(smOn);
        root.add(semaphore);

        Node train = new Node("train");
        Node trOn = new Node("on").setExpanded(true);
        trOn.add(new Node("enter", "train # on enter {\n  \n}"));
        trOn.add(new Node("exit", "train # on exit {\n  \n}"));
        trOn.add(new Node("link", "train # on link {\n  \n}"));
        trOn.add(new Node("unlink", "train # on unlink {\n  \n}"));
        trOn.add(new Node("crash", "train # on crash {\n  \n}"));
        trOn.add(new Node("contact", "train # on contact {\n  \n}"));
        trOn.add(new Node("crash fwd", "train # on crash forward {\n  \n}"));
        trOn.add(new Node("contact bwd", "train # on contact backward {\n  \n}"));
        train.add(trOn);
        root.add(train);

        // ACTIONS
        Node actHeading = new Node("ACTIONS").setHeading(true);
        root.add(actHeading);

        Node trainAct = new Node("train").setExpanded(true);
        trainAct.add(new Node("set speed", "train # set speed #;"));
        trainAct.add(new Node("accelerate", "train # accelerate;"));
        trainAct.add(new Node("decelerate", "train # decelerate;"));
        trainAct.add(new Node("stop", "train # stop;"));
        trainAct.add(new Node("invert", "train # invert;"));
        trainAct.add(new Node("set forward", "train # set forward;"));
        trainAct.add(new Node("set backward", "train # set backward;"));
        trainAct.add(new Node("engine", "train # set engine on;"));
        trainAct.add(new Node("load", "train # load;"));
        trainAct.add(new Node("unload", "train # unload;"));
        trainAct.add(new Node("link", "train # link forward #;"));
        trainAct.add(new Node("unlink", "train # unlink backward #;"));
        root.add(trainAct);

        Node trainAt = new Node("train at");
        trainAt.add(new Node("station", "train at station # stop;"));
        trainAt.add(new Node("sensor", "train at sensor # stop;"));
        trainAt.add(new Node("fork", "train at fork # stop;"));
        trainAt.add(new Node("semaphore", "train at semaphore # stop;"));
        root.add(trainAt);

        Node forkAct = new Node("fork").setExpanded(true);
        forkAct.add(new Node("straight", "fork # set straight;"));
        forkAct.add(new Node("curved", "fork # set curved;"));
        forkAct.add(new Node("flip", "fork # set flip;"));
        forkAct.add(new Node("dir...", "fork # set e;"));
        root.add(forkAct);

        Node semAct = new Node("semaphore").setExpanded(true);
        semAct.add(new Node("open", "semaphore # set open;"));
        semAct.add(new Node("closed", "semaphore # set closed;"));
        semAct.add(new Node("invert", "semaphore # invert;"));
        root.add(semAct);

        Node signalAct = new Node("signal").setExpanded(true);
        signalAct.add(new Node("limit", "signal # set limit #;"));
        signalAct.add(new Node("mode max", "signal # set mode max;"));
        signalAct.add(new Node("mode min", "signal # set mode min;"));
        signalAct.add(new Node("invert", "signal # invert;"));
        root.add(signalAct);

        Node stationAct = new Node("station").setExpanded(true);
        stationAct.add(new Node("invert", "station # invert;"));
        root.add(stationAct);

        Node sensorAct = new Node("sensor").setExpanded(true);
        sensorAct.add(new Node("invert", "sensor # invert;"));
        root.add(sensorAct);

        // SET NAMES
        Node namesHeading = new Node("SET NAMES").setHeading(true);
        root.add(namesHeading);
        root.add(new Node("station", "station # set name \"\";"));
        root.add(new Node("sensor", "sensor # set name \"\";"));
        root.add(new Node("train", "train # set name \"\";"));

        return root;
    }

    /** Scenario settings: the {@code letrain.cfg} keys, written as {@code key=value}. */
    private static List<Node> configTree() {
        List<Node> root = new ArrayList<>();
        root.add(new Node("CONFIGURATION").setHeading(true));

        Node economy = new Node("economy").setExpanded(true);
        economy.add(new Node("startingBalance", "startingBalance=0"));
        economy.add(new Node("fuelCostPerMeter", "fuelCostPerMeter=0.5"));
        economy.add(new Node("cargoLoadingFee", "cargoLoadingFee=100"));
        root.add(economy);

        Node prices = new Node("prices");
        prices.add(new Node("track", "price.CONSTRUCTED_NORMAL_RAIL_TRACK=5"));
        prices.add(new Node("bridge", "price.CONSTRUCTED_BRIDGE_RAIL_TRACK=20"));
        prices.add(new Node("tunnel", "price.CONSTRUCTED_TUNNEL_RAIL_TRACK=25"));
        prices.add(new Node("fork", "price.CONSTRUCTED_FORK=15"));
        prices.add(new Node("station", "price.CONSTRUCTED_STATION=50"));
        prices.add(new Node("sensor", "price.CONSTRUCTED_SENSOR=20"));
        prices.add(new Node("semaphore", "price.CONSTRUCTED_SEMAPHORE=20"));
        prices.add(new Node("locomotive", "price.CONSTRUCTED_LOCOMOTIVE=100"));
        prices.add(new Node("wagon", "price.CONSTRUCTED_WAGON=40"));
        root.add(prices);

        Node cargo = new Node("cargo values");
        cargo.add(new Node("coal", "cargo.COAL=10"));
        cargo.add(new Node("gold", "cargo.GOLD=100"));
        cargo.add(new Node("ruby", "cargo.RUBY=250"));
        root.add(cargo);

        Node thresholds = new Node("resource thresholds").setExpanded(true);
        thresholds.add(new Node("gold", "threshold.GOLD=0.30"));
        thresholds.add(new Node("coal", "threshold.COAL=0.25"));
        thresholds.add(new Node("ruby", "threshold.RUBY=0.35"));
        thresholds.add(new Node("water", "threshold.WATER=130"));
        thresholds.add(new Node("rock", "threshold.ROCK=180"));
        root.add(thresholds);

        Node map = new Node("map");
        map.add(new Node("view radius", "map.VIEW_RADIUS=15"));
        root.add(map);

        Node delays = new Node("construction delays");
        delays.add(new Node("normal", "delay.NORMAL_TRACK=0"));
        delays.add(new Node("bridge", "delay.BRIDGE_TRACK=20"));
        delays.add(new Node("bridge gate", "delay.BRIDGE_GATE_TRACK=20"));
        delays.add(new Node("tunnel", "delay.TUNNEL_TRACK=30"));
        delays.add(new Node("tunnel gate", "delay.TUNNEL_GATE_TRACK=30"));
        root.add(delays);

        Node derail = new Node("derailment");
        derail.add(new Node("min curve interval", "derail.minCurveInterval=12"));
        derail.add(new Node("min speed", "derail.minSpeed=3"));
        root.add(derail);

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
            if (!node.isHeading && node.snippet != null && node.children.isEmpty()
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
        if (node.isHeading) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(node.label).append('\n');
        } else if (node.snippet != null && node.children.isEmpty()) {
            sb.append("  ").append(indent).append(oneLine(node.snippet));
            if (node.label != null && !node.label.isBlank()) {
                sb.append("  // ").append(node.label);
            }
            sb.append('\n');
        } else {
            sb.append("  ").append(indent).append(node.label).append('\n');
            for (Node child : node.children) {
                renderHelp(child, sb, indent + "  ");
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
            if (node.isHeading) {
                if (!flat.isEmpty()) {
                    flat.add(new String[] {"", ""});
                }
                flat.add(new String[] {node.label, ""});
            } else if (node.snippet != null && node.children.isEmpty()) {
                flat.add(new String[] {"  " + node.label, node.snippet});
            } else {
                flatten(node, flat, "  ");
            }
        }
        return flat;
    }

    private static void flatten(Node node, List<String[]> list, String indent) {
        if (node.snippet != null && node.children.isEmpty()) {
            list.add(new String[] {indent + node.label, node.snippet});
            return;
        }
        for (Node child : node.children) {
            if (child.snippet != null) {
                list.add(new String[] {indent + node.label + " " + child.label, child.snippet});
            } else {
                flatten(child, list, indent + node.label + " ");
            }
        }
    }
}
