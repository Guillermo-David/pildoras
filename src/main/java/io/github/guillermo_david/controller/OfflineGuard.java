package io.github.guillermo_david.controller;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.util.I18n;
import javafx.util.Duration;

final class OfflineGuard {

    private OfflineGuard() {}

    /** True (y avisa al usuario) si la app está en modo sin conexión de solo lectura. */
    static boolean blocked() {
        if (!DatabaseHelper.getInstance().isOffline())
            return false;
        StatusBus.show(I18n.get("status.offline.readonly"), StatusBus.Type.WARN, Duration.seconds(3));
        return true;
    }
}
