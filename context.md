Proyecto: Píldoras (JavaFX + SQLite).
Stack: Java 21, JavaFX 21, ControlsFX (autocompletado), Ikonli (iconos), SQLite JDBC.
Build: Maven + maven-shade-plugin.
Empaquetado: jpackage (app-image + MSI). Script: build-msi.ps1.

BD (SQLite):

Ruta definitiva: %APPDATA%\Pildoras\knowledgebase.db (Roaming).

Migración desde %LOCALAPPDATA%\Pildoras\knowledgebase.db en el primer arranque (mueve también -wal y -shm).

PRAGMAs al abrir: foreign_keys=ON, journal_mode=WAL, synchronous=NORMAL, busy_timeout=5000.

Tags siempre en minúsculas (TagDao.findOrCreate normaliza).

Índice único case-insensitive:
CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower ON tags(lower(nombre));

Se ejecutó deduplicación de tags (case-insensitive) antes de crear el índice.

UI/Tema:

Sistema de temas (light/dark) con base.css, theme-light.css, theme-dark.css.

Diálogos Alert con decoración custom (StageStyle.UNDECORATED), barra de título propia y mismos stylesheets que la app (método decorate(Alert)).

Detalle: chips de tags con colores deterministas vía ColorUtil.colorForTag(name, isDark) y texto bestTextOn().

En detalle: botón MenuButton Exportar arriba a la derecha (opciones Markdown y HTML).
HTML exportado incluye CSS para chips consistentemente coloreados.

Listado/Atajos:

Filtros texto/tags, paginación, favoritas, “pinned”, doble click abre detalle.

Atajos: Ctrl+N (nueva), Ctrl+E (editar), Supr (eliminar), Ctrl+F / Ctrl+T (filtros), PageUp/PageDown, etc.

Empaquetado (Windows):

build-msi.ps1:

Usa mvn clean package -DskipTests.

Copia *-shaded.jar a target/jpkg/app.jar.

jpackage app-image con --module-path "$env:JAVA_HOME\jmods;$env:PATH_JAVAFX_JMODS" y --add-modules java.sql,java.xml,java.logging,java.desktop,jdk.crypto.ec,jdk.localedata,javafx.controls,javafx.fxml,javafx.web,javafx.graphics.

Añade --java-options "-Dprism.order=sw --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED".

MSI sin --win-console, con --win-upgrade-uuid fijo.

No empaquetar knowledgebase.db dentro del app-image/MSI.

Pendientes/ideas (si salen):

Import/Export ZIP del espacio de usuario (BD + assets).

Búsqueda mejorada por contenido/etiquetas.

Sugerencia de tags frecuentes al editar.