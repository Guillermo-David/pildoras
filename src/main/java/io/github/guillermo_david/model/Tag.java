package io.github.guillermo_david.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Tag {
    private Integer id;
    private String nombre;

    public Tag(String nombre) {
        this(null, nombre);
    }
}
