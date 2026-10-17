# [Nombre del proyecto] - Contexto del Proyecto

> Plantilla genérica. Sustituir lo que está entre [corchetes] y borrar las secciones que no apliquen.

## Preferencias de trabajo

- **Minimizar tokens**: solo escribir código correcto en la aplicación y probarlo (compilar/tests). Sin explicaciones, sin resúmenes largos, sin repetir contexto, sin preguntas innecesarias. Respuesta final: 1-3 líneas máximo (qué se hizo + resultado de la prueba).
- No leer ficheros enteros ni explorar si no hace falta; usar grep/rangos de líneas. No releer lo ya editado.
- No añadir comentarios en el código salvo que el "por qué" sea no obvio.
- No crear ficheros de documentación (.md, README) salvo que se pida explícitamente.
- Seguir el estilo del código circundante (nombres, idioma, densidad de comentarios).
- Probar siempre lo que se cambia (compilar, tests) antes de darlo por terminado; si algo falla, decirlo tal cual con la salida del error.
- Confirmar antes de acciones difíciles de revertir o que afecten a terceros (borrar, sobrescribir, publicar, force-push).
- **Mantener este CLAUDE.md actualizado SIEMPRE, sin que el usuario lo pida**: el cambio en `CLAUDE.md` va en el mismo commit que el cambio de código, y antes de dar por terminada cualquier tarea hay que revisar que refleja el estado real. Actualizar cuando: se implementa o elimina una funcionalidad, cambia la arquitectura o se mueven ficheros, se añaden dependencias, se descubre una trampa o lección, o se completa/aparece una tarea. No avisar de que se ha actualizado.

## Git

- Rama de trabajo: `[rama]`. Rama principal: `[main/master]`. Autor: [nombre y correo].
- Commit y push a la rama de trabajo al terminar cada cambio grande, sin narrarlo. Mensajes descriptivos (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `security:`).
- No crear PR ni mergear salvo petición explícita. No reescribir historia ajena.
- Antes de continuar un trabajo, comprobar con `git fetch` y `git log` si la rama principal ha avanzado.

## Seguridad - CRÍTICO

- Nunca commitear secretos (tokens, claves, contraseñas, `.env`). Añadirlos a `.gitignore` desde el principio: `[fichero_de_secretos]`.
- Todo SQL con valores externos va parametrizado (`PreparedStatement`). Si el driver no lo permite, escapar de forma centralizada y con tests. Whitelist para `ORDER BY` y nombres de columna.
- Los PIN/contraseñas se limpian de memoria tras usarse (`Arrays.fill(pin, '\0')`). Derivación con PBKDF2 (o Argon2) y cifrado autenticado (AES-GCM).
- Bloqueo por intentos fallidos con espera exponencial y tope máximo.

## Descripción

[Qué es la aplicación, para quién y qué problema resuelve, en 2-3 líneas.]

## Stack técnico

- [Lenguaje / versión]
- [Framework UI o web]
- [Build: comando exacto de compilar y de ejecutar tests]
- [Base de datos]
- [Librerías clave y versiones fijadas que no deben actualizarse sin probar]

## Arquitectura

```
[paquete raíz]/
├── [controller/]  — controladores / vistas
├── [model/]       — modelos
├── [dao|repo/]    — acceso a datos
├── [db/]          — conexión y migraciones
├── [util/]        — utilidades
└── ...
```

Describir aquí el flujo de navegación entre vistas y cómo se comunican los componentes.

## Patrones y convenciones

- **Textos de UI**: siempre mediante i18n; añadir cada clave en todos los idiomas a la vez.
- **Diálogos**: usar un único helper que aplique tema y estilo a todos los diálogos.
- **Notificaciones**: un único canal para mensajes al usuario (toast / barra de estado).
- **Preferencias de usuario**: guardarlas en un único sitio (`Preferences`, fichero de config) con claves en constantes.
- **Código compartido**: si una lógica aparece en dos sitios, extraerla a una clase auxiliar antes de copiarla una tercera vez.
- **Constructores con muchos parámetros** (más de 5): usar un record o builder de configuración.
- **Controladores grandes** (> ~800 líneas): extraer diálogos, menús y atajos a clases propias.
- **Un handler por familia de acciones** (por ejemplo, un único `onAction` con `userData`) en vez de decenas de métodos que solo delegan.

## Base de datos

- Ruta, PRAGMAs y modo de conexión: [..].
- Migraciones idempotentes: añadir columnas solo si faltan; una función por migración; probar la migración desde un esquema antiguo con un test.
- Borrado lógico (papelera) antes que borrado físico cuando el dato sea del usuario.
- Los tests que usan la BD fijan un directorio temporal antes de abrir la conexión (singleton).

## Testing

- Ejecutar los tests antes de cada commit. Cada bug corregido añade un test que lo reproduzca.
- Para vistas declarativas (FXML, plantillas), un test que compruebe que cada manejador referenciado existe en su controlador.
- Los tests de lógica de negocio (filtros, cifrado, migraciones, exportación) no dependen de la interfaz.

## Funcionalidades implementadas

- [Lista viva; añadir al implementar, borrar al eliminar]

## Trampas conocidas y lecciones aprendidas

- [Cada error difícil de diagnosticar se anota aquí con su causa y la solución]
- Si un refactor automático (regex/script) toca muchos sitios, verificar el resultado con compilación **y** con un test que cargue las vistas: un fallo silencioso solo aparece en ejecución.
- No asumir que una API de terceros soporta todas las operaciones: comprobar el historial del repositorio antes de "mejorar" algo que se escribió así a propósito.

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
- Empaquetado: [comando]
- Variables de entorno requeridas: [..]
