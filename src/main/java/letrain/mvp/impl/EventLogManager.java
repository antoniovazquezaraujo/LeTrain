package letrain.mvp.impl;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class EventLogManager {
    public EventLogManager() {
    }
    private final int MAX_ENTRIES = 100;
    private List<String> entries = new ArrayList<>();

    public synchronized void addEntry(String entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public synchronized List<String> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void setEntries(List<String> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
