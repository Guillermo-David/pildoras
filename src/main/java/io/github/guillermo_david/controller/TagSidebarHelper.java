package io.github.guillermo_david.controller;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.util.I18n;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

class TagSidebarHelper {
    private final TagDao tagDao = new TagDao();

    private static final String PREF_SIDEBAR_COLLAPSED = "sidebarCollapsed";

    private final VBox tagSidebar;
    private final VBox tagListBox;
    private final Button btnToggleSidebar;
    private final Runnable resetPageAndRefresh;
    private final BooleanSupplier includeHidden;
    private final Preferences prefs;

    private Button allBtn;
    private boolean sidebarCollapsed = false;
    private final Set<String> selectedTags = new LinkedHashSet<>();

    TagSidebarHelper(VBox tagSidebar, VBox tagListBox, Button btnToggleSidebar,
                     Runnable resetPageAndRefresh, BooleanSupplier includeHidden,
                     Preferences prefs) {
        this.tagSidebar = tagSidebar;
        this.tagListBox = tagListBox;
        this.btnToggleSidebar = btnToggleSidebar;
        this.resetPageAndRefresh = resetPageAndRefresh;
        this.includeHidden = includeHidden;
        this.prefs = prefs;
    }

    String getSelectedTags() {
        if (selectedTags.isEmpty()) return null;
        return selectedTags.stream().collect(Collectors.joining(","));
    }

    void selectTag(String tagName) {
        selectedTags.clear();
        if (tagName != null) selectedTags.add(tagName);
        refresh();
    }

    void clearSelection() {
        selectedTags.clear();
        refresh();
    }

    void init() {
        btnToggleSidebar.setText("");
        btnToggleSidebar.setGraphic(new FontIcon(FontAwesomeSolid.CHEVRON_RIGHT));
        btnToggleSidebar.setOnAction(e -> toggleSidebar());

        allBtn = new Button(I18n.get("sidebar.all.tags"));
        allBtn.getStyleClass().add("tag-sidebar-item");
        allBtn.setMaxWidth(Double.MAX_VALUE);
        allBtn.setOnAction(e -> {
            selectedTags.clear();
            resetAndRefresh();
            refresh();
        });

        tagSidebar.getChildren().add(1, allBtn);

        refresh();

        if (prefs.getBoolean(PREF_SIDEBAR_COLLAPSED, false)) {
            toggleSidebar();
        }
    }

    void refresh() {
        tagListBox.getChildren().clear();

        if (allBtn != null) {
            allBtn.getStyleClass().remove("tag-sidebar-selected");
            if (selectedTags.isEmpty()) allBtn.getStyleClass().add("tag-sidebar-selected");
        }

        var tagsWithCount = tagDao.listAllWithCount(includeHidden.getAsBoolean());
        for (String[] tc : tagsWithCount) {
            String nombre = tc[0];
            String count = tc[1];
            var btn = new Button(nombre + "  (" + count + ")");
            btn.getStyleClass().add("tag-sidebar-item");
            btn.setMaxWidth(Double.MAX_VALUE);
            if (selectedTags.contains(nombre)) btn.getStyleClass().add("tag-sidebar-selected");
            btn.setOnAction(e -> {
                if (selectedTags.contains(nombre)) {
                    selectedTags.remove(nombre);
                } else {
                    selectedTags.add(nombre);
                }
                resetAndRefresh();
                refresh();
            });
            tagListBox.getChildren().add(btn);
        }
    }

    private void resetAndRefresh() {
        resetPageAndRefresh.run();
    }

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        prefs.putBoolean(PREF_SIDEBAR_COLLAPSED, sidebarCollapsed);
        Node header = tagSidebar.getChildren().get(0);
        if (sidebarCollapsed) {
            javafx.scene.layout.HBox headerBox = (javafx.scene.layout.HBox) header;
            headerBox.getChildren().remove(btnToggleSidebar);
            header.setVisible(false);
            header.setManaged(false);
            if (allBtn != null) { allBtn.setVisible(false); allBtn.setManaged(false); }
            tagListBox.setVisible(false);
            tagListBox.setManaged(false);
            for (Node n : tagSidebar.getChildren()) {
                if (n instanceof ScrollPane) { n.setVisible(false); n.setManaged(false); }
            }
            tagSidebar.setPrefWidth(28);
            tagSidebar.setMinWidth(28);
            tagSidebar.setMaxWidth(28);
            btnToggleSidebar.setGraphic(new FontIcon(FontAwesomeSolid.CHEVRON_LEFT));
            tagSidebar.getChildren().add(0, btnToggleSidebar);
        } else {
            tagSidebar.getChildren().remove(btnToggleSidebar);
            header = tagSidebar.getChildren().get(0);
            header.setVisible(true);
            header.setManaged(true);
            javafx.scene.layout.HBox headerBox = (javafx.scene.layout.HBox) header;
            headerBox.getChildren().add(btnToggleSidebar);
            if (allBtn != null) { allBtn.setVisible(true); allBtn.setManaged(true); }
            for (Node n : tagSidebar.getChildren()) {
                if (n instanceof ScrollPane) { n.setVisible(true); n.setManaged(true); }
            }
            tagListBox.setVisible(true);
            tagListBox.setManaged(true);
            tagSidebar.setPrefWidth(200);
            tagSidebar.setMinWidth(0);
            tagSidebar.setMaxWidth(200);
            btnToggleSidebar.setGraphic(new FontIcon(FontAwesomeSolid.CHEVRON_RIGHT));
        }
    }
}
