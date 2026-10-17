package io.github.guillermo_david.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.guillermo_david.model.Pildora;

class PildoraDaoFilterTest {

    static final PildoraDao dao = new PildoraDao();
    static final TagDao tags = new TagDao();
    static int a, b, c;

    static int create(String titulo, boolean oculta, String... tagNames) throws Exception {
        Pildora p = new Pildora();
        p.setTitulo(titulo);
        p.setDescripcion("desc " + titulo);
        p.setOculta(oculta);
        dao.insertar(p);
        for (String t : tagNames) {
            var tag = tags.findOrCreate(t);
            try (var ps = io.github.guillermo_david.db.DatabaseHelper.getInstance().getConnection()
                    .prepareStatement("INSERT INTO pildora_tag(pildora_id, tag_id) VALUES(?, ?)")) {
                ps.setInt(1, p.getId());
                ps.setInt(2, tag.getId());
                ps.execute();
            }
        }
        return p.getId();
    }

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("pildoras-test").toString());
        a = create("alfa", false, "x", "y");
        b = create("beta", false, "y");
        c = create("gamma", true, "x");
    }

    private List<Integer> ids(String text, String tags, boolean hidden) {
        return dao.listarFiltradas(text, tags, false, 1, 50, "titulo", "ASC", hidden).stream()
                .map(Pildora::getId).toList();
    }

    @Test
    void andFilter() {
        assertEquals(List.of(a), ids(null, "x,y", true));
        assertEquals(1, dao.contarFiltradas(null, "x,y", false, true));
    }

    @Test
    void hiddenAndTextFilters() {
        assertEquals(List.of(a, b, c), ids(null, null, true));
        assertEquals(List.of(b), ids("bet", null, true));
        assertEquals(3, dao.contarFiltradas(null, null, false, true));
        assertEquals(2, dao.contarFiltradas(null, null, false, false));
    }

    @Test
    void tagCountCacheSeparatesHidden() {
        var all = tags.listAllWithCount(true);
        var visible = tags.listAllWithCount(false);
        assertEquals("2", all.stream().filter(r -> r[0].equals("x")).findFirst().get()[1]);
        assertEquals("1", visible.stream().filter(r -> r[0].equals("x")).findFirst().get()[1]);
    }
}
