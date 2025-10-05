Píldoras — Contexto actualizado
Stack / build / empaquetado

Stack: Java 21, JavaFX 21, ControlsFX (autocompletado), Ikonli (iconos), SQLite JDBC.

Build: Maven + maven-shade-plugin.

Empaquetado: jpackage (app-image + MSI) vía build-msi.ps1.

No empaquetar la base de datos dentro del app-image/MSI.

Ejecución local (Eclipse)

Run as Java Application (no Maven).

VM args:

--module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib"
--add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web
--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED


ControlsFX requiere el --add-exports anterior.

Script de empaquetado (Windows)

mvn clean package -DskipTests.

Copia *-shaded.jar a target/jpkg/app.jar.

jpackage app-image con:

--module-path "$env:JAVA_HOME\jmods;$env:PATH_JAVAFX_JMODS"

--add-modules java.sql,java.xml,java.logging,java.desktop,jdk.crypto.ec,jdk.localedata,javafx.controls,javafx.fxml,javafx.web,javafx.graphics

--java-options "-Dprism.order=sw --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED"

jpackage MSI (sin --win-console) con --win-upgrade-uuid fijo.

Variables requeridas: JAVA_HOME y PATH_JAVAFX_JMODS (carpeta jmods de JavaFX).

Base de datos (SQLite)

Ruta: %APPDATA%\Pildoras\knowledgebase.db (Roaming).

Migración primer arranque: si no existe en Roaming, mover desde %LOCALAPPDATA%\Pildoras\knowledgebase.db (+ -wal y -shm).

PRAGMAs al abrir: foreign_keys=ON, journal_mode=WAL, synchronous=NORMAL, busy_timeout=5000.

Esquema actual

pildoras(id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned)

tags(id, nombre)

pildora_tag(pildora_id, tag_id) (FKs, PK compuesta)

Normalización de tags

TagDao.findOrCreate() normaliza a lowerCase(Locale.ROOT).

En startup: deduplicación por lower(nombre), UPDATE tags SET nombre = lower(nombre), CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower ON tags(lower(nombre)).

UI / UX (estado actual)
Temas / CSS

base.css + theme-light.css / theme-dark.css.

Toggle de tema se ha eliminado del header y ahora es una opción del menú de Ajustes.

ThemeManager sincroniza iconos/estilo (logo claro/oscuro).

Tipografía configurable (familia y tamaño pequeño/normal/grande) guardada en Preferences y aplicada a toda la escena con estilo inline (-fx-font-family y -fx-font-size heredados).

Title bar custom (undecorated)

Barra superior mínima con botón ✕ (cerrar), arrastre por la barra, y acciones a la derecha.

Altura reducida: padding ~4 8, spacing 6–8, borde inferior 1px.

Botón ✕ con hover rojo sutil.

Listado

Tabla paginada (20 por página).

Ordenación tri-estado por columna: primer clic ASC, segundo DESC, tercero NONE (sin flecha y vuelve al orden por defecto: fecha_creacion DESC).

Implementación estable con setSortPolicy, control de estado lastSortColumn/lastState y flag suppressSort para evitar recursividad (esto solucionó el StackOverflow/loops de sort).

Doble clic abre el detalle.

Columnas de acciones con botones compactos (editar/borrar) y HBox con spacing 10–12 (no se cortan, tamaño consistente con la altura de la fila).

Clases CSS: .icon-btn reducida dentro de fila; se remató para que el botón no sea más alto que la celda.

Favoritas y Pinned

Columna “estrella”:

Botón con Ikonli (FontAwesomeSolid.STAR/FontAwesomeRegular.STAR).

Clase star-icon + star-fav para dorado cuando está activa.

Columna “pin”:

Botón con THUMBTACK (solid), inclinación opcional, clase pin-active para estado activo (dorado).

Al fijar/desfijar, se refresca la tabla por si el ORDER BY usa pinned.

Filtros

Texto y Tags (CustomTextField de ControlsFX) con botón de limpiar integrado a la derecha (Icono usado: PLUS rotado 45° como “X”), tamaño fijo del slot para que el ancho del input no cambie al aparecer/desaparecer.

Ancho aumentado de los filtros (se ajustó prefColumnCount o -fx-pref-width).

AND/OR ahora es ToggleButton con icono:

OR → CODE_BRANCH (múltiples ramas: al menos un tag).

AND → LINK (cadena: deben estar todos).

Mantiene mismo tamaño redondo que “Solo favoritas”.

No se colorea de dorado al togglear (solo cambia el icono).

Solo favoritas es ToggleButton redondo del mismo tamaño que AND/OR, con estrella dorada al activar (solo el icono, no el fondo).

Menú de Ajustes (⚙)

MenuButton con icono COG.

Opciones:

Tipografía (familias: System, Arial, Serif, Monospace…).

Tamaño (Pequeño/Normal/Grande).

Tema (Claro / Oscuro) — movido aquí.

Atajos de teclado… (dialog informativo bonito con secciones y monoespaciado).

Se oculta la flecha del MenuButton por CSS; solo se ve el icono.

Status bar

Mensajes autoexpiran con PauseTransition.

Se muestran mensajes al cambiar:

“Solo favoritas activado/desactivado”.

“Filtro OR/AND”.

Detalle

Chips de tags son botones (misma estética que el editor, colores deterministas).

Al pulsar un tag: cierra el detalle, vuelve al listado, aplica filtro por ese tag (modo OR), va a página 1, muestra toast/status.

Exportar (arriba dcha):

Markdown (.md) y HTML (.html).

En HTML, se inyecta extraCss con colores de chips idénticos a la app.

WebView:

CSS incrustado claro/oscuro.

Re-render al cambiar de tema.

Atajos: Ctrl+E (editar), Supr (borrar), Esc (volver).

Editor

Chips de tags (FlowPane) con autocompletado ControlsFX (TagDao.listAllLike).

Enter o coma → añade chip; botón ✕ elimina chip.

Guardado: reescribe relaciones y usa findOrCreate() (tags en minúsculas).

Snippets Markdown con botones y atajos: Ctrl+B/I/K/E.

(Pendiente por seguridad): añadir toggle “Proteger píldora” aquí.

Iconos (Ikonli / FA6)

Estrella: FontAwesomeSolid.STAR / FontAwesomeRegular.STAR (+ star-fav dorado).

Pinned: FontAwesomeSolid.THUMBTACK.

Nueva: FontAwesomeSolid.FOLDER_PLUS (sustituyó al PLUS simple).

AND/OR: LINK (AND) / CODE_BRANCH (OR).

Ajustes: COG.

Export: FILE_EXPORT; Markdown: MARKDOWN; HTML: FILE_CODE.

Atajos de teclado (resumen UX)

Listado:
Ctrl+F (filtro texto), Ctrl+T (filtro tags), Ctrl+N (nueva),
Ctrl+O (detalle), Ctrl+E (editar), Supr (borrar),
M (favorita toggle, con tabla enfocada),
Ctrl+Shift+F (Solo favoritas), Ctrl+Shift+O (OR/AND),
PageUp / Ctrl+← (página -1), PageDown / Ctrl+→ (página +1),
Esc (limpiar filtros si hay foco en filtros / volver).

Detalle: Ctrl+E (editar), Supr (borrar), Esc (volver), click en tag → filtra listado por ese tag.

Editor: Ctrl+S (guardar), Esc (cancelar), Ctrl+B/I/K/E (snippets), Enter o , para confirmar tag, Backspace con input vacío borra último chip.

Preferencias (recuerdos)

PREF_SOLO_FAV (boolean).

PREF_AND_OR (boolean).

PREF_FONT_FAMILY (string).

PREF_FONT_SIZE = small|normal|large (string).

El tema se gestiona con ThemeManager (persistencia propia).

Cambios de layout/estilo aplicados

Botones redondos iguales (SoloFav / AND-OR): 36×36 aprox., icono interior más pequeño; el AND/OR no se colorea en dorado.

Ajustes para que la estrella dorada solo afecte al icono, no al fondo del botón.

Estabilidad de ancho de las dos primeras columnas (estrella/pin) cuando se activa/desactiva SoloFav.

Botones de acciones en celdas: tamaño pequeño, sin recortar, mayor separación (HBox.setSpacing(10–12)), centrados verticalmente.

Seguridad — Diseño aprobado (pendiente de integrar)

Objetivo: permitir “proteger” una píldora. Al activar protección, su contenido queda cifrado, y en el listado solo se muestra el título; la descripción se sustituye por “Contenido protegido” (sin tooltip). Para leer/editar, se solicita PIN.

Decisiones cerradas:

PIN como factor único; 4 dígitos (sencillo y suficiente para tu caso).
(Se puede permitir también PIN de longitud 4–8 si quisieras.)

Lockout global: 3 intentos fallidos → bloquea 30s. Estado global de la app.

Recordar PIN temporal: ventana de “recuerdo” en memoria (no repide durante X minutos).

Recuperación:

Pregunta de seguridad (respuesta hash/secreta; no revelamos el PIN).

Código de recuperación one-time (mostrar al crear; guardar hash).
Se podrá recuperar el PIN con una de las dos vías (o ambas, si las configuró).

Cifrado:

Generar CMK (AES-256) aleatoria y guardarla envuelta con KEK derivada del PIN (PBKDF2-HMAC-SHA256).

Datos cifrados con AES/GCM/NoPadding (IV de 12 bytes por registro).

Nunca guardar el PIN en claro.

Almacenamiento para esta app 1-usuario:

Hashes, salts, CMK envuelta, estado de lockouts/remember en Preferences.

(Más adelante, si quieres, mover a DB).

UI:

Editor: toggle “Proteger” (si no hay PIN configurado, asistente para crear PIN + pregunta + código).

Detalle: si está protegida y no hay CMK en memoria → pedir PIN (o usar “recordar”).

Listado: filas protegidas muestran “Contenido protegido” en descripción, sin tooltip.

DAO/DB (extensión a realizar):

Añadir a pildoras: protegida INTEGER NOT NULL DEFAULT 0, descripcion_cipher BLOB, descripcion_iv BLOB.

Opción A: migrar descripcion a cifrado (vaciar texto en claro al proteger).

Opción B: mantener ambos y mostrar uno u otro según protegida (recomendado para migraciones suaves).

Servicio de seguridad (API planeada):

SecurityService singleton con:

Setup/verificación PIN, cambio de PIN (re-envuelve CMK).

Estado global de lockout (3 intentos/30s).

“Remember window” (minutos).

Recuperación por pregunta y por código.

encrypt(plain) / decrypt(cipher, iv) con AES-GCM (usa CMK en memoria).

ensureUnlocked(Supplier<char[]>) para pedir PIN cuando haga falta.

Siguiente paso inmediato: integrar la implementación de SecurityService (ya diseñada) + migración de esquema + ganchos de UI (toggle en editor, prompt PIN/recuperación, y lógica en listado/detalle/DAO).

Estado de bugs relevantes ya resueltos en este chat

Ordenación: el tercer clic dejaba la flecha pero no reset; se corrigió con manejo explícito de primary == null post-clear, suppressSort, y reset a DEFAULT_ORDER_COL/DIR.

Tamaños de botones en celdas: se regularon vía CSS y fábrica de celdas para que no superen la altura de fila (evitar recortes).

Ancho columnas pin/fav: estabilizado para que no “salten” al activar SoloFav.

Clear button de filtros: slot fijo (no cambia el ancho del input), posición y márgenes afinados.

Preferencias clave (actuales)

soloFavoritas – boolean.

filtroAndOr – boolean.

uiFontFamily – String.

uiFontSize – small|normal|large.

To-Do inmediato (prioridad)

Seguridad

Añadir columnas protegida, descripcion_cipher, descripcion_iv (+ migración).

Implementar SecurityService y llamadas desde UI/DAO:

Editor: toggle “Proteger” (y wizard de alta PIN si no existe).

Al guardar protegida: cifrar descripcion → cipher+iv; limpiar descripcion en claro.

Detalle/Listado: si protegida → mostrar “Contenido protegido”; para ver, ensureUnlocked() y descifrar on-demand.

Recuperación (pregunta / código) accesible desde Ajustes o desde el prompt.

Ajustes → Tema (si no está ya): añadir acciones “Claro / Oscuro” en el menú ⚙ y persistir con ThemeManager.

Export/Import ZIP (plan futuro): empaquetar BD + assets; importar con confirmación y backup.