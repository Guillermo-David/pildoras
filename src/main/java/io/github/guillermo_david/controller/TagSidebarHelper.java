package io.github.guillermo_david.controller;

import java.util.function.BooleanSupplier;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.TagDao;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

class TagSidebarHelper {

    private final VBox tagSidebar;
    private final VBox tagListBox;
    private final Button btnToggleSidebar;
    private final Runnable resetPageAndRefresh;
    private final BooleanSupplier includeHidden;

    private Button allBtn;
    private boolean sidebarCollapsed = false;
    private String selectedSidebarTag = null;

    TagSidebarHelper(VBox tagSidebar, VBox tagListBox, Button btnToggleSidebar,
                     Runnable resetPageAndRefresh, BooleanSupplier includeHidden) {
        this.tagSidebar = tagSidebar;
        this.tagListBox = tagListBox;
        this.btnToggleSidebar = btnToggleSidebar;
        this.resetPageAndRefresh = resetPageAndRefresh;
        this.includeHidden = includeHidden;
    }

    String getSelectedTag() {
        return selectedSidebarTag;
    }

    void selectTag(String tagName) {
        selectedSidebarTag = tagName;
        refresh();
    }

    void init() {
        btnToggleSidebar.setOnAction(e -> toggleSidebar());

        allBtn = new Button("Todas");
        allBtn.getStyleClass().add("tag-sidebar-item");
        allBtn.setMaxWidth(Double.MAX_VALUE);
        allBtn.setOnAction(e -> {
            selectedSidebarTag = null;
            resetAndRefresh();
            refresh();
        });

        // Insert "Todas" between header and ScrollPane (index 1)
        tagSidebar.getChildren().add(1, allBtn);

        refresh();
    }

    void refresh() {
        tagListBox.getChildren().clear();

        // Update "Todas" styling
        if (allBtn != null) {
            allBtn.getStyleClass().remove("tag-sidebar-selected");
            if (selectedSidebarTag == null) allBtn.getStyleClass().add("tag-sidebar-selected");
        }

        var tagsWithCount = new TagDao().listAllWithCount(includeHidden.getAsBoolean());
        for (String[] tc : tagsWithCount) {
            String nombre = tc[0];
            String count = tc[1];
            var btn = new Button(nombre + "  (" + count + ")");
            btn.getStyleClass().add("tag-sidebar-item");
            btn.setMaxWidth(Double.MAX_VALUE);
            if (nombre.equals(selectedSidebarTag)) btn.getStyleClass().add("tag-sidebar-selected");
            btn.setOnAction(e -> {
                selectedSidebarTag = nombre;
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
        Node header = tagSidebar.getChildren().get(0);
        if (sidebarCollapsed) {
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
            var expandBtn = new Button();
            expandBtn.setId("sidebarExpandBtn");
            expandBtn.setGraphic(new FontIcon(FontAwesomeSolid.CHEVRON_LEFT));
            expandBtn.getStyleClass().add("sidebar-expand-btn");
            expandBtn.setMaxWidth(Double.MAX_VALUE);
            expandBtn.setOnAction(ev -> toggleSidebar());
            tagSidebar.getChildren().add(0, expandBtn);
        } else {
            tagSidebar.getChildren().removeIf(n -> "sidebarExpandBtn".equals(n.getId()));
            header = tagSidebar.getChildren().get(0);
            header.setVisible(true);
            header.setManaged(true);
            if (allBtn != null) { allBtn.setVisible(true); allBtn.setManaged(true); }
            for (Node n : tagSidebar.getChildren()) {
                if (n instanceof ScrollPane) { n.setVisible(true); n.setManaged(true); }
            }
            tagListBox.setVisible(true);
            tagListBox.setManaged(true);
            tagSidebar.setPrefWidth(200);
            tagSidebar.setMinWidth(0);
            tagSidebar.setMaxWidth(200);
            btnToggleSidebar.setText("▶");
        }
    }
}
