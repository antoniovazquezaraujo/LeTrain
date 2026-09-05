# LeTrain - Gramática de Scripting (Automation)

LeTrain incluye su propio analizador léxico/sintáctico (basado en ANTLR4) que te permite automatizar la red ferroviaria usando un lenguaje específico. Los scripts se ejecutan línea a línea.

## ⚙️ Estructura del Lenguaje

El lenguaje admite tres tipos principales de sentencias: comandos directos, creación/asignación de itinerarios (Autopilot) y bloques disparados por eventos (*triggers*).

### 1. Comandos Directos
Se ejecutan inmediatamente. **Requieren punto y coma (`;`) al final**.

**Acciones de Trenes (`trainRef` puede ser número o nombre entre comillas):**
- `train [ID] accelerate;`
- `train [ID] decelerate;`
- `train [ID] set speed [NUM];` o `train [ID] set [NUM];`
- `train [ID] invert;`
- `train [ID] set engine on;` / `train [ID] set engine off;`
- `train [ID] set forward;` / `train [ID] set backward;`
- `train [ID] load;`
- `train [ID] unload;`
- `train [ID] couple forward [NUM];` / `train [ID] uncouple backward;`

**Nombrar Elementos:**
- `station [ID] set name "Mi Estacion";`
- `sensor [ID] set name "Sensor Norte";`
- `train [ID] set name "Mercancias";`

### 2. Autopilot e Itinerarios
Permite programar una lista de destinos (waypoints) para que el tren busque el camino mediante A*.
Los bloques de itinerario usan llaves `{ }` y no requieren `;` al final.

**Crear Itinerario:**
```letrain
create itinerary "RutaCarbon" {
    add station 1 load
    add station 2 reverse unload
    add sensor 5 forward speed 20
}
```
*Acciones de Waypoint permitidas:* `load`, `unload`, `reverse`, `stop`, `wait [NUM]`, `speed [NUM]`. Se puede opcionalmente fijar la dirección de entrada (`forward` / `backward`) y añadir múltiples acciones.

**Asignar y Activar:**
- `assign itinerary "RutaCarbon" to train 1;`
- `train 1 set autopilot true;`

### 3. Automatización por Eventos (Triggers)
Responde a eventos del juego en tiempo real. 

**Estructura Base:**
```letrain
[SELECTOR] on [EVENTO] {
    [ACCION];
    [ACCION];
}
```

**Selectores:**
- `sensor [ID]`, `fork [ID]`, `semaphore [ID]`, `station [ID]`, `train [ID]` (o `train` genérico).

**Eventos:**
- Trenes: `on train enter`, `on train exit`, `on train couple`, `on train uncouple` (opcionalmente con dirección `forward`/`backward`).
- Accidentes: `train 1 on crash`, `train on contact forward`.

**Acciones especiales dentro de bloques (terminan en `;`):**
- *Semáforos:* `semaphore [ID] set open;` / `semaphore [ID] set closed;`
- *Cambios de Aguja (Forks):* `fork [ID] set flip;` / `fork [ID] set straight;` / `fork [ID] set curved;`
- *Tren Condicional:* Puedes usar `train at station [ID]` en lugar de usar un número fijo de tren para aplicar acciones al tren que disparó el evento.

---

### Ejemplo Completo

```letrain
// Nombramos la estación
station 1 set name "Mina Central";

// Creamos la ruta del tren
create itinerary "RutaPrincipal" {
    add station 1 forward load
    add station 2 backward unload
}

// Activamos la ruta
assign itinerary "RutaPrincipal" to train 1;
train 1 set autopilot true;

// Automatizamos el cruce para cualquier tren que pise el sensor
sensor 4 on train enter {
    fork 2 set straight;
    semaphore 1 set open;
}
```

---
<div align="center" style="margin-top: 40px; margin-bottom: 40px;">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
  <img src="https://img.shields.io/badge/LibGDX-Engine-E3363E?style=flat-square&logo=libgdx&logoColor=white" alt="LibGDX">
  <img src="https://img.shields.io/badge/Open_Source-%E2%9D%A4%EF%B8%8F-2EA44F?style=flat-square" alt="Open Source">
  <br><br>
  <strong>The Letter Train Simulator (LeTrain)</strong><br>
  Desarrollado con ☕ por <a href="https://github.com/antoniovazquezaraujo">Antonio Vázquez Araújo</a><br><br>
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/issues">Reportar un Bug</a> &nbsp;|&nbsp; 
  <a href="https://github.com/antoniovazquezaraujo/LeTrain">Código Fuente</a> &nbsp;|&nbsp; 
  <a href="mailto:antoniovazquezaraujo@gmail.com">Contacto (Email)</a>
</div>
