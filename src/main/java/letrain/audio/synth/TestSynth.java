package letrain.audio.synth;

import java.net.URL;

public class TestSynth {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting TestSynth Throttle Simulation...");

        // Load real resource
        URL url = TestSynth.class.getResource("/sound/freesound_community-train-17869.wav");
        if (url == null) {
            System.err.println("ERROR: Resource not found! Check classpath.");
            return;
        }
        AudioSample sample = new AudioSample(url);

        TrainSynthesizer synth = new TrainSynthesizer();
        synth.setSample(sample);

        // Check Idle
        System.out.println("--- Idle (Notch 0) ---");
        SpeedNotch n0 = synth.getNotch(0);
        System.out.println("Notch 0 Cruise: " + n0.cruiseSpeed);
        // We can't easily check engine speed instantly because of Validating it
        // requries running the loop or accessing private fields?
        // Actually getLocoEngine() is public.
        System.out.println("Loco Engine Speed: " + synth.getLocoEngine().getSpeed());

        // Notch 1
        System.out.println("--- Notch 1 ---");
        SpeedNotch n1 = synth.getNotch(1);
        System.out.println("Notch 1 Cruise: " + n1.cruiseSpeed);
        synth.setThrottle(1);

        // Simulate time passing for ramp (runRamp uses Timer, which runs on a thread.
        // We need to wait)
        System.out.println("Waiting for ramp...");
        Thread.sleep(3000);
        System.out.println("Loco Engine Speed: " + synth.getLocoEngine().getSpeed());

        // Notch 9
        System.out.println("--- Notch 9 ---");
        SpeedNotch n9 = synth.getNotch(9);
        System.out.println("Notch 9 Cruise: " + n9.cruiseSpeed);
        synth.setThrottle(9);

        System.out.println("Waiting for ramp...");
        Thread.sleep(5000);
        System.out.println("Loco Engine Speed: " + synth.getLocoEngine().getSpeed());

        System.out.println("TestSynth Complete.");
    }
}
