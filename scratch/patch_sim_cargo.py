with open('core/src/main/java/letrain/mvp/impl/services/SimulationService.java', 'r') as f:
    content = f.read()

old_state = '''    private static class CargoState {
        int amount;

        CargoState(CargoTypes t, int a) {
            amount = a;
        }
    }'''
new_state = '''    private static class CargoState {
        int amount;
        letrain.track.CargoTypes type;

        CargoState(letrain.track.CargoTypes t, int a) {
            type = t;
            amount = a;
        }
    }'''

content = content.replace(old_state, new_state)

with open('core/src/main/java/letrain/mvp/impl/services/SimulationService.java', 'w') as f:
    f.write(content)
