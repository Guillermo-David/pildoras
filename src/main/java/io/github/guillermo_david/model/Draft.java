package io.github.guillermo_david.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor
public class Draft {
    private Integer id;
    private String  titulo;
    private String  contenido;
    private byte[]  contenidoCipher;
    private byte[]  contenidoIv;
    private boolean protegida;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public static Draft of(
    		String titulo, 
    		String contenido, 
    		boolean protegida,
    		byte[] cipher, 
    		byte[] iv
    		) {
        return new Draft(null, titulo, contenido, cipher, iv, protegida, null, null);
    }
}
