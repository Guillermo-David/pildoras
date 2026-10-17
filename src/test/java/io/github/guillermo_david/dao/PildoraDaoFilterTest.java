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

    @Test
    void trashHidesRestoresAndPurges() throws Exception {
        int d = create("delta", false, "z");
        dao.eliminar(d);
        assertFalse(ids(null, null, true).contains(d));
        assertTrue(tags.listAllWithCount(true).stream().noneMatch(r -> r[0].equals("z")));
        assertEquals(1, dao.contarPapelera());
        dao.restaurar(d);
        assertTrue(ids(null, null, true).contains(d));
        dao.eliminar(d);
        dao.vaciarPapelera();
        assertEquals(0, dao.contarPapelera());
        assertNull(dao.buscarPorId(d));
    }

    @Test
    void duplicateCopiesTagsAndTitle() throws Exception {
        var copy = dao.duplicar(a, " (copia)");
        try {
            assertEquals("alfa (copia)", dao.buscarPorId(copy.getId()).getTitulo());
            assertEquals(2, tags.findByPildoraId(copy.getId()).size());
            assertFalse(dao.buscarPorId(copy.getId()).isFavorita());
        } finally {
            dao.eliminarDefinitivo(copy.getId());
        }
    }

    @Test
    void fullTextSearchIsPrefixCaseAndAccentInsensitive() throws Exception {
        int id = create("Canción de cuna", false);
        try {
            assertTrue(ids("cancion", null, false).contains(id));
            assertTrue(ids("CANC cuna", null, false).contains(id));
            assertFalse(ids("cancion zzz", null, false).contains(id));
            assertEquals(1, dao.contarFiltradas("cancion", null, false, false));
            assertEquals("\"a\"* \"b\"*", PildoraDao.ftsQuery("a \"b\" ;;"));
            assertNull(PildoraDao.ftsQuery(" ;; "));
        } finally {
            dao.eliminarDefinitivo(id);
        }
    }

    @Test
    void ftsIndexFollowsUpdatesAndDeletes() throws Exception {
        int id = create("temporal", false);
        var p = dao.buscarPorId(id);
        p.setTitulo("renombrada");
        p.setDescripcion("otra cosa");
        dao.actualizar(p);
        assertTrue(ids("renombr", null, false).contains(id));
        assertFalse(ids("tempor", null, false).contains(id));
        dao.eliminarDefinitivo(id);
        assertFalse(ids("renombr", null, false).contains(id));
    }
}
