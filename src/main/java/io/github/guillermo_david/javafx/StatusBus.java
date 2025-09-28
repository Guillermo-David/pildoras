package io.github.guillermo_david.javafx;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;

public final class StatusBus {
    private StatusBus() {}
    
    public enum Type { INFO, SUCCESS, WARN, ERROR }
    
    public static final class Message {
        public final String text; public final Type type; public final Duration ttl;
        public Message(String t, Type ty, Duration ttl) { this.text=t; this.type=ty; this.ttl=ttl; }
    }
    
    private static final ObjectProperty<Message> message = new SimpleObjectProperty<>();
    
    public static ObjectProperty<Message> messageProperty() { return message; }
    
    public static void show(String text, Type type, Duration ttl) {
        Platform.runLater(() -> message.set(new Message(text, type, ttl)));
    }
}
