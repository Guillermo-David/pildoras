Stack / Build / Empaquetado

Stack: Java 21, JavaFX 21 (controls, fxml, web), ControlsFX (autocompletado), Ikonli (FA6), SQLite JDBC.

Build: Maven + maven-shade-plugin (fat JAR).

Empaquetado: jpackage (app-image + MSI) vía build-msi.ps1.
No empaquetar la BD dentro del MSI/app-image.

Ejecución local (Eclipse)

Ejecutar como Java Application (no Maven).

VM args:

--module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib"
--add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web
--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED


(el --add-exports es por ControlsFX).

jpackage

App-image:

--module-path "$env:JAVA_HOME\jmods;$env:PATH_JAVAFX_JMODS"
--add-modules java.sql,java.xml,java.logging,java.desktop,jdk.crypto.ec,jdk.localedata,javafx.controls,javafx.fxml,javafx.web,javafx.graphics
--java-options "-Dprism.order=sw --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED"


MSI: sin --win-console, con UUID fijo en --win-upgrade-uuid.

Variables requeridas: JAVA_HOME y PATH_JAVAFX_JMODS (carpeta jmods de JavaFX).

Base de datos (SQLite)

Ruta: %APPDATA%\Pildoras\knowledgebase.db (Roaming).

Migración primer arranque: si falta en Roaming, mover desde %LOCALAPPDATA%\Pildoras\knowledgebase.db incluyendo -wal y -shm.

PRAGMAs al abrir: foreign_keys=ON, journal_mode=WAL, synchronous=NORMAL, busy_timeout=5000.

Conexión y ciclo de vida

DatabaseHelper mantiene una única conexión abierta (singleton).
Los DAO no deben cerrarla. Usar savepoints si se anidan operaciones en transacción.

Esquema actual

pildoras(id, titulo, descripcion NULL, fecha_creacion, fecha_actualizacion, favorita, pinned, descripcion_cipher BLOB, descripcion_iv BLOB, protegida INTEGER NOT NULL DEFAULT 0)

tags(id, nombre)

pildora_tag(pildora_id, tag_id) (PK compuesta, FKs)

drafts(id, titulo, contenido NULL, contenido_cipher BLOB, contenido_iv BLOB, protegida, fechas)

pildora_link(from_id, to_id, kind TEXT DEFAULT 'ref', position INTEGER NULL, created_at, UNIQUE(from_id,to_id,kind), FKs ON DELETE CASCADE)

Migraciones idempotentes clave

Añadir columnas si faltan (protegida, descripcion_cipher, descripcion_iv).

Si pildoras.descripcion era NOT NULL, reconstrucción a NULL.

Tags normalizados: deduplicación case-insensitive +
CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower ON tags(lower(nombre)).

Drafts y pildora_link creadas si no existen.

Seguridad (PIN y cifrado)

PIN: 6 dígitos. Lockout global: 3 intentos → 30s.
“Recordar PIN” en memoria por ventana temporal.

Cifrado: CMK AES-256 envuelta con KEK derivada del PIN (PBKDF2-SHA256).
Datos protegidos con AES/GCM (IV 12 bytes) por registro.
Nunca se guarda el PIN en claro.

Recuperación: Pregunta de seguridad y código one-time.

UI:

Listado: si protegida, la descripción muestra “Contenido protegido”.

Detalle: si protegida y bloqueado, pedir PIN; si desbloqueado, descifrar on-demand.

Editor: toggle “Proteger contenido” (cifra al guardar).

Borradores: soporte de protección (ver sección Drafts).

Servicio: SecurityService con ensureUnlocked(...), encrypt(...), decrypt(...), intentos, lockout, etc.

Markdown y vista previa

Parser/renderer: Flexmark (sustituye a commonmark), con extensiones:
tables, strikethrough, autolink, gfm-tasklist, footnotes,
attributes, toc, anchorlink (no “heading-anchor”).

Math: sin flexmark-math (evitamos GraalVM).
MathJax v3 en WebView y en HTML exportado. Soporta $…$ y $$…$$.

Editor inserta plantillas correctas para \sum, \prod, \int con llaves escapadas en Markdown (p. ej. $\sum_\{i=1\}^\{n\}{}$).

Delimitadores \left...\right (menú de corchetes/llaves) listos para teclado español.

CSS WebView: base-webview.css + tema claro/oscuro.
Evita unidades rem (JavaFX CSS no las soporta).

Detalle / WebView bridge:

wrapHtmlWithCss(bodyHtml, css, mathjax, interceptLinks).

interceptLinks=true: intercepta pildora:<id> y llama a window.app.openPildora(id) (navegación interna).

mathjax=true carga MathJax 3.

Editor

Toolbar Markdown con: B, I, tachado, código inline, encabezados H1/H2/H3, limpiar encabezado, listas con viñetas/numeradas, task-list (- [ ]), blockquote, bloque de código, regla horizontal, tabla 2x2, insertar enlace/imagen con diálogos, TOC, footnote, Math inline y Math block (con plantillas).

Atajos:
Ctrl+S / Ctrl+Enter guardar · Esc cancelar ·
Ctrl+B/I/K/E formato · Tab/Shift+Tab para indentar/desindentar selección.

Preview: conmutador (ToggleSwitch) “👁 Vista previa” a la derecha de la toolbar.
SplitPane editor/preview, apagado por defecto. Debounce (~250ms).

Tags: FlowPane con chips; Enter o , → añade; Backspace con input vacío → borra el último chip.

Enlaces internos entre píldoras

Sintaxis: [título](pildora:123) (Markdown).

Inserción: mdInsertInternalLink() abre PildoraPicker; inserta el enlace (usa selección como texto si existe).

Render: WebView intercepta y abre la píldora destino (sin salir de la app).

Listado

Tabla paginada (tamaño fijo por página).
Rueda del ratón → cambia de página (no hay scroll vertical).

Ordenación tri-estado por columna (ASC → DESC → NONE) con restauración del estado visual (control de lastSortColumn/lastState y suppressSort).

Acciones por fila: editar/borrar con botones compactos, sin desbordes.

Favoritos (estrella) y Pinned (chincheta) con iconos Ikonli; solo el icono se colorea.

Filtros:

Texto y Tags con Clear integrado (ControlsFX CustomTextField). El ancho no “baila” al aparecer la X.

AND/OR es Toggle redondo: LINK (AND) / CODE_BRANCH (OR).
“Solo favoritas” es un toggle redondo homólogo (estrella dorada).

Paginación (label): “X–Y de Z”.

Atajos:
Ctrl+F filtro texto · Ctrl+T filtro tags · Ctrl+N nueva ·
Ctrl+O detalle · Ctrl+E editar · Supr borrar ·
M toggle favorita (con foco en tabla) ·
Ctrl+Shift+F solo favoritas · Ctrl+Shift+O OR/AND ·
PageUp / Ctrl+← pág. anterior · PageDown / Ctrl+→ pág. siguiente ·
Esc limpia filtros (si foco) / vuelve.

Detalle

Chips de tags (botones coloridos deterministas). Click → vuelve al listado filtrando por ese tag (modo OR).

Exportar: Markdown y HTML.
HTML reutiliza Flexmark con las mismas extensiones, inyecta MathJax y añade extraCss con colores de chips idénticos a la app.

WebView: CSS claro/oscuro + re-render al cambiar tema.
Usa wrapHtmlWithCss(..., mathjax=true, interceptLinks=true).

Referencias entre píldoras (cross-links):

Tabla pildora_link (kind='ref').

En detalle se muestran dos secciones:

Referencias (salientes, p → otros)

Enlazadas aquí (entrantes, otros → p)

Chips clicables con 🔒 si la destino está protegida.

Implementación unificada: loadRefsSection(pId) + chipForLink(...).
(Se eliminó la duplicación previa de métodos/controles.)

Atajos: Ctrl+E editar · Supr borrar · Esc volver.

Drafts (Borradores)

Menú (icono goma de borrar):

Nuevo borrador… → diálogo rápido:

Título opcional, área de texto, [ ] Proteger con PIN.

Botones: Guardar (borrador) / Convertir a píldora / Cancelar.

Si se marca “Proteger”, cifra el cuerpo (usa SecurityService.ensureUnlocked).

Atajos: Ctrl+S / Ctrl+Enter → Guardar.

Bandeja de borradores… → TableView con:

Columnas Título y Contenido (si protegido, se muestra 🔒 Contenido protegido).

Doble clic fila → edita en el diálogo rápido (con descifrado si procede).

Supr → borrar seleccionado (con confirmación).

Columna Acciones con botón Eliminar.

Badge/contador: el tooltip del botón muestra “Borradores (N)”.
(Overlay visual opcional, pendiente si se desea).

Ajustes (⚙)

Tipografía: familia (System, Arial, Serif, Monospace…) y tamaño (small/normal/large + vlarge). Persistencia en Preferences.

Tema: claro/oscuro (con ThemeManager).

Atajos de teclado…: diálogo monoespaciado con secciones (incluye bloque Borradores).

Seguridad (submenú): bloquear, configurar pregunta, generar código, etc.

CSS / Estilo

App: base.css + theme-light.css / theme-dark.css.

Variables “chip” compatibles con JavaFX:

.tag-chip {
  -chip-bg: #eef2ff; -chip-fg: #3730a3;
  -fx-background-color: -chip-bg;
  -fx-text-fill: -chip-fg;
  -fx-background-radius: 999px;
  -fx-padding: 2px 8px;
}


Evitar rem y border: 1px solid rgba(...) con medidas no soportadas por JavaFX CSS en partes no WebView.

WebView: base-webview.css (sí se pueden usar unidades CSS estándar).

DAO destacados

TagDao: findOrCreate() normaliza a minúsculas (Locale.ROOT).

PildoraLinkDao:

replaceRefs(int fromId, Collection<Integer> toIds) usa savepoint si ya hay transacción activa; no cierra la conexión global.

listOutgoingRefs(...) / listIncomingRefs(...) devuelven Píldoras mínimas (id, título, protegida).

Al guardar protegidas: se guarda descripcion_cipher + descripcion_iv y la descripcion puede ser NULL.

Atajos (resumen)

Listado: Ctrl+F, Ctrl+T, Ctrl+N, Ctrl+O, Ctrl+E, Supr, M, Ctrl+Shift+F, Ctrl+Shift+O, PageUp/Ctrl+←, PageDown/Ctrl+→, Esc.

Detalle: Ctrl+E, Supr, Esc, click en tag → filtrar.

Editor: Ctrl+S/Ctrl+Enter guardar, Esc cancelar, Ctrl+B/I/K/E, Tab/Shift+Tab indentación, Enter/, para confirmar tag, Backspace (input vacío) elimina último tag.

Drafts:

Bandeja: doble clic edita; Supr elimina.

Diálogo rápido: Ctrl+S / Ctrl+Enter guardar.

Pendientes / Ideas (parking)

Overlay numérico en botón de borradores (badge visual).

Export/Import ZIP (BD + assets) con backup y confirmación.

SMTP/“Compartir por email” (diseñar estrategia credenciales).

Más tipos de “píldora” (tarea, receta, etc.) y vistas específicas (en pausa).

Notas de integración clave (para nuevos colaboradores)

Usar Flexmark (no Commonmark) con las extensiones indicadas. Para anchors, usar flexmark-ext-anchorlink (no “heading-anchor”).

Math siempre con MathJax v3 (WebView y export HTML). No incluir dependencias Graal/JS.

WebView debe cargar contenido con wrapHtmlWithCss(bodyHtml, css, /*mathjax*/true, /*interceptLinks*/true) en Detalle, para soportar enlaces internos y fórmulas.

No cerrar la conexión de DatabaseHelper. En DAOs, si hace falta transacción anidada, usar savepoints.

Protección: respetar SecurityService.ensureUnlocked(...) antes de descifrar y cifrar con AES-GCM al guardar.