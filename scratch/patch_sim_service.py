with open('core/src/main/java/letrain/mvp/impl/services/SimulationService.java', 'r') as f:
    content = f.read()

old_code = '''                    economyManager.onUnloadCargo(wagon, wagon.getCargoType(), unitsUnloaded,
                            distance);'''
new_code = '''                    economyManager.onUnloadCargo(wagon, prevState.type, unitsUnloaded,
                            distance);'''

content = content.replace(old_code, new_code)

with open('core/src/main/java/letrain/mvp/impl/services/SimulationService.java', 'w') as f:
    f.write(content)
