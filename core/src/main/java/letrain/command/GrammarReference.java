package letrain.command;

import java.util.ArrayList;
import java.util.List;

public class GrammarReference {

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

    private static List<Node> cachedTree = null;

    public static List<Node> getReferenceTree() {
        if (cachedTree != null) {
            return cachedTree;
        }
        List<Node> root = new ArrayList<>();

        Node itinHeading = new Node("ITINERARY DSL").setHeading(true);
        root.add(itinHeading);

        Node itin = new Node("create itinerary");
        itin.add(new Node("template", "create itinerary \"\" {\n  add station #\n}"));
        itin.setExpanded(true);
        root.add(itin);

        Node addSt = new Node("add station [cmd]");
        addSt.add(new Node("load", "add station # load"));
        addSt.add(new Node("unload", "add station # unload"));
        addSt.add(new Node("reverse", "add station # reverse"));
        addSt.add(new Node("stop", "add station # stop"));
        addSt.add(new Node("wait n", "add station # wait #"));
        addSt.add(new Node("speed n", "add station # speed #"));
        root.add(addSt);

        Node addSe = new Node("add sensor [cmd]");
        addSe.add(new Node("load", "add sensor # load"));
        addSe.add(new Node("unload", "add sensor # unload"));
        addSe.add(new Node("wait n", "add sensor # wait #"));
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
        root.add(semAct);

        // SET NAMES
        Node namesHeading = new Node("SET NAMES").setHeading(true);
        root.add(namesHeading);

        root.add(new Node("station", "station # set name \"\";"));
        root.add(new Node("sensor", "sensor # set name \"\";"));
        root.add(new Node("train", "train # set name \"\";"));

        cachedTree = root;
        return root;
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
