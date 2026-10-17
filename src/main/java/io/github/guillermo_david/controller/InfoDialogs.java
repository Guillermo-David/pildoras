package io.github.guillermo_david.controller;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.util.I18n;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

final class InfoDialogs {

    private InfoDialogs() {}

    static void showShortcutsDialog(Parent root) {
        var sb = new StringBuilder();

        sb.append(I18n.get("shortcuts.section.list.title")).append("\n")
          .append("───────\n")
          .append(I18n.get("shortcuts.section.list.content")).append("\n\n")

          .append(I18n.get("shortcuts.section.detail.title")).append("\n")
          .append("───────\n")
          .append(I18n.get("shortcuts.section.detail.content")).append("\n\n")

          .append(I18n.get("shortcuts.section.editor.title")).append("\n")
          .append("──────\n")
          .append(I18n.get("shortcuts.section.editor.content")).append("\n\n")

          .append(I18n.get("shortcuts.section.drafts.title")).append("\n")
          .append("──────────\n")
          .append(I18n.get("shortcuts.section.drafts.content")).append("\n");

        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.get("shortcuts.dialog.title"));
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

    static void showTursoGuideDialog(Parent root) {
        Alert guide = new Alert(AlertType.INFORMATION);
        guide.setTitle(I18n.get("guide.turso.title"));
        guide.setHeaderText(null);

        var content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        var title = new Label(I18n.get("guide.turso.headline"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var intro = new Label(I18n.get("guide.turso.intro"));
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 13px;");

        var stepsTitle = new Label(I18n.get("guide.turso.steps.title"));
        stepsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8 0 0 0;");

        var steps = new Label(I18n.get("guide.turso.steps"));
        steps.setWrapText(true);
        steps.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");

        var linkBox = new javafx.scene.layout.HBox(6);
        linkBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        linkBox.setPadding(new javafx.geometry.Insets(6, 0, 0, 0));
        var linkLabel = new Label(I18n.get("guide.turso.more.info"));
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

    static void showAboutDialog(Parent root) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle(I18n.get("about.title"));
        dlg.setHeaderText(null);

        var logoUrl = InfoDialogs.class.getResource("/icons/gdg_B.png");
        javafx.scene.image.ImageView logo = null;
        if (logoUrl != null) {
            logo = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(logoUrl.toExternalForm(), 0, 64, true, true));
            logo.setFitHeight(64);
            logo.setPreserveRatio(true);
        }

        Label lblTitle = new Label("Pildoras");
        lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label lblVersion = new Label(I18n.get("about.version",
                io.github.guillermo_david.util.AppVersion.get()));
        lblVersion.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");

        Label lblDesc = new Label(I18n.get("about.description"));
        lblDesc.setWrapText(true);
        lblDesc.setMaxWidth(400);

        Label lblAuthor = new Label(I18n.get("about.author"));
        lblAuthor.setStyle("-fx-font-size: 12px; -fx-opacity: 0.6;");
        Label lblAuthorName = new Label(I18n.get("about.author.name"));
        lblAuthorName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Hyperlink ghLink = new Hyperlink("github.com/guillermo-david/pildoras");
        ghLink.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/guillermo-david/pildoras")); }
            catch (Exception ignored) {}
        });

        Label lblTech = new Label(I18n.get("about.tech"));
        lblTech.setStyle("-fx-font-size: 11px; -fx-opacity: 0.5;");
        Label lblLicense = new Label(I18n.get("about.license"));
        lblLicense.setStyle("-fx-font-size: 11px; -fx-opacity: 0.5;");

        VBox content = new VBox(10);
        if (logo != null) content.getChildren().add(logo);
        content.getChildren().addAll(lblTitle, lblVersion,
                new javafx.scene.control.Separator(),
                lblDesc, lblAuthor, lblAuthorName, ghLink,
                new javafx.scene.control.Separator(),
                lblTech, lblLicense);
        content.setPadding(new Insets(10));
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPrefWidth(420);

        dlg.getDialogPane().setContent(content);
        Dialogs.decorate(dlg, root);
        dlg.showAndWait();
    }

    static void showHelpDialog(Parent root) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle(I18n.get("help.title"));
        dlg.setHeaderText(null);

        StringBuilder sb = new StringBuilder();
        sb.append("━━ ").append(I18n.get("help.section.what")).append(" ━━\n");
        sb.append(I18n.get("help.section.what.content")).append("\n\n");
        sb.append("━━ ").append(I18n.get("help.section.features")).append(" ━━\n");
        sb.append(I18n.get("help.section.features.content")).append("\n\n");
        sb.append("━━ ").append(I18n.get("help.section.export")).append(" ━━\n");
        sb.append(I18n.get("help.section.export.content")).append("\n\n");
        sb.append("━━ ").append(I18n.get("help.section.security")).append(" ━━\n");
        sb.append(I18n.get("help.section.security.content")).append("\n\n");
        sb.append("━━ ").append(I18n.get("help.section.turso")).append(" ━━\n");
        sb.append(I18n.get("help.section.turso.content")).append("\n\n");
        sb.append("━━ ").append(I18n.get("help.section.shortcuts")).append(" ━━\n");
        sb.append(I18n.get("help.section.shortcuts.hint"));

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(20);
        ta.setPrefColumnCount(50);
        ta.setStyle("-fx-font-family: 'System'; -fx-font-size: 13px;");

        VBox content = new VBox(ta);
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);
        Dialogs.decorate(dlg, root);
        dlg.showAndWait();
    }

    static void showStatisticsDialog(Parent root) {
        var pDao = new PildoraDao();
        var tDao = new io.github.guillermo_david.dao.TagDao();

        int total = pDao.contarTotal();
        int prot = pDao.contarProtegidas();
        int favs = pDao.contarFavoritas();
        int hidden = pDao.contarOcultas();
        int totalTags = tDao.countAll();

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int last7 = pDao.contarCreadasDesde(now.minusDays(7));
        int last30 = pDao.contarCreadasDesde(now.minusDays(30));
        int lastYear = pDao.contarCreadasDesde(now.minusYears(1));

        var topTags = tDao.topTagsByUsage(10);

        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle(I18n.get("stats.title"));
        dlg.setHeaderText(null);

        var gridStyle = "-fx-font-size: 13px;";

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));

        int row = 0;
        grid.add(boldLabel(I18n.get("stats.total.pills")), 0, row);
        grid.add(new Label(String.valueOf(total)), 1, row++);
        grid.add(boldLabel(I18n.get("stats.total.protected")), 0, row);
        grid.add(new Label(String.valueOf(prot)), 1, row++);
        grid.add(boldLabel(I18n.get("stats.total.favorites")), 0, row);
        grid.add(new Label(String.valueOf(favs)), 1, row++);
        grid.add(boldLabel(I18n.get("stats.total.hidden")), 0, row);
        grid.add(new Label(String.valueOf(hidden)), 1, row++);
        grid.add(boldLabel(I18n.get("stats.total.tags")), 0, row);
        grid.add(new Label(String.valueOf(totalTags)), 1, row++);

        grid.add(new javafx.scene.control.Separator(), 0, row, 2, 1); row++;
        Label freqTitle = boldLabel(I18n.get("stats.creation.frequency"));
        grid.add(freqTitle, 0, row, 2, 1); row++;
        grid.add(new Label("  " + I18n.get("stats.pills.last.7days")), 0, row);
        grid.add(new Label(String.valueOf(last7)), 1, row++);
        grid.add(new Label("  " + I18n.get("stats.pills.last.30days")), 0, row);
        grid.add(new Label(String.valueOf(last30)), 1, row++);
        grid.add(new Label("  " + I18n.get("stats.pills.last.year")), 0, row);
        grid.add(new Label(String.valueOf(lastYear)), 1, row++);

        if (!topTags.isEmpty()) {
            grid.add(new javafx.scene.control.Separator(), 0, row, 2, 1); row++;
            grid.add(boldLabel(I18n.get("stats.most.used.tags")), 0, row, 2, 1); row++;
            for (var t : topTags) {
                grid.add(new Label("  " + I18n.get("stats.tag.count", t[0], t[1])), 0, row, 2, 1);
                row++;
            }
        }

        grid.setStyle(gridStyle);
        VBox content = new VBox(grid);
        content.setPrefWidth(380);
        dlg.getDialogPane().setContent(content);
        Dialogs.decorate(dlg, root);
        dlg.showAndWait();
    }

    private static Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }
}
