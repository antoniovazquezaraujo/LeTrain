package letrain.audio.core;

public class DistanceAttenuator {

    public static float calculateVolume(float distance, float refDistance, float maxDistance) {
        if (distance <= refDistance) {
            return 1.0f;
        }
        if (distance >= maxDistance) {
            return 0.0f;
        }

        // Linear rolloff
        return 1.0f - ((distance - refDistance) / (maxDistance - refDistance));
    }

    public static float[] calculateStereoPan(
            float listenerX, float listenerY, float listenerAngle, float sourceX, float sourceY) {
        // Returns [LeftVolume, RightVolume]
        float dx = sourceX - listenerX;
        float dy = sourceY - listenerY;

        // Angle of source relative to world
        double angleToSource = Math.atan2(dy, dx);

        // Angle relative to listener
        double angleRelative = angleToSource - listenerAngle;

        // Normalize to -PI to PI
        while (angleRelative > Math.PI) angleRelative -= 2 * Math.PI;
        while (angleRelative < -Math.PI) angleRelative += 2 * Math.PI;

        // Simple linear pan: -PI/2 (left) → pan=-1, 0 (ahead) → pan=0, +PI/2 (right) → pan=+1
        float pan = (float) Math.sin(angleRelative);

        // Map pan [-1, 1] to [0, 1] for left/right volume
        float p = (pan + 1.0f) / 2.0f;
        return new float[] {1.0f - p, p};
    }
}
