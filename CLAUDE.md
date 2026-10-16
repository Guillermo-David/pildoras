# Píldoras - Contexto del Proyecto

## Preferencias de trabajo

- No generar explicaciones. Solo hacer el cambio y confirmar brevemente qué se hizo.
- No añadir comentarios en el código salvo que el "por qué" sea no obvio.
- No crear ficheros de documentación (.md, README) salvo que se pida explícitamente.
- Respuestas cortas y directas.
- **Mantener este CLAUDE.md actualizado automáticamente**: Cada vez que se implemente una funcionalidad, se cambie la arquitectura, se añadan ficheros, o se modifique algo relevante del proyecto, actualizar este fichero sin que el usuario lo pida. Incluir nuevas funcionalidades en "Funcionalidades implementadas", mover tareas completadas de "Tareas pendientes", y actualizar la estructura si cambia.

## Descripción

Aplicación de escritorio JavaFX para crear y gestionar "píldoras de conocimiento" — notas técnicas con formato markdown, etiquetas, enlaces entre píldoras, y cifrado opcional.

## Stack técnico

- Java 21 / JavaFX 21 (pom.xml dice release=25 pero compilar con `-Dmaven.compiler.release=21`)
- Maven: `mvn compile -Dmaven.compiler.release=21`
- Lombok @Data para modelos
- ControlsFX (CustomTextField, ToggleSwitch)
- Ikonli FontAwesome6 v12.4.0 (FontAwesomeSolid, FontAwesomeBrands)
- openhtmltopdf para exportación PDF (requiere XHTML estricto, atributos booleanos expandidos)
- SQLite local + Turso/LibSQL para sincronización remota
- Ventana UNDECORATED con barra de título personalizada y ResizeHelper
- flexmark-java para markdown → HTML

## Seguridad — CRÍTICO

`turso_token.txt` NUNCA debe ser commiteado. Está en `.gitignore`. El token de Turso vive únicamente en la máquina del usuario.

## Arquitectura

### Paquetes principales

```
io.github.guillermo_david/
├── MainApp.java                    — Punto de entrada
├── controller/
│   ├── MainController.java         — Controlador principal (contenedor)
│   ├── ListadoPildorasController   — Tabla, paginación, filtros, columnas (~1500 líneas, pendiente refactorizar)
│   ├── DetallePildoraController    — Vista detalle con WebView, Ctrl+F (~750 líneas, pendiente refactorizar)
│   ├── EditorPildoraController     — Editor markdown
│   ├── SettingsMenuHelper          — Menú engranaje: tema, idioma, tipografía, columnas, BD, seguridad, estadísticas, ayuda, acerca de (~1000 líneas, pendiente refactorizar)
│   ├── TagSidebarHelper            — Panel lateral de etiquetas
│   ├── TextFormatController        — Barra de formato del editor
│   └── DraftsUIHelper              — UI de borradores
├── model/
│   ├── Pildora.java                — id, titulo, descripcion, fechaCreacion, fechaActualizacion, favorita, pinned, protegida, oculta, cifrado
│   ├── Tag.java
│   └── Draft.java
├── dao/
│   ├── PildoraDao.java             — CRUD + estadísticas (contarTotal, contarProtegidas, contarFavoritas, contarOcultas, contarCreadasDesde)
│   ├── TagDao.java                 — CRUD + topTagsByUsage, countAll
│   ├── PildoraTagDao.java
│   ├── PildoraLinkDao.java
│   ├── DraftDao.java
│   └── SqlHelper.java
├── db/
│   ├── DatabaseHelper.java         — Conexión SQLite + migraciones
│   ├── TursoConfig.java            — Configuración Turso
│   └── TursoMigrationService.java
├── export/
│   ├── PildoraExporter.java        — Exportar a Markdown, HTML, PDF (enlaces pildora: se eliminan)
│   └── BackupService.java
├── javafx/
│   ├── Dialogs.java                — Dialogs.decorate() para diálogos temáticos con UNDECORATED
│   ├── StatusBus.java              — Notificaciones toast (StatusBus.show())
│   ├── ThemeManager.java           — Gestión de temas claro/oscuro
│   ├── ResizeHelper.java           — Redimensionado de ventana
│   ├── PildoraPickerDialogs.java   — Selector de píldoras (ordenado alfabéticamente)
│   ├── PinDialogs.java
│   ├── DraftDialogs.java
│   ├── AppIcons.java
│   └── ColorUtil.java
├── markdown/
│   └── MarkdownEngine.java         — Renderizado markdown a HTML
├── security/
│   └── SecurityService.java        — Cifrado/descifrado de píldoras
├── importer/
│   └── MarkdownImportService.java
└── util/
    ├── I18n.java                   — Internacionalización con ResourceBundle
    ├── AppVersion.java             — Versión desde system property / manifest / version.txt
    └── AppLogger.java
```

### Recursos

```
src/main/resources/
├── fxml/
│   ├── listado-pildoras.fxml       — Vista principal con tabla
│   ├── detalle-pildora.fxml        — Vista detalle
│   └── editor-pildora.fxml         — Editor
├── messages_es.properties          — i18n español
├── messages_en.properties          — i18n inglés
├── icons/
│   ├── gdg_B.png                   — Logo tema claro
│   └── gdg_W.png                   — Logo tema oscuro
└── css/                            — Estilos de la aplicación
```

## Navegación y comunicación entre vistas

La app tiene una sola ventana (`Stage UNDECORATED`). `MainApp` carga `listado-pildoras.fxml` como escena raíz. `ListadoPildorasController` es el controlador principal que gestiona la tabla y carga las otras vistas:

- **Listado → Detalle**: `ListadoPildorasController` carga `detalle-pildora.fxml` y reemplaza el contenido del `center` del `BorderPane root`. Le pasa callbacks (`onClose`, `onEdit`, `onDelete`, `onTagClick`) al `DetallePildoraController`.
- **Listado → Editor**: Similar, carga `editor-pildora.fxml` y pasa callbacks al `EditorPildoraController`.
- **Detalle → Editor**: El botón editar en detalle invoca el callback `onEdit` que `ListadoPildorasController` proporcionó.
- **SettingsMenuHelper**: Se instancia desde `ListadoPildorasController`, recibiendo el `MenuButton btnSettings` y múltiples callbacks/dependencias via constructor (root, security, prefs, ensureUnlocked, refrescarTabla, setLogoFor, setPaginaActual, showColumnSelector).

## Temas disponibles

Definidos en `MainApp.doStart()` y gestionados por `ThemeManager`:
- light, dark, nord-light, nord-dark, dracula, solarized-light, solarized-dark

CSS: `base.css` (compartido) + `theme-*.css` (por tema). WebView tiene CSS propio: `base-webview.css` + `webview-light.css` / `webview-dark.css`.

## Layout principal (listado-pildoras.fxml)

```
BorderPane (root)
├── top: VBox
│   ├── HBox (titleBar) — barra título custom con btnSettings y btnClose
│   └── HBox (appHeader) — logo, filtro texto, toggle favoritos, btnNueva (SplitMenuButton)
├── center: HBox
│   ├── TableView (table) — columnas: colFav, colTitulo, colDescripcion, colTags, colFecha, colAcciones
│   └── VBox (tagSidebar) — panel lateral de tags con scroll
└── bottom: VBox
    ├── HBox (paginationBox) — btnAnterior, lblPagina, btnSiguiente
    └── HBox (statusBar) — lblStatus
```

## Patrones y convenciones

- **i18n**: Usar `I18n.get("clave")` y `MessageFormat` con `{0}`, `{1}` para parámetros. Claves en messages_es.properties y messages_en.properties. Siempre añadir claves en ambos ficheros.
- **Diálogos temáticos**: Usar `Dialogs.decorate(dialog)` para heredar tema UNDECORATED y estilos. Patrón: crear Dialog/Alert, llamar decorate(), configurar contenido, showAndWait().
- **Notificaciones**: `StatusBus.show("mensaje")` para toasts en la barra de estado.
- **Preferencias usuario**: `java.util.prefs.Preferences` para persistir ajustes (directorio exportación, fuente, visibilidad columnas, tema, idioma).
- **WebView JS bridge**: `DetallePildoraController.JsBridge` — clase pública con nombre (no anónima ni lambda). Se inyecta en `window.app` vía `JSObject.setMember()`. Necesario para que JavaScript invoque métodos Java (ej: `window.app.openPildora(id)`).
- **Enlaces internos**: Esquema `pildora:<id>` en markdown → se renderizan como links clickables en WebView → `JsBridge.openPildora()` los intercepta. Se eliminan (texto plano) en exports HTML/PDF.
- **Iconos**: Ikonli FontAwesome6 — usar `FontAwesomeSolid` y `FontAwesomeBrands`. Crear con `new FontIcon(FontAwesomeSolid.NOMBRE)`.
- **Exports**: `PildoraExporter` convierte markdown a HTML, luego a XHTML estricto para PDF (openhtmltopdf). Los atributos booleanos HTML deben expandirse (`checked` → `checked="checked"`) para que el parser XHTML no falle.
- **Paginación**: Server-side via `PildoraDao.listarFiltradas()` con LIMIT/OFFSET. Ordenación por columnas mapeada en `ListadoPildorasController.setTableProperties()`.

## Funcionalidades implementadas

- CRUD de píldoras con markdown y vista previa WebView
- Etiquetas con sidebar y filtrado
- Cifrado/descifrado de píldoras protegidas
- Exportación a Markdown, HTML, PDF (con memoria de directorio)
- Sincronización Turso/LibSQL
- Borradores auto-guardados
- Temas claro/oscuro
- i18n español/inglés
- Selector de columnas (título, descripción, tags, fecha, acciones) con persistencia
- Búsqueda Ctrl+F en WebView detalle
- Diálogos: Acerca de, Ayuda, Estadísticas
- Atajos de teclado configurables
- Paginación servidor con ordenación por columnas
- Pill picker ordenado alfabéticamente
- Pin/favoritos/ocultar píldoras

## Trampas conocidas y lecciones aprendidas

- **PDF export XHTML**: openhtmltopdf requiere XHTML estricto. Los atributos booleanos HTML (`checked`, `disabled`, `selected`) deben expandirse a `checked="checked"` etc. Si no, el parser falla silenciosamente.
- **WebView JS bridge**: La clase que se pasa a `JSObject.setMember()` DEBE ser pública y con nombre. Las clases anónimas/lambda no funcionan — el garbage collector las elimina y las llamadas JS fallan silenciosamente.
- **Compilación en cloud**: El pom.xml tiene `maven.compiler.release=25` pero el entorno cloud solo tiene Java 21. Usar siempre `-Dmaven.compiler.release=21` al compilar.
- **Ikonli version**: v12.4.0 exacta. Las clases de iconos cambian entre versiones. No actualizar sin verificar.
- **ControlsFX CustomTextField**: Importar desde `org.controlsfx.control.textfield.CustomTextField`, no desde javafx.
- **Recursos images**: Los logos están en `/icons/gdg_B.png` (claro) y `/icons/gdg_W.png` (oscuro). Los iconos de la app en `/icons/pill_*.png`. Cargar siempre con null-check por si el recurso no existe.

## Versión actual

- pom.xml version: 3.1.0
- AppVersion: lee de system property `app.version`, luego MANIFEST, luego `/version.txt`, fallback "dev"

## Tareas pendientes

- **Refactorizar controladores grandes**: ListadoPildorasController (~1500 líneas), DetallePildoraController (~750 líneas), SettingsMenuHelper (~1000 líneas). Extraer lógica a clases auxiliares. Candidatos de extracción:
  - ListadoPildorasController: setup de columnas, atajos de teclado, paginación, menú contextual, carga de vistas detalle/editor
  - DetallePildoraController: lógica de exportación, barra de búsqueda Ctrl+F, renderizado WebView con temas
  - SettingsMenuHelper: diálogos (About, Help, Statistics) a clases propias, configuración de tipografía, gestión de BD/Turso

## Posibles mejoras/cambios

### BUG / FIX

- **Turso SQL injection**: `PildoraDao` (insertarTurso, actualizarTurso), `TagDao.findOrCreateTurso()`, `BackupService.importFromFile()` usan concatenación de strings para construir SQL. Aunque `SqlHelper.esc()` escapa valores, es más seguro migrar a queries parametrizadas.
- **BackupService import duplica código local/Turso**: Las ramas `if (!turso)` y `else` en `importFromFile()` ejecutan exactamente el mismo SQL de DELETE — código muerto.
- **EditorPildoraController.mdClearHeading()** llama a `textFormatController.mdH3()` en vez de a un método que elimine headings. Probablemente un bug de copiar/pegar.
- **EditorPildoraController preview doble-wrapping HTML**: `renderPreviewNow()` genera un HTML completo y luego lo mete dentro de otro `<!doctype html>` con `loadContent(formatted)`. El body se renderiza dos veces anidado.
- **TagSidebarHelper instancia `new TagDao()` en cada `refresh()`** — debería reutilizar instancia.
- **PildoraExporter duplica configuración flexmark**: `MarkdownEngine` y `PildoraExporter` tienen cada uno su propia configuración de Parser/HtmlRenderer con extensiones ligeramente distintas (PildoraExporter no incluye TypographicExtension). Deberían compartir una instancia.
- **ThemeManager.toggle()** solo alterna entre "light" y "dark" aunque hay 7 temas; si el usuario está en "dracula", toggle lo pone en "light" sin pasar por los intermedios.

### CODE QUALITY / REFACTORING

- **Código duplicado `unlockWithFeedback()`**: Existe tanto en `ListadoPildorasController` como en `DetallePildoraController`. Extraer a una clase utilitaria `SecurityUiHelper`.
- **Código duplicado `humanize()`**: Misma situación: duplicado en `ListadoPildorasController` y `DetallePildoraController`.
- **Lógica de confirmación de borrado triplicada**: En `ListadoPildorasController` aparece en el menú contextual, en la columna acciones, y en el handler de tecla DELETE. Unificar en un solo método `confirmAndDelete(Pildora)`.
- **Métodos de exportación duplicados en DetallePildoraController**: `exportMarkdown()`, `exportHtml()`, `exportPdf()` tienen bloques try/catch casi idénticos para selección de fichero, invocación y manejo de error. Extraer a un método genérico `exportWith(extension, exporter)`.
- **PildoraDao.listarFiltradas()/contarFiltradas()** duplican la construcción de la cláusula WHERE. Extraer a un método privado `buildWhereClause(...)`.
- **`new TagDao()` creado múltiples veces** en DetallePildoraController en distintos métodos — reutilizar una instancia de campo.
- **SettingsMenuHelper constructor con 11 parámetros** — considerar un builder o un record de configuración.
- **DatabaseHelper.initializeDatabase()** es un método de ~190 líneas con migraciones entrelazadas — extraer cada migración a su propio método.
- **EditorPildoraController** tiene ~30 métodos FXML que solo delegan a `textFormatController` (`mdBold`, `mdItalic`, etc.) — considerar enlazar directamente en FXML con un `TextFormatController` instanciado en FXML o usar un solo método dispatcher.

### UX

- **Búsqueda global / full-text search**: Actualmente solo se filtra por título o contenido con LIKE. Implementar FTS5 de SQLite para búsquedas más rápidas y con ranking de relevancia.
- **Drag-and-drop de imágenes en el editor**: Permitir arrastrar imágenes al TextArea y convertirlas automáticamente a markdown con base64 o ruta relativa.
- **Múltiples ventanas / tabs**: Abrir más de una píldora a la vez (en pestañas o ventanas independientes).
- **Historial de versiones**: Guardar versiones anteriores de las píldoras para poder comparar o restaurar.
- **Ordenación de tags en sidebar**: Permitir ordenar por nombre o por cantidad de uso.
- **Filtro por múltiples tags (AND/OR)**: Actualmente el sidebar permite seleccionar múltiples tags pero el filtro parece ser OR. Ofrecer opción AND.
- **Autocompletado de enlaces internos**: Al escribir `pildora:` en el editor, mostrar sugerencias de píldoras existentes (ya existe `Ctrl+Shift+K` pero podría ser inline).
- **Indicador visual de píldoras enlazadas**: En la tabla de listado, indicar cuántos backlinks tiene cada píldora.
- **Preview en tiempo real mejorada**: El SplitPane del editor podría recordar su posición entre sesiones.
- **Modo "focus" / zen mode**: Ocultar todo excepto el editor para escribir sin distracciones.
- **Atajos de teclado personalizables via UI**: Actualmente los atajos están hardcodeados. Permitir configurarlos desde Settings.
- **Contador de palabras/caracteres en editor**: Mostrar estadísticas del texto mientras se escribe.
- **Papelera de reciclaje**: En vez de borrado definitivo, marcar como eliminada con posibilidad de restaurar.

### FEATURE

- **Importación masiva de markdown**: Importar múltiples ficheros .md de una carpeta, creando una píldora por cada uno.
- **Exportación masiva**: Exportar todas las píldoras (o las filtradas) a un directorio como .md o HTML.
- **Grafo de conocimiento**: Visualizar las relaciones entre píldoras (pildora:links) como un grafo interactivo.
- **Plantillas de píldoras**: Permitir crear templates (ej: "Bug report", "Tutorial", "Cheatsheet") para iniciar una nueva píldora con estructura predefinida.
- **Sistema de notificaciones/recordatorios**: Asociar recordatorios a píldoras para revisarlas periódicamente (spaced repetition).
- **Soporte para adjuntos**: Asociar ficheros (PDF, imágenes, etc.) a una píldora.
- **Compartir via enlace / exportar a gist**: Publicar una píldora como gist de GitHub o enlace temporal.
- **Sincronización bidireccional Turso mejorada**: Actualmente las variantes Turso son métodos separados; unificar la capa de datos para que el cambio local↔remoto sea transparente.
- **Soporte LaTeX completo**: La extensión MathJax está comentada en `MarkdownEngine`. Activar y asegurar renderizado en preview y exports.
- **Modo presentación**: Convertir una píldora (con headings) en slides simples navegables.
- **Tabla de contenidos automática**: Generar TOC a partir de los headings de una píldora larga.
- **Duplicar píldora**: Botón para crear una copia de una píldora existente como punto de partida.

### PERFORMANCE

- **Cache de tags con TTL fijo de 5s** (`TagDao.CACHE_TTL_MS`): Podría ser configurable. Además, `listAllWithCount()` no diferencia la cache entre los modos `includeHidden=true` y `includeHidden=false` — puede devolver datos incorrectos.
- **`preloadForPildoras()` concatena IDs sin PreparedStatement**: Para listas grandes podría ser un problema. Usar parámetros o batches.
- **MarkdownEngine se instancia repetidamente**: `EditorPildoraController` crea `new MarkdownEngine()` en campo. Podría ser un singleton o compartido si es thread-safe (flexmark parsers son reutilizables).
- **Paginación**: `contarFiltradas()` ejecuta un COUNT que recorre la misma query que `listarFiltradas()` — en tablas grandes podría beneficiarse de caching del total mientras los filtros no cambien.
- **DatabaseHelper.reconnect()** cierra y destruye la instancia singleton sin sincronización fina — si otros hilos están usando la conexión anterior podría dar errores.

### SECURITY

- **SecurityService lockout no es escalable a ataques sostenidos**: El exponential backoff crece (`30s * 2^cycles`) pero `lock_cycles` se resetea con un acierto. Considerar añadir un máximo de ciclos o wipe tras N ciclos.
- **Pregunta de seguridad almacena texto en claro**: `K_Q_TEXT` guarda la pregunta en Preferences sin cifrar. Un atacante con acceso al registro del sistema puede ver las preguntas (aunque no las respuestas).
- **PBKDF2 200K iteraciones**: Está bien para 2024, pero conviene tener en cuenta Argon2 como alternativa futura para mayor resistencia.
- **Backup JSON expone cipher blobs en base64**: Si un backup es interceptado, contiene los datos cifrados. Considerar cifrar el fichero de backup completo.
- **`askPin6()` en EditorPildoraController** usa `TextInputDialog` sin Dialogs.decorate() — no hereda el tema.

### ACCESSIBILITY

- **Sin soporte de accesibilidad declarado**: Los nodos JavaFX no tienen `accessibleText` o `accessibleRole` configurados explícitamente.
- **Navegación por teclado incompleta**: Algunos flujos (sidebar tags, selector de columnas) podrían no ser navegables solo con teclado.
- **Contraste de colores**: Los chips de tags generados por `ColorUtil.colorForTag()` no verifican ratio de contraste WCAG.
- **Tamaño de fuente mínimo**: La configuración de tipografía permite reducir mucho el tamaño sin mínimo.

## Git

- Rama de desarrollo: `claude/optimistic-fermi-wnwnr9`
- Rama principal: `master`
- Autor: Guillermo David (willorenagepete@gmail.com)
