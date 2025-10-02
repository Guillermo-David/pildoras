package io.github.guillermo_david.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Pildora {
    private Integer id;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private boolean favorita;
    private boolean pinned;
    private boolean protegida;
    private byte[] descripcionCipher;
    private byte[] descripcionIv;


    public Pildora(
    		String titulo, 
    		String descripcion, 
    		boolean protegida, 
    		byte[] descripcionCipher, 
    		byte[] descripcionIv) {
        this.id = null;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaCreacion = null;
        this.fechaActualizacion = null;
        this.favorita = false;
        this.pinned = false;
        this.protegida = protegida;
        this.descripcionCipher = descripcionCipher;
        this.descripcionIv = descripcionIv;
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

