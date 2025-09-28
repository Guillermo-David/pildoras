package io.github.guillermo_david.test;

import java.util.List;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;

public class TestTags {
    public static void main(String[] args) {
        PildoraDao pildoraDao = new PildoraDao();
        TagDao tagDao = new TagDao();
        PildoraTagDao pildoraTagDao = new PildoraTagDao();

        // 1. Crear una píldora
        Pildora pildora = new Pildora("Píldora con tags", "Esta es una píldora que tendrá varios tags");
        pildoraDao.insertar(pildora);
        System.out.println("Insertada: " + pildora);

        // 2. Crear tags y asociarlos
        String[] tags = {"Java", "SQLite", "Persistencia"};
        for (String nombre : tags) {
            Tag tag = tagDao.findOrCreate(nombre);
            pildoraTagDao.addTagToPildora(pildora.getId(), tag.getId());
            System.out.println("Asociado tag '" + nombre + "' a la píldora " + pildora.getId());
        }

        // 3. Listar todos los tags de la píldora
        List<Tag> tagsDePildora = tagDao.findByPildoraId(pildora.getId());
        System.out.println("Tags de la píldora " + pildora.getId() + ": " + tagsDePildora);

        // 4. Listar todos los tags existentes en la base
        List<Tag> todosLosTags = tagDao.listarTodos();
        System.out.println("Tags en la base: " + todosLosTags);
    }
}
