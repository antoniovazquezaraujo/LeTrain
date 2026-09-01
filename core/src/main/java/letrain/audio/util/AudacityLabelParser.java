package letrain.audio.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudacityLabelParser {

    public static class Label {
        public double startTime;
        public double endTime;
        public String name;

        public Label(double start, double end, String name) {
            this.startTime = start;
            this.endTime = end;
            this.name = name;
        }
    }

    public static List<Label> parse(File file) throws IOException {
        return parse(new java.io.FileInputStream(file));
    }

    public static List<Label> parse(java.io.InputStream stream) throws IOException {
        List<Label> labels = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Format: StartTime\tEndTime\tLabelName
                // 1.234567 2.345678 MyLabel
                String[] parts = line.split("\\t"); // Audacity uses tabs
                if (parts.length < 3) {
                    // Try spaces?
                    parts = line.split("\\s+");
                }

                if (parts.length >= 3) {
                    try {
                        double start = Double.parseDouble(parts[0]);
                        double end = Double.parseDouble(parts[1]);
                        // Reassemble name if it had spaces
                        String name = parts[2].trim();
                        if (parts.length > 3) {
                            for (int i = 3; i < parts.length; i++)
                                name += " " + parts[i].trim();
                        }

                        labels.add(new Label(start, end, name.trim()));
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping invalid label line: " + line);
                    }
                }
            }
        }
        return labels;
    }
}
