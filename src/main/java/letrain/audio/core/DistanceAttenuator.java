package letrain.audio.core;

public class DistanceAttenuator {

    public static float calculateVolume(float distance, float refDistance, float maxDistance) {
        if (distance <= refDistance)
            return 1.0f;
        if (distance >= maxDistance)
            return 0.0f;

        // Linear rolloff
        return 1.0f - ((distance - refDistance) / (maxDistance - refDistance));
    }

    public static float[] calculateStereoPan(float listenerX, float listenerY, float listenerAngle, float sourceX,
            float sourceY) {
        // Returns [LeftVolume, RightVolume]
        float dx = sourceX - listenerX;
        float dy = sourceY - listenerY;

        // Angle of source relative to world
        double angleToSource = Math.atan2(dy, dx);

        // Angle relative to listener
        double angleRelative = angleToSource - listenerAngle;

        // Normalize to -PI to PI
        while (angleRelative > Math.PI)
            angleRelative -= 2 * Math.PI;
        while (angleRelative < -Math.PI)
            angleRelative += 2 * Math.PI;

        // Simple pan: when angle is -PI/2 (Left), Left=1, Right=0.
        // When 0 (Ahead), Left=1, Right=1. (Or 0.707 for constant power)

        float pan = (float) Math.sin(angleRelative); // -1 (Left) to 1 (Right)

        // Constant power panning approx
        float left = (float) ((Math.sqrt(2) / 2.0) * (Math.cos(angleRelative) + Math.sin(angleRelative))); // This is
                                                                                                           // wrong
                                                                                                           // formula
        // Let's use simple linear for now

        float p = (pan + 1.0f) / 2.0f; // 0 (Left) to 1 (Right)
        return new float[] { 1.0f - p, p };
    }
}
