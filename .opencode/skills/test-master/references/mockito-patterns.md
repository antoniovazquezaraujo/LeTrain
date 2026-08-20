# Mockito: Patrones y Antipatrones

## Patrones Recomendados
- **Verificación de Comportamiento**: Priorizar `verify(mock, times(n)).metodo()` para asegurar que las interacciones ocurren como se espera, especialmente en el `CommandManager`.
- **ArgumentCaptor**: Utilizar `ArgumentCaptor<T>` para inspeccionar objetos complejos pasados a métodos, en lugar de equals profundos.
- **`@Mock` vs `mock()`**: Usar `MockitoExtension` y las anotaciones (`@Mock`, `@InjectMocks`) para mantener los tests limpios.

## Antipatrones a Evitar
- **Stubbing excesivo**: No *stubbear* más de lo necesario para el test actual. Si un mock requiere 10 líneas de `when().thenReturn()`, el diseño de la clase bajo test podría estar mal (violación de la Ley de Demeter).
- **Testeo de Mocks**: No verificar mocks que son configuraciones de infraestructura. Testear el resultado de negocio.
- **Brittle Tests**: Evitar tests que fallen si se cambia un detalle irrelevante de la implementación.
- **`any()` indiscriminado**: Evitar usar `any()` si se puede usar un matcher específico (ej. `eq(expectedValue)`), para mayor seguridad.
