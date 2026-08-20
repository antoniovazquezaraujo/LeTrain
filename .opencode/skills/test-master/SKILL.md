name: test-master
description: Skill de maestría en testing (JUnit 5, Mockito). Úsala para asegurar pruebas unitarias robustas, mocks precisos y alta cobertura de código con un enfoque en BDD/TDD.

# Test Master

Esta skill proporciona conocimiento experto sobre:

1. **JUnit 5**: Uso avanzado de anotaciones, ciclos de vida de tests (`@BeforeEach`, `@AfterEach`, `@Nested`) y pruebas parametrizadas.
2. **Mockito**: Dominio de `mock()`, `spy()`, `verify()`, `ArgumentCaptor` y `when().thenReturn()`.
3. **Estrategia de Testing**: TDD (Test Driven Development) y BDD (Behavior Driven Development).

## Cómo utilizar esta Skill

### 1. Definición de Tests
Usa [STRATEGY.md](references/strategy.md) para determinar si una clase necesita tests unitarios, de integración o tests de estado (muy comunes en `LeTrain` para el mapa y las rutas).

### 2. Implementación con Mockito
Consulta [MOCKITO_PATTERNS.md](references/mockito-patterns.md) para evitar problemas comunes como *stubbing* excesivo o *brittle tests*.

### 3. Checklist de Calidad de Testing
Antes de validar un cambio, Bicho debe responder:
- ¿Es el test independiente y determinista?
- ¿Cubre casos borde (ej. rutas nulas, desconexión de vías, economía cero)?
- ¿Se están usando los *verify* adecuados en lugar de solo probar el estado final?
