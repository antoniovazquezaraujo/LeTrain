# 🚀 Proceso de Release y Despliegue (LeTrain)

Este documento describe los procesos automatizados que tiene configurados el repositorio para la publicación de la página web y de los ejecutables del juego.

---

## 1. Publicación de la Página Web (Docsify)

La documentación pública que ven los jugadores (`docs/user/`) está configurada para publicarse sola gracias a GitHub Actions (`deploy-pages.yml`).

**¿Qué tienes que hacer?**
¡Absolutamente nada! Cada vez que modifiques cualquier archivo dentro de la carpeta `docs/user/` y subas tus cambios a la rama `develop` (`git push origin develop`), GitHub lanzará un proceso de fondo. 
Este proceso copiará automáticamente tu documentación en la rama `gh-pages` y la web pública se actualizará en cuestión de segundos sin que muevas un dedo.

---

## 2. Publicación de Ejecutables (Windows y Linux)

Dado que `jpackage` (la herramienta de empaquetado de Java) solo puede crear binarios para el sistema operativo en el que se ejecuta, delegamos esta tarea a GitHub Actions (`release.yml`). GitHub arrancará ordenadores con Windows y Ubuntu en la nube para compilar y empaquetar el juego simultáneamente de forma nativa.

### Pasos para sacar una nueva versión:

Cuando el juego esté estable y quieras publicar una nueva actualización (por ejemplo, la `v1.0.0`), abre tu terminal y ejecuta estos **3 únicos pasos**:

**Paso 1: Asegúrate de que tu código está subido**
```bash
git push origin develop
```

**Paso 2: Crea la etiqueta (tag) con el número de versión**
*(OJO: Es obligatorio que el nombre empiece por una `v` minúscula para que funcione)*
```bash
git tag v1.0.0
```

**Paso 3: Sube la etiqueta a GitHub para despertar a los robots**
```bash
git push origin v1.0.0
```

### ¿Qué ocurre entonces?
En el momento en que ejecutes el Paso 3, ve a la pestaña de **Actions** o **Releases** de tu repositorio en GitHub y verás la magia:
1. GitHub creará una nueva Release oficial.
2. Compilará el código de forma paralela en Windows y Linux usando `mvn package -DskipTests`.
3. Comprimirá el resultado en `LeTrain-Windows.zip` y `LeTrain-Linux.zip`.
4. Adjuntará esos dos archivos a la Release para que la gente los descargue con un clic.
