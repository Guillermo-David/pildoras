package io.github.guillermo_david.controller;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.util.I18n;
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

    record Config(BorderPane root, SecurityService security, MenuButton btnSettings, Preferences prefs,
                  String prefFontFamily, String prefFontSize, String defFontFamily, String defFontSize,
                  BooleanSupplier ensureUnlocked, Runnable refrescarTabla,
                  Consumer<Theme> setLogoFor, Consumer<Integer> setPaginaActual,
                  Runnable showColumnSelector) {}

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
    private final Runnable showColumnSelector;

    SettingsMenuHelper(Config c) {
        this.root = c.root();
        this.security = c.security();
        this.btnSettings = c.btnSettings();
        this.prefs = c.prefs();
        this.prefFontFamily = c.prefFontFamily();
        this.prefFontSize = c.prefFontSize();
        this.defFontFamily = c.defFontFamily();
        this.defFontSize = c.defFontSize();
        this.ensureUnlocked = c.ensureUnlocked();
        this.refrescarTabla = c.refrescarTabla();
        this.setLogoFor = c.setLogoFor();
        this.setPaginaActual = c.setPaginaActual();
        this.showColumnSelector = c.showColumnSelector();
    }

    void initSettingsMenu() {
        btnSettings.getItems().clear();

        var gear = new FontIcon(FontAwesomeSolid.COG);
        btnSettings.setText(null);
        btnSettings.setGraphic(gear);
        btnSettings.setTooltip(new Tooltip(I18n.get("tooltip.settings")));
        btnSettings.getStyleClass().addAll("icon-btn", "primary");

        // --- Importar (.md) ---
        var miImport = new MenuItem(I18n.get("menu.import.md"));
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
        var miFamily = new Menu(I18n.get("menu.typography"));
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
        var miSize = new Menu(I18n.get("menu.font.size"));
        var tgSize = new ToggleGroup();
        var sizes = List.of(
                new Family(I18n.get("font.size.xsmall"), "xsmall"),
                new Family(I18n.get("font.size.small"), "small"),
                new Family(I18n.get("font.size.normal"), "normal"),
                new Family(I18n.get("font.size.medium"), "medium"),
                new Family(I18n.get("font.size.large"), "large"),
                new Family(I18n.get("font.size.vlarge"), "vlarge"));
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
        var miTheme = new Menu(I18n.get("menu.theme"));
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
                StatusBus.show(I18n.get("status.theme.changed", entry.label()), StatusBus.Type.INFO, Duration.seconds(2));
            });
            miTheme.getItems().add(r);
        }

        // =============== Idioma ===============
        Menu langMenu = new Menu(I18n.get("menu.language"));
        ToggleGroup langGroup = new ToggleGroup();
        RadioMenuItem langEs = new RadioMenuItem("Español");
        langEs.setToggleGroup(langGroup);
        RadioMenuItem langEn = new RadioMenuItem("English");
        langEn.setToggleGroup(langGroup);
        if (I18n.getLocale().getLanguage().equals("es")) langEs.setSelected(true);
        else langEn.setSelected(true);
        langEs.setOnAction(e -> {
            I18n.setLocale(new java.util.Locale("es"));
            showLanguageRestartAlert();
        });
        langEn.setOnAction(e -> {
            I18n.setLocale(java.util.Locale.ENGLISH);
            showLanguageRestartAlert();
        });
        langMenu.getItems().addAll(langEs, langEn);

        // =============== Submenú Seguridad ===============
        var miSecurity = new Menu(I18n.get("menu.security"));

        var miChangePin = new MenuItem(security.hasPin() ? I18n.get("menu.pin.change") : I18n.get("menu.pin.create"));
        miChangePin.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    char[] np = PinDialogs.promptNewPin6(root);
                    if (np == null) return;
                    try {
                        security.setupPin(np);
                        StatusBus.show(I18n.get("status.pin.created"), StatusBus.Type.SUCCESS, Duration.seconds(3));
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
                        StatusBus.show(I18n.get("status.pin.changed"), StatusBus.Type.SUCCESS, Duration.seconds(3));
                    } finally {
                        Arrays.fill(np, '\0');
                    }
                }
            } catch (Exception ex) {
                StatusBus.show(security.hasPin() ? I18n.get("status.pin.change.failed") : I18n.get("status.pin.create.failed"),
                        StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miSetQuestion = new MenuItem(I18n.get("menu.security.question.setup"));
        miSetQuestion.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    StatusBus.show(I18n.get("status.pin.create.first"), StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }
                if (!ensureUnlocked.getAsBoolean()) return;
                showSetupSecurityQuestion();
            } catch (Exception ex) {
                StatusBus.show(I18n.get("status.security.question.save.failed"), StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miResetByAnswer = new MenuItem(I18n.get("menu.pin.reset.answer"));
        miResetByAnswer.setOnAction(e -> {
            try {
                if (!security.hasSecurityQuestion()) {
                    StatusBus.show(I18n.get("status.no.security.question"), StatusBus.Type.WARN,
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
                    StatusBus.show(I18n.get("status.pin.reset"), StatusBus.Type.SUCCESS, Duration.seconds(3));
                    initSettingsMenu();
                } else if (security.isLockedOut()) {
                    showLockoutModal();
                } else {
                    StatusBus.show(I18n.get("status.answer.incorrect"), StatusBus.Type.ERROR, Duration.seconds(3));
                }
            } catch (Exception ex) {
                StatusBus.show(I18n.get("status.pin.reset.failed"), StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miGenRecovery = new MenuItem(I18n.get("menu.recovery.code.generate"));
        miGenRecovery.setOnAction(e -> {
            try {
                if (!security.hasPin()) {
                    StatusBus.show(I18n.get("status.pin.create.first"), StatusBus.Type.WARN, Duration.seconds(3));
                    return;
                }
                if (!ensureUnlocked.getAsBoolean()) return;
                showGenerateRecoveryCode();
            } catch (Exception ex) {
                StatusBus.show(I18n.get("status.recovery.code.generate.failed"), StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miResetByCode = new MenuItem(I18n.get("menu.pin.reset.code"));
        miResetByCode.setOnAction(e -> {
            try {
                if (!security.hasRecoveryCode()) {
                    StatusBus.show(I18n.get("status.no.recovery.code"), StatusBus.Type.WARN, Duration.seconds(3));
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
                    StatusBus.show(I18n.get("status.pin.reset.with.code"), StatusBus.Type.SUCCESS, Duration.seconds(3));
                    initSettingsMenu();
                } else if (security.isLockedOut()) {
                    showLockoutModal();
                } else {
                    StatusBus.show(I18n.get("status.code.incorrect.expired"), StatusBus.Type.ERROR, Duration.seconds(3));
                }
            } catch (Exception ex) {
                StatusBus.show(I18n.get("status.pin.reset.failed"), StatusBus.Type.ERROR, Duration.seconds(4));
            }
        });

        var miLock = new MenuItem(I18n.get("menu.lock.now"));
        miLock.setOnAction(e -> {
            security.lockNow();
            StatusBus.show(I18n.get("status.session.locked"), StatusBus.Type.INFO,
                    Duration.seconds(3));
        });

        miSecurity.getItems().addAll(miChangePin, new SeparatorMenuItem(), miSetQuestion, miResetByAnswer,
                new SeparatorMenuItem(), miGenRecovery, miResetByCode, new SeparatorMenuItem(), miLock);

        // =============== Submenú Base de datos ===============
        var miDatabase = new Menu(I18n.get("menu.database"));

        var miTurso = new MenuItem(TursoConfig.isConfigured() ? I18n.get("menu.turso.connected") : I18n.get("menu.turso.configure"));
        miTurso.setOnAction(e -> showTursoDialog(miTurso));

        var miMigrate = new MenuItem(I18n.get("menu.turso.migrate"));
        miMigrate.setOnAction(e -> handleMigrateToTurso());

        var miTursoGuide = new MenuItem(I18n.get("menu.turso.guide"));
        miTursoGuide.setOnAction(e -> InfoDialogs.showTursoGuideDialog(root));

        var miBackup = new MenuItem(I18n.get("menu.backup.export"));
        miBackup.setOnAction(e -> handleExportBackup());

        var miRestore = new MenuItem(I18n.get("menu.backup.import"));
        miRestore.setOnAction(e -> handleImportBackup());

        miDatabase.getItems().addAll(miImport, new SeparatorMenuItem(),
                miTurso, miMigrate, new SeparatorMenuItem(),
                miBackup, miRestore, new SeparatorMenuItem(), miTursoGuide);

        // =============== Submenú Debug ===============
        var miDebug = new Menu(I18n.get("menu.debug"));
        miDebug.getItems().add(createOpenLogMenuItem());

        var miShortcuts = new MenuItem(I18n.get("menu.shortcuts"));
        miShortcuts.setOnAction(e -> InfoDialogs.showShortcutsDialog(root));

        var miColumns = new MenuItem(I18n.get("menu.columns"));
        var colIcon = new FontIcon(FontAwesomeSolid.COLUMNS);
        colIcon.setIconSize(14);
        miColumns.setGraphic(colIcon);
        miColumns.setOnAction(e -> { if (showColumnSelector != null) showColumnSelector.run(); });

        var miStats = new MenuItem(I18n.get("menu.statistics"));
        var statsIcon = new FontIcon(FontAwesomeSolid.CHART_BAR);
        statsIcon.setIconSize(14);
        miStats.setGraphic(statsIcon);
        miStats.setOnAction(e -> InfoDialogs.showStatisticsDialog(root));

        var miAbout = new MenuItem(I18n.get("menu.about"));
        var aboutIcon = new FontIcon(FontAwesomeSolid.INFO_CIRCLE);
        aboutIcon.setIconSize(14);
        miAbout.setGraphic(aboutIcon);
        miAbout.setOnAction(e -> InfoDialogs.showAboutDialog(root));

        var miHelp = new MenuItem(I18n.get("menu.help"));
        var helpIcon = new FontIcon(FontAwesomeSolid.QUESTION_CIRCLE);
        helpIcon.setIconSize(14);
        miHelp.setGraphic(helpIcon);
        miHelp.setOnAction(e -> InfoDialogs.showHelpDialog(root));

        btnSettings.getItems().setAll(
                miFamily,
                miSize,
                new SeparatorMenuItem(),
                miTheme,
                langMenu,
                new SeparatorMenuItem(),
                miColumns,
                new SeparatorMenuItem(),
                miDatabase,
                new SeparatorMenuItem(),
                miSecurity,
                new SeparatorMenuItem(),
                miShortcuts,
                miStats,
                new SeparatorMenuItem(),
                miHelp,
                miAbout,
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
        a.setTitle(I18n.get("alert.lockout.title2"));
        a.setHeaderText(I18n.get("alert.lockout.header"));
        a.setContentText(I18n.get("alert.lockout.content", secs));
        Dialogs.decorate(a, root);
        a.showAndWait();
    }

    private void showSetupSecurityQuestion() {
        if (!ensureUnlocked.getAsBoolean()) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("dialog.security.question.title"));
        dlg.setHeaderText(I18n.get("dialog.security.question.header"));
        Dialogs.decorate(dlg, root);

        TextField txtQ = new TextField();
        txtQ.setPromptText(I18n.get("dialog.security.question.prompt"));

        PasswordField pfA = new PasswordField();
        pfA.setPromptText(I18n.get("dialog.security.answer.prompt"));

        PasswordField pfA2 = new PasswordField();
        pfA2.setPromptText(I18n.get("dialog.security.answer.confirm.prompt"));

        VBox content = new VBox(8, new Label(I18n.get("label.question")), txtQ, new Label(I18n.get("label.answer")), pfA,
                new Label(I18n.get("label.answer.confirm")), pfA2);
        content.setPrefWidth(420);
        dlg.getDialogPane().setContent(content);

        ButtonType OK = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType CANCEL = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
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
            StatusBus.show(I18n.get("status.security.question.saved"), StatusBus.Type.SUCCESS, Duration.seconds(3));
        } catch (Exception ex) {
            StatusBus.show(I18n.get("status.security.question.save.error", ex.getMessage()), StatusBus.Type.ERROR,
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
            StatusBus.show(I18n.get("status.recovery.code.error", ex.getMessage()), StatusBus.Type.ERROR,
                    Duration.seconds(4));
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("dialog.recovery.code.title"));
        dlg.setHeaderText(I18n.get("dialog.recovery.code.header"));
        Dialogs.decorate(dlg, root);

        TextField txt = new TextField(code);
        txt.setEditable(false);
        txt.setStyle("-fx-font-family: 'Consolas', 'Menlo', 'Courier New', monospace; -fx-font-size: 14px;");
        Button btnCopy = new Button(I18n.get("button.copy"));
        btnCopy.setOnAction(ev -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(code);
            Clipboard.getSystemClipboard().setContent(cc);
            StatusBus.show(I18n.get("status.code.copied"), StatusBus.Type.INFO, Duration.seconds(2));
        });

        VBox box = new VBox(12, new Label(I18n.get("label.recovery.code.shown.once")), new HBox(8, txt, btnCopy),
                new Label(I18n.get("label.recovery.code.warning")));
        box.setPrefWidth(480);
        dlg.getDialogPane().setContent(box);

        dlg.getDialogPane().getButtonTypes().setAll(new ButtonType(I18n.get("button.understood"), ButtonBar.ButtonData.OK_DONE));
        dlg.showAndWait();
    }

    private void showTursoDialog(MenuItem miTurso) {
        if (TursoConfig.isConfigured()) {
            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
            info.setTitle(I18n.get("dialog.turso.title"));
            info.setHeaderText(I18n.get("dialog.turso.connected.header"));
            info.setContentText(I18n.get("dialog.turso.connected.content", TursoConfig.getUrl()));
            Dialogs.decorate(info, root);
            info.showAndWait().ifPresent(res -> {
                if (res.getButtonData().isDefaultButton()) {
                    TursoConfig.clear();
                    DatabaseHelper.reconnect();
                    miTurso.setText(I18n.get("menu.turso.configure"));
                    refrescarTabla.run();
                    StatusBus.show(I18n.get("status.turso.disconnected"),
                        StatusBus.Type.INFO, Duration.seconds(3));
                }
            });
            return;
        }

        var dlg = new Alert(Alert.AlertType.NONE);
        dlg.setTitle(I18n.get("dialog.turso.configure.title"));

        var lblUrl = new Label(I18n.get("label.turso.url"));
        var txtUrl = new TextField();
        txtUrl.setPromptText(I18n.get("prompt.turso.url"));
        txtUrl.setPrefWidth(420);

        var lblToken = new Label(I18n.get("label.turso.token"));
        var txtToken = new TextField();
        txtToken.setPromptText(I18n.get("prompt.turso.token"));
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
                    StatusBus.show(I18n.get("validation.turso.required"),
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
                    StatusBus.show(I18n.get("status.turso.connection.error", ex.getMessage()),
                        StatusBus.Type.ERROR, Duration.seconds(5));
                    ex.printStackTrace();
                    return;
                }

                TursoConfig.save(url, token);
                DatabaseHelper.reconnect();
                miTurso.setText(I18n.get("menu.turso.connected"));
                refrescarTabla.run();
                StatusBus.show(I18n.get("status.turso.connected"),
                    StatusBus.Type.SUCCESS, Duration.seconds(3));
            }
        });
    }

    private void handleExportBackup() {
        var fc = new FileChooser();
        fc.setTitle(I18n.get("filechooser.export.backup"));
        fc.setInitialFileName("pildoras-backup.json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            io.github.guillermo_david.export.BackupService.exportToFile(file);
            StatusBus.show(I18n.get("status.backup.exported"), StatusBus.Type.SUCCESS, Duration.seconds(4));
        } catch (Exception ex) {
            ex.printStackTrace();
            StatusBus.show(I18n.get("status.backup.export.error", ex.getMessage()), StatusBus.Type.ERROR, Duration.seconds(5));
        }
    }

    private void handleImportBackup() {
        var confirm = new Alert(AlertType.WARNING);
        confirm.setTitle(I18n.get("filechooser.import.backup"));
        confirm.setHeaderText(I18n.get("confirm.import.backup.header"));
        confirm.setContentText(I18n.get("confirm.import.backup.content"));
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.decorate(confirm, root);
        var res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        var fc = new FileChooser();
        fc.setTitle(I18n.get("filechooser.import.backup"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = fc.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            int[] counts = io.github.guillermo_david.export.BackupService.importFromFile(file);
            refrescarTabla.run();
            StatusBus.show(I18n.get("status.backup.imported", counts[0], counts[1], counts[2]),
                    StatusBus.Type.SUCCESS, Duration.seconds(5));
        } catch (Exception ex) {
            ex.printStackTrace();
            StatusBus.show(I18n.get("status.backup.import.error", ex.getMessage()), StatusBus.Type.ERROR, Duration.seconds(5));
        }
    }

    private void handleImportMarkdown() {
        var scene = root.getScene();
        if (scene == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.get("filechooser.import.md"));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.get("filechooser.filter.markdown"), "*.md"),
            new FileChooser.ExtensionFilter(I18n.get("filechooser.filter.all"), "*.*")
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
                            ? I18n.get("status.import.success", okCount)
                            : I18n.get("status.import.partial", okCount, totalCount, failCount)),
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
            StatusBus.show(I18n.get("status.import.unexpected.error"), StatusBus.Type.ERROR, Duration.seconds(5));
        });

        new Thread(task, "ImportMarkdown").start();
    }

    private void handleMigrateToTurso() {
        if (!TursoConfig.isConfigured()) {
            var alert = new Alert(AlertType.WARNING);
            alert.setTitle(I18n.get("alert.turso.not.configured.title"));
            alert.setHeaderText(null);
            alert.setContentText(I18n.get("alert.turso.not.configured.content"));
            Dialogs.decorate(alert, root);
            alert.showAndWait();
            return;
        }

        var confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle(I18n.get("alert.turso.migrate.title"));
        confirm.setHeaderText(I18n.get("alert.turso.migrate.header"));
        confirm.setContentText(I18n.get("alert.turso.migrate.content"));
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
                I18n.get("status.turso.migration.completed", r.total()),
                StatusBus.Type.SUCCESS, Duration.seconds(8));
        });
        task.setOnFailed(e -> {
            root.setDisable(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String detail = I18n.get("status.error.unknown");
            if (ex != null) {
                detail = ex.getClass().getSimpleName();
                if (ex.getMessage() != null) detail += ": " + ex.getMessage();
                if (ex.getCause() != null) {
                    detail += " [" + I18n.get("status.error.cause") + ": " + ex.getCause().getClass().getSimpleName();
                    if (ex.getCause().getMessage() != null) detail += ": " + ex.getCause().getMessage();
                    detail += "]";
                }
            }
            StatusBus.show(I18n.get("status.turso.migration.error", detail),
                StatusBus.Type.ERROR, Duration.seconds(15));
        });

        new Thread(task, "MigrateToTurso").start();
    }

    private void showLanguageRestartAlert() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.get("language.restart.title"));
        alert.setHeaderText(I18n.get("language.restart.header"));
        alert.setContentText(I18n.get("language.restart.content"));
        Dialogs.decorate(alert, root);
        alert.showAndWait();
    }

    private MenuItem createOpenLogMenuItem() {
        var mi = new MenuItem(I18n.get("menu.debug.open.log"));
        mi.setOnAction(e -> {
            var logPath = io.github.guillermo_david.util.AppLogger.getLogPath();
            if (java.nio.file.Files.exists(logPath)) {
                try {
                    java.awt.Desktop.getDesktop().open(logPath.toFile());
                } catch (Exception ex) {
                    StatusBus.show(I18n.get("status.log.open.failed", logPath.toString()),
                        StatusBus.Type.ERROR, Duration.seconds(5));
                }
            } else {
                StatusBus.show(I18n.get("status.log.not.found", logPath.toString()),
                    StatusBus.Type.WARN, Duration.seconds(5));
            }
        });
        return mi;
    }
}
