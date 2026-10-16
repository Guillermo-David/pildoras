# Píldoras - Contexto del Proyecto

## Preferencias de trabajo

- No generar explicaciones. Solo hacer el cambio y confirmar brevemente qué se hizo.
- No añadir comentarios en el código salvo que el "por qué" sea no obvio.
- No crear ficheros de documentación (.md, README) salvo que se pida explícitamente.
- Respuestas cortas y directas.

## Descripción

Aplicación de escritorio JavaFX para crear y gestionar "píldoras de conocimiento" — notas técnicas con formato markdown, etiquetas, enlaces entre píldoras, y cifrado opcional.

## Stack técnico

- Java 21 / JavaFX 21 (pom.xml dice release=25 pero compilar con `-Dmaven.compiler.release=21`)
- Maven: `mvn compile -Dmaven.compiler.release=21`
- Lombok @Data para modelos
- ControlsFX (CustomTextField, ToggleSwitch)
- Ikonli FontAwesome6 v12.4.0 (FontAwesomeSolid, FontAwesomeBrands)
- openhtmltopdf para exportación PDF (requiere XHTML estricto)
- SQLite local + Turso/LibSQL para sincronización remota
- Ventana UNDECORATED con barra de título personalizada

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

## Patrones y convenciones

- **i18n**: Usar `I18n.get("clave")` y `MessageFormat` con `{0}`, `{1}` para parámetros. Claves en messages_es.properties y messages_en.properties.
- **Diálogos temáticos**: Usar `Dialogs.decorate(dialog)` para heredar tema UNDECORATED y estilos.
- **Notificaciones**: `StatusBus.show("mensaje")` para toasts.
- **Preferencias usuario**: `java.util.prefs.Preferences` para persistir ajustes (directorio exportación, fuente, visibilidad columnas).
- **WebView JS bridge**: Debe usar clase pública con nombre (no anónima) para llamadas JS→Java.
- **Enlaces internos**: Esquema `pildora:<id>` en markdown. Se eliminan en exports HTML/PDF.
- **Iconos**: Ikonli FontAwesome6 — usar `FontAwesomeSolid` y `FontAwesomeBrands`.

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

## Tareas pendientes

- **Refactorizar controladores grandes**: ListadoPildorasController (~1500 líneas), DetallePildoraController (~750 líneas), SettingsMenuHelper (~1000 líneas). Extraer lógica a clases auxiliares.

## Git

- Rama de desarrollo: `claude/optimistic-fermi-wnwnr9`
- Rama principal: `master`
- Autor: Guillermo David (willorenagepete@gmail.com)
