# Implementation Plan - Fix Train Speed 0 On Station Enter

We will fix the issue where setting speed to 0 (e.g. `train set speed 0`) on entering a station results in the train staying at speed 1 or 2 instead of stopping completely.

## Proposed Changes

### [MODIFY] [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Locomotive.java)

1. **Immediate Stop on Target Speed 0**:
   In `setTargetSpeed(int speed)`, if the new target speed is `0`, we will immediately set `currentSpeed = 0` using `setCurrentSpeed(0)`. This bypasses the rail-based inertia deceleration which takes too long (148 ticks) and causes the train to overshoot the 1-rail-long station.

2. **Acoustic Signal Race Condition Protection**:
   In `update()`, we will guard the blocks where `acousticSpeedSignal` is applied to `currentSpeed` with a check that `targetSpeed > 0`. This ensures that lagging asynchronous speed updates from the audio sync thread during ramp-down (e.g., notch 2, 1) do not jumpstart a train that has been intentionally stopped (targetSpeed == 0).

## Verification Plan

### Automated Tests
- Run `mvn clean test -Dtest=AutoPilotIntegrationTest#madridToBarcelonaSpeed0OnEnter` to verify that the specific test passes.
- Run `mvn clean test` to confirm all other tests continue to pass.
