package io.github.guillermo_david.controller;

import java.util.Arrays;

import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.security.SecurityService;
import io.github.guillermo_david.util.I18n;
import javafx.scene.Parent;
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

    public static String humanize(long ms) {
        long s = (ms + 999) / 1000;
        if (s < 60)
            return s + " s";
        long m = s / 60, rs = s % 60;
        return rs == 0 ? (m + " min") : (m + " min " + rs + " s");
    }
}
