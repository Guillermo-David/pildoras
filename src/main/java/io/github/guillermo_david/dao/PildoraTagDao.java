package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.guillermo_david.db.DatabaseHelper;

public class PildoraTagDao {

    public void addTagToPildora(int pildoraId, int tagId) {
        String sql = "INSERT OR IGNORE INTO pildora_tag (pildora_id, tag_id) VALUES (?, ?)";

        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, pildoraId);
            pstmt.setInt(2, tagId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeTagFromPildora(int pildoraId, int tagId) {
        String sql = "DELETE FROM pildora_tag WHERE pildora_id = ? AND tag_id = ?";

        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, pildoraId);
            pstmt.setInt(2, tagId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void removeAllTagsFromPildora(int pildoraId) {
        String sql = "DELETE FROM pildora_tag WHERE pildora_id = ?";
        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, pildoraId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
