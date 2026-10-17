package io.github.guillermo_david.controller;

import java.util.Arrays;

import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.security.SecurityService;
import io.github.guillermo_david.util.I18n;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.util.Duration;

public final class SecurityUiHelper {

    private SecurityUiHelper() {}

    public static boolean unlockWithFeedback(Parent owner) {
        var security = SecurityService.getInstance();
        if (security.isUnlocked())
            return true;

        long lockMs = security.lockoutRemainingMillis();
        if (lockMs > 0) {
            StatusBus.show(I18n.get("status.lockout.retry", humanize(lockMs)),
                    StatusBus.Type.ERROR, Duration.seconds(4));
            return false;
        }
        char[] pin = PinDialogs.promptPin6(owner, true, java.time.Duration.ofMinutes(10));
        if (pin == null)
            return false;
        try {
            if (security.verifyPin(pin)) {
                StatusBus.show(I18n.get("status.unlocked"), StatusBus.Type.INFO, Duration.seconds(2));
                return true;
            }
            if (security.isLockedOut()) {
                StatusBus.show(I18n.get("status.lockout.retry", humanize(security.lockoutRemainingMillis())),
                        StatusBus.Type.ERROR, Duration.seconds(4));
            } else {
                StatusBus.show(I18n.get("alert.pin.incorrect.remaining", security.attemptsLeft()),
                        StatusBus.Type.ERROR, Duration.seconds(3));
            }
            return false;
        } finally {
            Arrays.fill(pin, '\0');
        }
    }


	public static boolean ensureUnlockedWithRetries(Parent root) {
		var security = SecurityService.getInstance();

		// Ya desbloqueado o recordado -> OK
		if (security.isUnlocked() || security.isRemembered())
			return true;

		// Bloqueado ahora mismo -> avisa temado y corta
		if (security.isLockedOut()) {
			showLockoutAlert(root);
			return false;
		}

		final int MAX = 3; // debe coincidir con MAX_FAILED del servicio
		int attempts = 0;

		while (attempts < MAX) {
			// PIN temado + foco + "recordar 10 min"
			char[] pin = PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10));
			if (pin == null)
				return false; // cancelado

			try {
				if (security.verifyPin(pin)) {
					StatusBus.show(I18n.get("status.unlocked.10min"), StatusBus.Type.INFO, Duration.seconds(3));
					return true;
				} else {
					attempts++;
					// ¿acaba de bloquearse?
					if (security.isLockedOut()) {
						showLockoutAlert(root);
						return false;
					}
					int left = MAX - attempts;
					Alert warn = new Alert(Alert.AlertType.WARNING);
					warn.setTitle(I18n.get("alert.pin.incorrect.title"));
					warn.setHeaderText(null);
					warn.setContentText(
							left > 0 ? I18n.get("alert.pin.incorrect.remaining", left)
									: I18n.get("alert.pin.exhausted"));
					Dialogs.decorate(warn, root); // 👈 temado igual que el resto
					warn.showAndWait();
				}
			} finally {
				Arrays.fill(pin, '\0');
			}
		}

		// Si salimos por agotar intentos, muestra lockout si aplica
		if (security.isLockedOut())
			showLockoutAlert(root);
		return false;
	}

	private static void showLockoutAlert(Parent root) {
		var security = SecurityService.getInstance();
		long secs = (security.lockoutRemainingMillis() + 999) / 1000;
		Alert lock = new Alert(Alert.AlertType.ERROR);
		lock.setTitle(I18n.get("alert.lockout.title"));
		lock.setHeaderText(I18n.get("alert.lockout.header"));
		lock.setContentText(I18n.get("alert.lockout.content", secs));
		Dialogs.decorate(lock, root); // 👈 temado
		lock.showAndWait();
	}

    public static String humanize(long ms) {
        long s = (ms + 999) / 1000;
        if (s < 60)
            return s + " s";
        long m = s / 60, rs = s % 60;
        return rs == 0 ? (m + " min") : (m + " min " + rs + " s");
    }
}
