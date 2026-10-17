package io.github.guillermo_david.controller;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.util.I18n;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.util.Duration;

class PildoraContextMenu {

    record Actions(Consumer<Pildora> open, Consumer<Pildora> edit, Consumer<Pildora> delete,
                   Runnable refreshAll, Runnable refreshCells, BooleanSupplier onlyFavorites) {}

    private final Actions a;
    private final PildoraDao dao = new PildoraDao();

    PildoraContextMenu(Actions a) {
        this.a = a;
    }

    ContextMenu build(Supplier<Pildora> rowItem) {
        ContextMenu ctx = new ContextMenu();
        ctx.getItems().add(new MenuItem("..."));
        ctx.setOnShowing(e -> {
            ctx.getItems().clear();
            Pildora p = rowItem.get();
            if (p == null) return;

            var miAbrir = item("menu.open", new FontIcon(FontAwesomeSolid.EYE), () -> a.open().accept(p));
            var miEditar = item("menu.edit", new FontIcon(FontAwesomeRegular.EDIT), () -> a.edit().accept(p));

            var miFav = new MenuItem(I18n.get(p.isFavorita() ? "menu.favorites.remove" : "menu.favorites.add"));
            miFav.setGraphic(new FontIcon(p.isFavorita() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR));
            miFav.setOnAction(ev -> {
                boolean nueva = !p.isFavorita();
                dao.marcarFavorita(p.getId(), nueva);
                p.setFavorita(nueva);
                toast(nueva ? "status.favorites.added" : "status.favorites.removed");
                a.refreshCells().run();
                if (a.onlyFavorites().getAsBoolean()) a.refreshAll().run();
            });

            var miPin = new MenuItem(I18n.get(p.isPinned() ? "menu.unpin" : "menu.pin"));
            miPin.setGraphic(new FontIcon(FontAwesomeSolid.THUMBTACK));
            miPin.setOnAction(ev -> {
                boolean nuevo = !p.isPinned();
                dao.marcarPinned(p.getId(), nuevo);
                p.setPinned(nuevo);
                toast(nuevo ? "status.pildora.pinned" : "status.pildora.unpinned");
                a.refreshAll().run();
            });

            var miOcultar = new MenuItem(I18n.get(p.isOculta() ? "menu.show" : "menu.hide"));
            miOcultar.setGraphic(new FontIcon(p.isOculta() ? FontAwesomeSolid.EYE : FontAwesomeSolid.EYE_SLASH));
            miOcultar.setOnAction(ev -> {
                boolean nuevo = !p.isOculta();
                dao.marcarOculta(p.getId(), nuevo);
                p.setOculta(nuevo);
                toast(nuevo ? "status.pildora.hidden" : "status.pildora.visible");
                a.refreshAll().run();
            });

            var miEliminar = item("menu.delete", new FontIcon(FontAwesomeSolid.TRASH), () -> a.delete().accept(p));
            miEliminar.getStyleClass().add("danger");

            ctx.getItems().addAll(miAbrir, miEditar, new SeparatorMenuItem(), miFav, miPin,
                    new SeparatorMenuItem(), miOcultar, new SeparatorMenuItem(), miEliminar);
        });
        return ctx;
    }

    private static MenuItem item(String key, FontIcon icon, Runnable action) {
        var mi = new MenuItem(I18n.get(key));
        mi.setGraphic(icon);
        mi.setOnAction(ev -> action.run());
        return mi;
    }

    private static void toast(String key) {
        StatusBus.show(I18n.get(key), StatusBus.Type.INFO, Duration.seconds(2));
    }
}
