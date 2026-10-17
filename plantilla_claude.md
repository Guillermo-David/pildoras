# [Nombre del proyecto] - Contexto del Proyecto

> Plantilla genérica de CLAUDE.md. Rellenar lo que está entre [corchetes]. Los bloques marcados "(opcional)" se borran si no aplican al stack. Mantener el fichero corto: solo lo que Claude necesita para trabajar bien.

## Preferencias de trabajo

- **Minimizar tokens**: solo escribir código correcto y probarlo (compilar/tests). Sin explicaciones, resúmenes largos, repetir contexto ni preguntas innecesarias. Respuesta final: 1-3 líneas máximo (qué se hizo + resultado de la prueba).
- No leer ficheros enteros ni explorar si no hace falta; usar grep/rangos de líneas. No releer lo ya editado.
- No añadir comentarios en el código salvo que el "por qué" sea no obvio.
- No crear ficheros de documentación (.md, README) salvo que se pida explícitamente.
- Seguir el estilo del código circundante (nombres, idioma, densidad de comentarios).
- Probar siempre lo que se cambia antes de darlo por terminado; si algo falla, decirlo tal cual con la salida del error.
- Confirmar antes de acciones difíciles de revertir o que afecten a terceros (borrar, sobrescribir, publicar, force-push).
- **Mantener este CLAUDE.md actualizado SIEMPRE, sin que el usuario lo pida**: el cambio en `CLAUDE.md` va en el mismo commit que el cambio de código, y antes de dar por terminada cualquier tarea hay que revisar que refleja el estado real. Actualizar cuando: se implementa o elimina una funcionalidad, cambia la arquitectura o se mueven ficheros, se añaden dependencias, se descubre una trampa o lección, o se completa/aparece una tarea. No avisar de que se ha actualizado.

## Git

- Rama de trabajo: `[rama]`. Rama principal: `[main/master]`. Autor: [nombre y correo].
- Commit y push a la rama de trabajo al terminar cada cambio grande, sin narrarlo. Mensajes descriptivos (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `security:`).
- No crear PR ni mergear salvo petición explícita. No reescribir historia ajena.
- Antes de continuar un trabajo en una sesión nueva, hacer `git fetch` y `git log` para ver si la rama principal ha avanzado.

## Seguridad - CRÍTICO

- Nunca commitear secretos (tokens, claves, contraseñas, `.env`). Añadirlos a `.gitignore` desde el principio: [`fichero_de_secretos`].
- Todo SQL con valores externos va parametrizado. Si el driver no lo permite, escapar de forma centralizada y con tests. Usar lista blanca para `ORDER BY` y nombres de columna.
- Validar toda entrada externa (ficheros importados, parámetros, red) en el borde del sistema.
- [Si hay credenciales o PIN de usuario: limpiarlos de memoria tras usarlos, derivar con PBKDF2/Argon2, cifrar con AES-GCM, y limitar intentos con espera exponencial y tope máximo.] (opcional)

## Descripción

[Qué es el proyecto, para quién y qué problema resuelve, en 2-3 líneas.]

## Stack técnico

- [Lenguaje / versión]
- [Framework]
- [Comando exacto de compilar, de ejecutar tests y de ejecutar la app]
- [Base de datos / almacenamiento]
- [Librerías clave con versiones fijadas que no deben actualizarse sin probar]

## Arquitectura

```
[estructura de carpetas/paquetes con una línea por elemento]
```

[Cómo se comunican los componentes principales y el flujo de navegación o de datos.]

## Patrones y convenciones

- Una sola forma de hacer cada cosa común (textos de UI, diálogos, notificaciones, configuración, logging): anotar aquí cuál es.
- Si una lógica aparece en dos sitios, extraerla antes de copiarla una tercera vez.
- Más de ~5 parámetros en un constructor: usar un record/objeto de configuración.
- Ficheros de más de ~800 líneas: extraer piezas con responsabilidad propia.
- Un handler por familia de acciones (por ejemplo, uno con un identificador) en vez de decenas de métodos que solo delegan.
- [Convenciones específicas: idioma de los textos, nombres, estilo de commits…]

## Datos (opcional)

- Ruta, configuración y modo de conexión: [..].
- Migraciones idempotentes: una función por migración; añadir columnas solo si faltan; probar la migración desde un esquema antiguo con un test.
- Borrado lógico (papelera) antes que borrado físico cuando el dato sea del usuario.
- Los tests que usan la BD apuntan a un directorio temporal antes de abrir la conexión.

## Testing

- Ejecutar los tests antes de cada commit. Cada bug corregido añade un test que lo reproduzca.
- La lógica de negocio (filtros, cifrado, migraciones, exportación) se prueba sin depender de la interfaz.
- Para vistas declarativas (plantillas, FXML, rutas), un test que compruebe que cada referencia (manejador, ruta, clave de traducción) existe.

## Funcionalidades implementadas

- [Lista viva: añadir al implementar, borrar al eliminar]

## Trampas conocidas y lecciones aprendidas

- Si un refactor automático (regex/script) toca muchos sitios, verificar con compilación **y** con una prueba que ejecute lo afectado: un fallo silencioso solo aparece en ejecución.
- No asumir que una API de terceros soporta todas las operaciones; antes de "mejorar" código que parece raro, revisar el historial de git: puede estar así a propósito.
- [Cada error difícil de diagnosticar se anota aquí con su causa y su solución]

## Versión actual

- [versión y de dónde se lee]

## Tareas pendientes

- [Tareas concretas con fichero/clase afectada]

## Posibles mejoras/cambios

### BUG / FIX
### CODE QUALITY / REFACTORING
### UX
### FEATURE
### PERFORMANCE
### SECURITY
### ACCESSIBILITY

## Cómo ejecutar y empaquetar

- Ejecución local: [comando]
- Empaquetado / despliegue: [comando]
- Variables de entorno requeridas: [..]
