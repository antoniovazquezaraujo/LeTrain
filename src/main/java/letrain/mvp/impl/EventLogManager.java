package letrain.mvp.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class EventLogManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int MAX_ENTRIES = 100;
    private final List<String> entries = new ArrayList<>();

    public synchronized void addEntry(String entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public synchronized List<String> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
