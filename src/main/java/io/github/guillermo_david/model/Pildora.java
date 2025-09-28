package io.github.guillermo_david.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Pildora {
    private Integer id;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private boolean favorita;
    private boolean pinned;


    public Pildora(String titulo, String descripcion) {
        this(null, titulo, descripcion, null, null, false, false);
    }

    @Override
    public String toString() {
        return "Pildora{" +
                "id=" + id +
                ", titulo='" + titulo + '\'' +
                ", descripcion='" + descripcion + '\'' +
                ", fechaCreacion=" + fechaCreacion +
                ", fechaActualizacion=" + fechaActualizacion +
                ", favorita=" + (favorita ? "Sí" : "No") +
                ", pinned=" + (pinned? "Sí" : "No") +
                '}';
    }
}

