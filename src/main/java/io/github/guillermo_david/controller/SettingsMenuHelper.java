package io.github.guillermo_david.controller;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.db.TursoConfig;
import io.github.guillermo_david.db.TursoMigrationService;
import io.github.guillermo_david.importer.MarkdownImportService;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.javafx.ThemeManager.Theme;
import io.github.guillermo_david.security.SecurityService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

class SettingsMenuHelper {

    private final BorderPane root;
    private final SecurityService security;
    private final MenuButton btnSettings;
    private final Preferences prefs;
    private final String prefFontFamily;
    private final String prefFontSize;
    private final String defFontFamily;
    private final String defFontSize;

    private final BooleanSupplier ensureUnlocked;
    private final Runnable refrescarTabla;
    private final Consumer<Theme> setLogoFor;
    private final Consumer<Integer> setPaginaActual;

    SettingsMenuHelper(BorderPane root, SecurityService security, MenuButton btnSettings,
                       Preferences prefs,
                       String prefFontFamily, String prefFontSize,
                       String defFontFamily, String defFontSize,
                       BooleanSupplier ensureUnlocked, Runnable refrescarTabla,
                       Consumer<Theme> setLogoFor, Consumer<Integer> setPaginaActual) {
        this.root = root;
        this.security = security;
        this.btnSettings = btnSettings;
        this.prefs = prefs;
        this.prefFontFamily = prefFontFamily;
        this.prefFontSize = prefFontSize;
        this.defFontFamily = defFontFamily;
        this.defFontSize = defFontSize;
        this.ensureUnlocked = ensureUnlocked;
        this.refrescarTabla = refrescarTabla;
        this.setLogoFor = setLogoFor;
        this.setPaginaActual = setPaginaActual;
    }

    void initSettingsMenu() {
        btnSettings.getItems().clear();

        var gear = new FontIcon(FontAwesomeSolid.COG);
        btnSettings.setText(null);
        btnSettings.setGraphic(gear);
        btnSettings.setTooltip(new Tooltip("Ajustes"));
        btnSettings.getStyleClass().addAll("icon-btn", "primary");

        // --- Importar (.md) ---
        var miImport = new MenuItem("Importar (.md)…");
        miImport.setOnAction(e -> handleImportMarkdown());

        // --- Tipografías ---
        record Family(String label, String css) {}
        var families = List.of(
                new Family("System", "System"),
                new Family("Sans (Arial)", "Arial"),
                new Family("Segoe UI", "Segoe UI"),
                new Family("Helvetica", "Helvetica"),
                new Family("Verdana", "Verdana"),
                new Family("Tahoma", "Tahoma"),
                new Family("Serif (Georgia)", "Georgia"),
                new Family("Serif (Times)", "Times New Roman"),
                new Family("Monospace (Consolas)", "Consolas"),
                new Family("Monospace (Courier)", "Courier New"));
        var miFamily = new Menu("Tipografía");
        var tgFamily = new ToggleGroup();
        String currentFamily = prefs.get(prefFontFamily, defFontFamily);
        for (var f : families) {
            var r = new RadioMenuItem(f.label());
            r.setToggleGroup(tgFamily);
            r.setSelected(f.css().equalsIgnoreCase(currentFamily));
            r.setStyle("-fx-font-family: '" + f.css().replace("'", "\\'") + "';");
            r.setOnAction(e -> {
                prefs.put(prefFontFamily, f.css());
                applyTypographyNow();
            });
            miFamily.getItems().add(r);
        }

        // --- Tamaño letra ---
        var miSize = new Menu("Tamaño");
        var tgSize = new ToggleGroup();
        var sizes = List.of(
                new Family("Muy pequeño (10px)", "xsmall"),
                new Family("Pequeño (11px)", "small"),
                new Family("Normal (13px)", "normal"),
                new Family("Mediano (15px)", "medium"),
                new Family("Grande (17px)", "large"),
                new Family("Muy grande (20px)", "vlarge"));
        String currentSize = prefs.get(prefFontSize, defFontSize);
        for (var item : sizes) {
            var r = new RadioMenuItem(item.label);
            r.setToggleGroup(tgSize);
            r.setSelected(item.css.equalsIgnoreCase(currentSize));
            r.setOnAction(e -> {
                prefs.put(prefFontSize, item.css);
                applyTypographyNow();
            });
            miSize.getItems().add(r);
        }

        // --- Tema (color schemes) ---
        var miTheme = new Menu("Tema");
        var tgTheme = new ToggleGroup();
        String currentThemeId = ThemeManager.loadId();
        for (var entry : ThemeManager.getThemes().values()) {
            var r = new RadioMenuItem(entry.label());
            r.setToggleGroup(tgTheme);
            r.setSelected(entry.id().equals(currentThemeId));
            final String tid = entry.id();
            r.setOnAction(e -> {
                var scene = root.getScene();
                if (scene == null) return;
                ThemeManager.apply(scene, tid);
                ThemeManager.save(tid);
                setLogoFor.accept(ThemeManager.load());
                StatusBus.show("Tema: " + entry.label(), StatusBus.Type.INFO, Duration.seconds(2));
            });
            miTheme.getItems().add(r);
        }

        // =============== Submenú Seguridad ===============
        var miSecurity = new Menu("Seguridad");

        var miChangePin = new MenuItem(security.hasPin() ? "Cambiar PIN…" : "Crear PIN…");
        miChangePin.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    char[] np = PinDialogs.promptNewPin6(root);
                    if (np == null) return;
                    try {
                        security.setupPin(np);
                        StatusBus.show("PIN creado.", StatusBus.Type.SUCCESS, Duration.seconds(3));
                        initSettingsMenu();
                    } finally {
                        Arrays.fill(np, '\0');
                    }
                } else {
                    if (!ensureUnlocked.getAsBoolean()) return;
                    char[] np = PinDialogs.promptNewPin6(root);
                    if (np == null) return;
                    try {
                        security.changePin(null, np);
                        StatusBus.show("PIN cambiado.", StatusBus.Type.SUCCESS, Duration.seconds(3));
                    } finally {
                        Arrays.fill(np, '\0');
                    }
                }
            } catch (Exception ex) {
                StatusBus.show("No se pudo " + (security.hasPin() ? "cambiar" : "crear") + " el PIN.",
                        StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miSetQuestion = new MenuItem("Configurar pregunta de seguridad…");
        miSetQuestion.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    StatusBus.show("Primero crea un PIN.", StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }
                if (!ensureUnlocked.getAsBoolean()) return;
                showSetupSecurityQuestion();
            } catch (Exception ex) {
                StatusBus.show("No se pudo guardar la pregunta.", StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miResetByAnswer = new MenuItem("Restablecer PIN con respuesta…");
        miResetByAnswer.setOnAction(e -> {
            try {
                if (!security.hasSecurityQuestion()) {
                    StatusBus.show("No hay pregunta de seguridad configurada.", StatusBus.Type.WARN,
                            Duration.seconds(3));
                    return;
                }
                if (security.isLockedOut()) {
                    showLockoutModal();
                    return;
                }

                String q = security.getSecurityQuestion();
                char[] ans = PinDialogs.promptAnswer(root, q);
                if (ans == null) return;
                char[] np = PinDialogs.promptNewPin6(root);
                if (np == null) {
                    Arrays.fill(ans, '\0');
                    return;
                }

                boolean ok;
                try {
                    ok = security.resetPinWithAnswer(ans, np);
                } finally {
                    Arrays.fill(ans, '\0');
                    Arrays.fill(np, '\0');
                }

                if (ok) {
                    StatusBus.show("PIN restablecido.", StatusBus.Type.SUCCESS, Duration.seconds(3));
                    initSettingsMenu();
                } else if (security.isLockedOut()) {
                    showLockoutModal();
                } else {
                    StatusBus.show("Respuesta incorrecta.", StatusBus.Type.ERROR, Duration.seconds(3));
                }
            } catch (Exception ex) {
                StatusBus.show("No se pudo restablecer el PIN.", StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miGenRecovery = new MenuItem("Generar código de recuperación…");
        miGenRecovery.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    StatusBus.show("Primero crea un PIN.", StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }
                if (!ensureUnlocked.getAsBoolean()) return;
                showGenerateRecoveryCode();
            } catch (Exception ex) {
                StatusBus.show("No se pudo generar el código.", StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miResetByCode = new MenuItem("Restablecer PIN con código…");
        miResetByCode.setOnAction(e -> {
            try {
                if (!security.hasRecoveryCode()) {
                    StatusBus.show("No hay código de recuperación activo.", StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }
                if (security.isLockedOut()) {
                    showLockoutModal();
                    return;
                }

                String code = PinDialogs.promptRecoveryCode(root);
                if (code == null || code.isBlank()) return;
                char[] np = PinDialogs.promptNewPin6(root);
                if (np == null) return;

                boolean ok;
                try {
                    ok = security.resetPinWithRecoveryCode(code, np);
                } finally {
                    Arrays.fill(np, '\0');
                }

                if (ok) {
                    StatusBus.show("PIN restablecido con código.", StatusBus.Type.SUCCESS, Duration.seconds(3));
                    initSettingsMenu();
                } else if (security.isLockedOut()) {
                    showLockoutModal();
                } else {
                    StatusBus.show("Código incorrecto o expirado.", StatusBus.Type.ERROR, Duration.seconds(3));
                }
            } catch (Exception ex) {
                StatusBus.show("No se pudo restablecer el PIN.", StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miLock = new MenuItem("Bloquear ahora");
        miLock.setOnAction(e -> {
            security.lockNow();
            StatusBus.show("Sesión bloqueada. Se pedirá PIN al acceder a contenido protegido.", StatusBus.Type.INFO,
                    Duration.seconds(3));
        });

        miSecurity.getItems().addAll(miChangePin, new SeparatorMenuItem(), miSetQuestion, miResetByAnswer,
                new SeparatorMenuItem(), miGenRecovery, miResetByCode, new SeparatorMenuItem(), miLock);

        // =============== Submenú Base de datos ===============
        var miDatabase = new Menu("Base de datos");

        var miTurso = new MenuItem(TursoConfig.isConfigured() ? "Turso: conectado" : "Configurar Turso…");
        miTurso.setOnAction(e -> showTursoDialog(miTurso));

        var miMigrate = new MenuItem("Migrar datos locales a Turso…");
        miMigrate.setOnAction(e -> handleMigrateToTurso());

        var miTursoGuide = new MenuItem("Cómo configurar Turso…");
        miTursoGuide.setOnAction(e -> showTursoGuideDialog());

        var miBackup = new MenuItem("Exportar backup…");
        miBackup.setOnAction(e -> handleExportBackup());

        var miRestore = new MenuItem("Importar backup…");
        miRestore.setOnAction(e -> handleImportBackup());

        miDatabase.getItems().addAll(miImport, new SeparatorMenuItem(),
                miTurso, miMigrate, new SeparatorMenuItem(),
                miBackup, miRestore, new SeparatorMenuItem(), miTursoGuide);

        // =============== Submenú Debug ===============
        var miDebug = new Menu("Debug");
        miDebug.getItems().add(createOpenLogMenuItem());

        var miShortcuts = new MenuItem("Atajos de teclado…");
        miShortcuts.setOnAction(e -> showShortcutsDialog());

        btnSettings.getItems().setAll(
                miFamily,
                miSize,
                new SeparatorMenuItem(),
                miTheme,
                new SeparatorMenuItem(),
                miDatabase,
                new SeparatorMenuItem(),
                miSecurity,
                new SeparatorMenuItem(),
                miShortcuts,
                new SeparatorMenuItem(),
                miDebug);
    }

    void applyTypographyNow() {
        Runnable apply = () -> {
            var scene = root.getScene();
            if (scene == null) {
                Platform.runLater(this::applyTypographyNow);
                return;
            }

            String family = prefs.get(prefFontFamily, defFontFamily);
            String sizeKey = prefs.get(prefFontSize, defFontSize);

            double px = switch (sizeKey) {
                case "xsmall" -> 10.0;
                case "small" -> 11.0;
                case "medium" -> 15.0;
                case "large" -> 17.0;
                case "vlarge" -> 20.0;
                default -> 13.0;
            };

            scene.getRoot().setStyle("""
                    -fx-font-family: '%s';
                    -fx-font-size: %spx;
                """.formatted(family.replace("'", "\\'"), px));
        };
        apply.run();
    }

    private void showLockoutModal() {
        long secs = Math.max(0L, (security.lockoutRemainingMillis() + 999) / 1000);
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Intentos agotados");
        a.setHeaderText("Has excedido los intentos del PIN");
        a.setContentText("Podrás volver a intentarlo en " + secs + " segundo" + (secs == 1 ? "" : "s") + ".");
        Dialogs.decorate(a, root);
        a.showAndWait();
    }

    private void showSetupSecurityQuestion() {
        if (!ensureUnlocked.getAsBoolean()) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Pregunta de seguridad");
        dlg.setHeaderText("Configura una pregunta y su respuesta para recuperar el PIN");
        Dialogs.decorate(dlg, root);

        TextField txtQ = new TextField();
        txtQ.setPromptText("Ej: Nombre de tu primera mascota");

        PasswordField pfA = new PasswordField();
        pfA.setPromptText("Respuesta");

        PasswordField pfA2 = new PasswordField();
        pfA2.setPromptText("Repite la respuesta");

        VBox content = new VBox(8, new Label("Pregunta:"), txtQ, new Label("Respuesta:"), pfA,
                new Label("Confirmar respuesta:"), pfA2);
        content.setPrefWidth(420);
        dlg.getDialogPane().setContent(content);

        ButtonType OK = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(OK, CANCEL);

        Node okBtn = dlg.getDialogPane().lookupButton(OK);
        okBtn.setDisable(true);
        Runnable validate = () -> {
            boolean valid = !txtQ.getText().isBlank() && !pfA.getText().isBlank()
                    && pfA.getText().equals(pfA2.getText());
            okBtn.setDisable(!valid);
        };
        txtQ.textProperty().addListener((o, a, b) -> validate.run());
        pfA.textProperty().addListener((o, a, b) -> validate.run());
        pfA2.textProperty().addListener((o, a, b) -> validate.run());
        Platform.runLater(txtQ::requestFocus);

        dlg.setResultConverter(bt -> bt);

        var res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != OK) return;

        char[] ans = pfA.getText().toCharArray();
        try {
            security.setSecurityQuestion(txtQ.getText().trim(), ans);
            StatusBus.show("Pregunta de seguridad guardada.", StatusBus.Type.SUCCESS, Duration.seconds(3));
        } catch (Exception ex) {
            StatusBus.show("No se pudo guardar la pregunta: " + ex.getMessage(), StatusBus.Type.ERROR,
                    Duration.seconds(4));
        } finally {
            Arrays.fill(ans, '\0');
        }
    }

    private void showGenerateRecoveryCode() {
        if (!ensureUnlocked.getAsBoolean()) return;

        String code;
        try {
            code = security.generateRecoveryCode();
        } catch (Exception ex) {
            StatusBus.show("No se pudo generar el código: " + ex.getMessage(), StatusBus.Type.ERROR,
                    Duration.seconds(4));
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Código de recuperación");
        dlg.setHeaderText("Guarda este código en un lugar seguro.\nTe permitirá restablecer el PIN si lo olvidas.");
        Dialogs.decorate(dlg, root);

        TextField txt = new TextField(code);
        txt.setEditable(false);
        txt.setStyle("-fx-font-family: 'Consolas', 'Menlo', 'Courier New', monospace; -fx-font-size: 14px;");
        Button btnCopy = new Button("Copiar");
        btnCopy.setOnAction(ev -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(code);
            Clipboard.getSystemClipboard().setContent(cc);
            StatusBus.show("Código copiado al portapapeles.", StatusBus.Type.INFO, Duration.seconds(2));
        });

        VBox box = new VBox(12, new Label("Código de recuperación (mostrado una sola vez):"), new HBox(8, txt, btnCopy),
                new Label("⚠ Si lo pierdes y olvidas el PIN, no podrás recuperar el acceso."));
        box.setPrefWidth(480);
        dlg.getDialogPane().setContent(box);

        dlg.getDialogPane().getButtonTypes().setAll(new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dlg.showAndWait();
    }

    private void showTursoDialog(MenuItem miTurso) {
        if (TursoConfig.isConfigured()) {
            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
            info.setTitle("Turso");
            info.setHeaderText("Conexión activa");
            info.setContentText("URL: " + TursoConfig.getUrl()
                + "\n\nPulsa Aceptar para desconectar y volver a SQLite local.");
            Dialogs.decorate(info, root);
            info.showAndWait().ifPresent(res -> {
                if (res.getButtonData().isDefaultButton()) {
                    TursoConfig.clear();
                    DatabaseHelper.reconnect();
                    miTurso.setText("Configurar Turso…");
                    refrescarTabla.run();
                    StatusBus.show("Desconectado de Turso. Usando BD local.",
                        StatusBus.Type.INFO, Duration.seconds(3));
                }
            });
            return;
        }

        var dlg = new Alert(Alert.AlertType.NONE);
        dlg.setTitle("Configurar Turso");

        var lblUrl = new Label("URL de la base de datos:");
        var txtUrl = new TextField();
        txtUrl.setPromptText("libsql://mi-db.turso.io");
        txtUrl.setPrefWidth(420);

        var lblToken = new Label("Token de autenticación:");
        var txtToken = new TextField();
        txtToken.setPromptText("eyJhbGciOi...");
        txtToken.setPrefWidth(420);

        var content = new VBox(8, lblUrl, txtUrl, lblToken, txtToken);
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(480);

        Dialogs.decorate(dlg, root);

        dlg.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                String url = txtUrl.getText().trim();
                String token = txtToken.getText().trim();
                if (url.isEmpty() || token.isEmpty()) {
                    StatusBus.show("URL y token son obligatorios.",
                        StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }

                try {
                    String jdbcUrl = TursoConfig.toJdbcUrl(url);
                    try (var conn = java.sql.DriverManager.getConnection(jdbcUrl, null, token);
                         var stmt = conn.createStatement();
                         var rs = stmt.executeQuery("SELECT 1")) {
                    }
                } catch (Exception ex) {
                    StatusBus.show("Error de conexión: " + ex.getMessage(),
                        StatusBus.Type.ERROR, Duration.seconds(5));
                    ex.printStackTrace();
                    return;
                }

                TursoConfig.save(url, token);
                DatabaseHelper.reconnect();
                miTurso.setText("Turso: conectado");
                refrescarTabla.run();
                StatusBus.show("Conectado a Turso correctamente.",
                    StatusBus.Type.SUCCESS, Duration.seconds(3));
            }
        });
    }

    private void showShortcutsDialog() {
        var sb = new StringBuilder();

        sb.append("LISTADO\n")
          .append("───────\n")
          .append("Rueda ratón                – Desplazar la lista\n")
          .append("PageUp / Ctrl+←            – Página anterior\n")
          .append("PageDown / Ctrl+→          – Página siguiente\n")
          .append("Ctrl+F                     – Foco en filtro de texto\n")
          .append("Ctrl+T                     – Foco en filtro de tags\n")
          .append("Ctrl+N                     – Nueva píldora\n")
          .append("Ctrl+O                     – Abrir detalle de la fila seleccionada\n")
          .append("Ctrl+E                     – Editar la fila seleccionada\n")
          .append("Supr                       – Eliminar la fila seleccionada\n")
          .append("M                          – (Tabla enfocada) Alternar favorita\n")
          .append("Ctrl+Shift+F               – Mostrar solo favoritas (toggle)\n")
          .append("Esc                        – Limpiar filtros (si hay foco en filtros) / Volver\n")
          .append("Doble clic fila            – Abrir detalle\n\n")

          .append("DETALLE\n")
          .append("───────\n")
          .append("Ctrl+E                     – Editar\n")
          .append("Supr                       – Eliminar\n")
          .append("Esc                        – Volver al listado\n")
          .append("Click en tag               – Filtrar listado por ese tag\n\n")

          .append("EDITOR\n")
          .append("──────\n")
          .append("Ctrl+S / Ctrl+Enter       – Guardar\n")
          .append("Esc                       – Cancelar y volver\n")
          .append("Ctrl+B                    – Insertar **negrita**\n")
          .append("Ctrl+I                    – Insertar *cursiva*\n")
          .append("Ctrl+K                    – Insertar [enlace](https://)\n")
          .append("Ctrl+E                    – Insertar `código`\n")
          .append("Tab / Shift+Tab           – Indentar / desindentar líneas (listas)\n")
          .append("Vista previa              – Conmutador en la barra de herramientas\n")
          .append("Enter / ','               – Confirmar tag en el campo de tags\n")
          .append("Backspace (tags)          – Con input vacío, borrar el último tag\n\n")

          .append("BORRADORES\n")
          .append("──────────\n")
          .append("Desde Ajustes → Borradores\n")
          .append("  Nuevo borrador…         – Diálogo rápido\n")
          .append("  Bandeja de borradores…  – Lista de borradores\n")
          .append("Bandeja\n")
          .append("  Doble clic fila         – Editar borrador\n")
          .append("  Supr                    – Eliminar borrador seleccionado\n")
          .append("Diálogo rápido de borrador\n")
          .append("  Ctrl+S / Ctrl+Enter     – Guardar\n");

        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Atajos de teclado");
        alert.setHeaderText(null);

        var ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setFocusTraversable(false);
        ta.setPrefColumnCount(56);
        ta.setPrefRowCount(28);
        ta.setStyle("-fx-font-family: 'Consolas','Monospaced'; -fx-font-size: 13px;");

        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setPrefWidth(620);

        Dialogs.decorate(alert, root);
        alert.showAndWait();
    }

    private void showTursoGuideDialog() {
        Alert guide = new Alert(AlertType.INFORMATION);
        guide.setTitle("Cómo configurar Turso");
        guide.setHeaderText(null);

        var content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        var title = new Label("☁ Sincronización en la nube con Turso");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var intro = new Label(
                "Píldoras guarda tus datos de forma local por defecto. "
                + "Si quieres acceder a tus píldoras desde varios equipos, "
                + "puedes sincronizarlas con Turso, un servicio gratuito de "
                + "bases de datos en la nube.\n\n"
                + "Sin Turso, la app funciona perfectamente en local. "
                + "Turso es totalmente opcional.");
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 13px;");

        var stepsTitle = new Label("Pasos para configurar Turso:");
        stepsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8 0 0 0;");

        var steps = new Label(
                "1. Crea una cuenta gratuita en turso.tech\n"
                + "2. Instala la herramienta de Turso en tu terminal:\n"
                + "     curl -sSfL https://get.tur.so/install.sh | bash\n"
                + "3. Inicia sesión: turso auth login\n"
                + "4. Crea una base de datos: turso db create pildoras\n"
                + "5. Copia la URL: turso db show pildoras --url\n"
                + "     (Ejemplo: libsql://pildoras-tu-usuario.turso.io)\n"
                + "6. Genera un token: turso db tokens create pildoras\n"
                + "7. En Píldoras: Ajustes → Base de datos → Configurar Turso\n"
                + "     Pega la URL y el token, y pulsa Aceptar.\n"
                + "8. (Opcional) Migra tus datos locales:\n"
                + "     Ajustes → Base de datos → Migrar datos locales a Turso");
        steps.setWrapText(true);
        steps.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");

        var linkBox = new javafx.scene.layout.HBox(6);
        linkBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        linkBox.setPadding(new javafx.geometry.Insets(6, 0, 0, 0));
        var linkLabel = new Label("Más información:");
        linkLabel.setStyle("-fx-font-size: 13px;");
        var linkBtn = new Hyperlink("turso.tech");
        linkBtn.setStyle("-fx-font-size: 13px;");
        linkBtn.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://turso.tech")); }
            catch (Exception ignored) {}
        });
        linkBox.getChildren().addAll(linkLabel, linkBtn);

        content.getChildren().addAll(title, intro, stepsTitle, steps, linkBox);

        var scrollPane = new javafx.scene.control.ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background-color: transparent;");

        guide.getDialogPane().setContent(scrollPane);
        guide.getDialogPane().setPrefWidth(580);
        Dialogs.decorate(guide, root);
        guide.showAndWait();
    }

    private void handleExportBackup() {
        var fc = new FileChooser();
        fc.setTitle("Exportar backup");
        fc.setInitialFileName("pildoras-backup.json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            io.github.guillermo_david.export.BackupService.exportToFile(file);
            StatusBus.show("Backup exportado correctamente.", StatusBus.Type.SUCCESS, Duration.seconds(4));
        } catch (Exception ex) {
            ex.printStackTrace();
            StatusBus.show("Error exportando: " + ex.getMessage(), StatusBus.Type.ERROR, Duration.seconds(5));
        }
    }

    private void handleImportBackup() {
        var confirm = new Alert(AlertType.WARNING);
        confirm.setTitle("Importar backup");
        confirm.setHeaderText("¿Estás seguro?");
        confirm.setContentText("Se eliminarán TODOS los datos actuales y se reemplazarán con el backup.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.decorate(confirm, root);
        var res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        var fc = new FileChooser();
        fc.setTitle("Importar backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = fc.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            int[] counts = io.github.guillermo_david.export.BackupService.importFromFile(file);
            refrescarTabla.run();
            StatusBus.show("Importado: " + counts[0] + " píldoras, " + counts[1] + " tags, "
                    + counts[2] + " borradores.", StatusBus.Type.SUCCESS, Duration.seconds(5));
        } catch (Exception ex) {
            ex.printStackTrace();
            StatusBus.show("Error importando: " + ex.getMessage(), StatusBus.Type.ERROR, Duration.seconds(5));
        }
    }

    private void handleImportMarkdown() {
        var scene = root.getScene();
        if (scene == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Importar (.md)...");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown (*.md)", "*.md"),
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        List<File> files = fc.showOpenMultipleDialog(scene.getWindow());
        if (files == null || files.isEmpty()) return;

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                var svc = new MarkdownImportService();
                int ok = 0, fail = 0;

                for (int i = 0; i < files.size(); i++) {
                    try {
                        svc.importFile(files.get(i));
                        ok++;
                    } catch (Exception ex) {
                        fail++;
                    }
                    updateProgress(i + 1, files.size());
                }

                final int okCount = ok;
                final int failCount = fail;
                final int totalCount = okCount + failCount;

                Platform.runLater(() -> {
                    setPaginaActual.accept(1);
                    refrescarTabla.run();
                    StatusBus.show(
                        (failCount == 0
                            ? ("Importadas " + okCount + " píldoras.")
                            : ("Importadas " + okCount + " de " + totalCount + " (" + failCount + " con error).")),
                        (failCount == 0 ? StatusBus.Type.SUCCESS : StatusBus.Type.WARN),
                        Duration.seconds(5)
                    );
                });
                return null;
            }
        };

        task.setOnRunning(e -> root.setDisable(true));
        task.setOnSucceeded(e -> root.setDisable(false));
        task.setOnFailed(e -> {
            root.setDisable(false);
            StatusBus.show("Error inesperado en importación.", StatusBus.Type.ERROR, Duration.seconds(5));
        });

        new Thread(task, "ImportMarkdown").start();
    }

    private void handleMigrateToTurso() {
        if (!TursoConfig.isConfigured()) {
            var alert = new Alert(AlertType.WARNING);
            alert.setTitle("Turso no configurado");
            alert.setHeaderText(null);
            alert.setContentText("Configura la conexion a Turso antes de migrar.");
            Dialogs.decorate(alert, root);
            alert.showAndWait();
            return;
        }

        var confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Migrar datos locales a Turso");
        confirm.setHeaderText("Importar todos los datos locales a Turso?");
        confirm.setContentText("Se copiaran pildoras, tags, borradores y enlaces "
            + "desde la base de datos local a Turso (INSERT OR REPLACE).\n\n"
            + "Los datos existentes en Turso con el mismo ID seran sobrescritos.");
        Dialogs.decorate(confirm, root);
        var choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        Task<TursoMigrationService.Result> task = new Task<>() {
            @Override protected TursoMigrationService.Result call() throws Exception {
                return TursoMigrationService.migrateLocalToTurso();
            }
        };

        task.setOnRunning(e -> root.setDisable(true));
        task.setOnSucceeded(e -> {
            root.setDisable(false);
            TursoMigrationService.Result r = task.getValue();
            setPaginaActual.accept(1);
            refrescarTabla.run();
            StatusBus.show(
                "Migracion completada: " + r.total() + " registros "
                    + "(pildoras=" + r.pildoras + ", tags=" + r.tags
                    + ", relaciones=" + r.pildoraTag
                    + ", borradores=" + r.drafts + ", enlaces=" + r.pildoraLink + ")",
                StatusBus.Type.SUCCESS, Duration.seconds(8));
        });
        task.setOnFailed(e -> {
            root.setDisable(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String detail = "desconocido";
            if (ex != null) {
                detail = ex.getClass().getSimpleName();
                if (ex.getMessage() != null) detail += ": " + ex.getMessage();
                if (ex.getCause() != null) {
                    detail += " [causa: " + ex.getCause().getClass().getSimpleName();
                    if (ex.getCause().getMessage() != null) detail += ": " + ex.getCause().getMessage();
                    detail += "]";
                }
            }
            StatusBus.show("Error en migracion: " + detail,
                StatusBus.Type.ERROR, Duration.seconds(15));
        });

        new Thread(task, "MigrateToTurso").start();
    }

    private MenuItem createOpenLogMenuItem() {
        var mi = new MenuItem("Abrir log...");
        mi.setOnAction(e -> {
            var logPath = io.github.guillermo_david.util.AppLogger.getLogPath();
            if (java.nio.file.Files.exists(logPath)) {
                try {
                    java.awt.Desktop.getDesktop().open(logPath.toFile());
                } catch (Exception ex) {
                    StatusBus.show("No se pudo abrir: " + logPath,
                        StatusBus.Type.ERROR, Duration.seconds(5));
                }
            } else {
                StatusBus.show("Log no encontrado: " + logPath,
                    StatusBus.Type.WARN, Duration.seconds(5));
            }
        });
        return mi;
    }
}
