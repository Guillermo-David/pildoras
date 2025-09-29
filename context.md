# Píldoras – Contexto del Proyecto

## Stack y build
- **Stack**: Java 21, JavaFX 21, ControlsFX (autocompletado), Ikonli (iconos), SQLite JDBC.
- **Build**: Maven + maven-shade-plugin.
- **Empaquetado**: `jpackage` (app-image + MSI) vía script `build-msi.ps1`.
- **NO** empaquetar la base de datos dentro del app-image/MSI.

## Ejecución local (Eclipse)
- Usa **Java Application** (no “Run as Maven”).
- **VM args**:

``` 
--module-path "<ruta a jars JavaFX en .m2 o al SDK>"
--add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web
--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED
``` 

- ControlsFX necesita el `--add-exports` anterior.

## Script de empaquetado (Windows)
- `build-msi.ps1`:
- Ejecuta `mvn clean package -DskipTests`.
- Copia `*-shaded.jar` a `target/jpkg/app.jar`.
- `jpackage` app-image con:
  - `--module-path "$env:JAVA_HOME\jmods;$env:PATH_JAVAFX_JMODS"`
  - `--add-modules java.sql,java.xml,java.logging,java.desktop,jdk.crypto.ec,jdk.localedata,javafx.controls,javafx.fxml,javafx.web,javafx.graphics`
  - `--java-options "-Dprism.order=sw --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED"`
- `jpackage` MSI **sin** `--win-console` y con `--win-upgrade-uuid` fijo.
- **Variables requeridas**: `JAVA_HOME` y `PATH_JAVAFX_JMODS` (apunta a la carpeta *jmods* de JavaFX).

## Base de datos (SQLite)
- **Ruta definitiva**: `%APPDATA%\Pildoras\knowledgebase.db` (Roaming).
- **Migración** (primer arranque): desde `%LOCALAPPDATA%\Pildoras\knowledgebase.db` (mueve también `-wal` y `-shm`) **solo si** la de Roaming no existe o está vacía.
- **PRAGMAs al abrir**: `foreign_keys=ON`, `journal_mode=WAL`, `synchronous=NORMAL`, `busy_timeout=5000`.
- **Esquema**:
- `pildoras(id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned)`
- `tags(id, nombre)`
- `pildora_tag(pildora_id, tag_id)` (FKs, PK compuesta)
- **Tags en minúsculas**: `TagDao.findOrCreate()` normaliza a `lowerCase`.
- **Deduplicación automática** (startup):
- Reasigna `pildora_tag` al `MIN(id)` por `lower(nombre)`.
- `UPDATE tags SET nombre = lower(nombre)`.
- Elimina duplicados.
- Crea índice único **case-insensitive**:
  ```
  CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower ON tags(lower(nombre));
  ```

## UI / UX
- **Temas**: `base.css` + `theme-light.css` / `theme-dark.css`. Toggle de tema sincronizado con iconos sol/luna (Ikonli).
- **Listado**:
- Tabla paginada (20 por página), ordenación, filtros de texto y tags, modo AND/OR, favoritas, “pinned”.
- Doble clic abre detalle.
- **Atajos**: `Ctrl+N` (nueva), `Ctrl+E` (editar), `Supr` (borrar con confirmación),
  `Ctrl+F` / `Ctrl+T` (filtros), `PageUp/Down` o `Ctrl+←/→` (paginación),
  `Ctrl+O` (detalle), `Ctrl+Shift+F` (solo favoritas), `M` (toggle favorita).
- Botones con Ikonli.
- **Editor**:
- Chips de tags (FlowPane + input `txtTagInput`).
- Autocompletado con ControlsFX (`listAllLike`).
- Enter o coma → añade chip; botón ✕ elimina.
- Guardado: quita relaciones previas y vincula tags normalizados (`findOrCreate`).
- Snippets Markdown: botones + atajos `Ctrl+B/I/K/E`.
- **Colores chips**: deterministas con `ColorUtil.colorForTag(name, isDark)` (HSL→HEX) y `bestTextOn()`; se recalculan al cambiar de tema.
- **Detalle**:
- Título, fecha, chips con **los mismos colores** que el editor.
- **Exportación** arriba a la derecha: `MenuButton` con opciones **Markdown** y **HTML**.
  - Markdown: `PildoraExporter.exportMarkdown(...)`.
  - HTML: `PildoraExporter.exportHtml(..., extraCss)` con `extraCss` generado por `buildChipsCssForHtml()` para colores idénticos a la app.
- WebView con CSS embebido claro/oscuro (re-render al cambiar de tema).
- Barra inferior: **Borrar / Editar / Volver** (tooltips + atajos).

## Diálogos / Alert
- Se copian los **stylesheets** del `Scene` principal para mantener tema (método `decorate(Alert)`).
- Estilos dark en `theme-dark.css` (fondo, botones, botón por defecto con `-fx-accent`).
- **Barra de título custom** (undecorated):
- `decorate(Alert)` + `buildDialogTitleBar()`:
  - Quita decoraciones del sistema.
  - Header personalizado con título a la izquierda y botón ✕ a la derecha.
  - Drag en la barra para mover el diálogo.
  - Iconos copiados del `Stage` principal.

## Exportación
- **Detalle → Exportar**:
- **Markdown (.md)**: título, fecha, tags y contenido (en Markdown).
- **HTML (.html)**: plantilla cuidada; chips con colores consistentes (vía `extraCss`).
- **Próximo**: **Export/Import ZIP portable** (toda la BD + assets) desde Ajustes/menú. Import con confirmación y copia de seguridad.

## Notas de compatibilidad
- ControlsFX requiere `--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED`:
- En ejecución local (Eclipse, VM args).
- En `jpackage` (app-image) dentro de `--java-options` del script.