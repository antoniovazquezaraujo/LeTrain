package letrain.audio.synth;

public class SpeedNotch {
    public String name;
    public float startSpeed; // Entry speed from lower gear
    public float cruiseSpeed; // Steady state speed
    public float endSpeed; // Exit speed to higher gear

    public float loopStart, loopEnd; // Loco
    public float coachLoopStart, coachLoopEnd; // Coach

    public float rampTime; // Seconds per phase

    public SpeedNotch(String name, float startSpeed, float cruiseSpeed, float endSpeed, float start,
            float end, float coachStart, float coachEnd, float rampTime) {
        this.name = name;
        this.startSpeed = startSpeed;
        this.cruiseSpeed = cruiseSpeed;
        this.endSpeed = endSpeed;
        this.loopStart = start;
        this.loopEnd = end;
        this.coachLoopStart = coachStart;
        this.coachLoopEnd = coachEnd;
        this.rampTime = rampTime;
    }
}
