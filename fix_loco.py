import re

with open("src/main/java/letrain/vehicle/rail/impl/Locomotive.java", "r") as f:
    content = f.read()

new_methods = """    public void incSpeed() {
        // Do not increase speed while stalled from a collision.
        if (getTrain() != null && getTrain().isStalled()) {
            return;
        }
        // Speed change in auto mode → switch to manual
        if (getTrain() != null && getTrain().isAutoMode()) {
            getTrain().setAutoMode(false);
        }
        if (getTrain() != null) {
            getTrain().setSpeed(this.targetSpeed + 1);
        } else {
            setTargetSpeed(this.targetSpeed + 1);
        }
    }

    public void decSpeed() {
        // Speed change in auto mode → switch to manual
        if (getTrain() != null && getTrain().isAutoMode()) {
            getTrain().setAutoMode(false);
        }
        if (getTrain() != null) {
            getTrain().setSpeed(this.targetSpeed - 1);
        } else {
            setTargetSpeed(this.targetSpeed - 1);
        }
    }"""

content = re.sub(r'    public void incSpeed\(\) \{.*?    \}', new_methods, content, flags=re.DOTALL)

# Because we replaced BOTH in one go (wait, the regex matches until the NEXT `}`, which might break things if I'm not careful. Let me just replace the exact string.
