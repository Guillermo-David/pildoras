package io.github.guillermo_david.javafx;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class ResizeHelper {

    private static final int BORDER = 6;

    public static void install(Stage stage) {
        Scene scene = stage.getScene();
        final double[] dx = {0}, dy = {0};
        final boolean[] dragging = {false};
        final Cursor[] edge = {Cursor.DEFAULT};

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            double x = e.getX(), y = e.getY();
            double w = scene.getWidth(), h = scene.getHeight();
            boolean left = x < BORDER, right = x > w - BORDER;
            boolean top = y < BORDER, bottom = y > h - BORDER;

            if (right && bottom)       edge[0] = Cursor.SE_RESIZE;
            else if (left && bottom)   edge[0] = Cursor.SW_RESIZE;
            else if (right && top)     edge[0] = Cursor.NE_RESIZE;
            else if (left && top)      edge[0] = Cursor.NW_RESIZE;
            else if (right)            edge[0] = Cursor.E_RESIZE;
            else if (left)             edge[0] = Cursor.W_RESIZE;
            else if (bottom)           edge[0] = Cursor.S_RESIZE;
            else if (top)              edge[0] = Cursor.N_RESIZE;
            else                       edge[0] = Cursor.DEFAULT;

            scene.setCursor(edge[0]);
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (edge[0] != Cursor.DEFAULT) {
                dragging[0] = true;
                dx[0] = e.getScreenX();
                dy[0] = e.getScreenY();
                e.consume();
            }
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging[0]) return;

            double ex = e.getScreenX(), ey = e.getScreenY();
            double ddx = ex - dx[0], ddy = ey - dy[0];
            dx[0] = ex;
            dy[0] = ey;

            double minW = stage.getMinWidth(), minH = stage.getMinHeight();
            Cursor c = edge[0];

            if (c == Cursor.E_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.NE_RESIZE) {
                stage.setWidth(Math.max(minW, stage.getWidth() + ddx));
            }
            if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE) {
                stage.setHeight(Math.max(minH, stage.getHeight() + ddy));
            }
            if (c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) {
                double nw = Math.max(minW, stage.getWidth() - ddx);
                if (nw > minW) {
                    stage.setX(stage.getX() + ddx);
                    stage.setWidth(nw);
                }
            }
            if (c == Cursor.N_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.NE_RESIZE) {
                double nh = Math.max(minH, stage.getHeight() - ddy);
                if (nh > minH) {
                    stage.setY(stage.getY() + ddy);
                    stage.setHeight(nh);
                }
            }

            e.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (dragging[0]) {
                dragging[0] = false;
            }
        });
    }
}
