package br.com.fatec.futefatec.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import br.com.fatec.futefatec.model.Partida;

/**
 * ============================================================================
 * PADRÃO DAO: PartidaDAO
 * ============================================================================
 * Gerencia a persistência de confrontos do torneio FuteFatec via JDBC.
 * ============================================================================
 */
public class PartidaDAO {

    private final TimeDAO timeDAO = new TimeDAO();

    /**
     * Salva uma lista completa de partidas geradas em uma única transação.
     */
    public void salvarLote(List<Partida> partidas) throws SQLException {
        String sql = """
            INSERT INTO partidas (chave, fase, numero_partida, time_a_id, time_b_id, gols_a, gols_b,
                                  vencedor_id, perdedor_id, prox_vencedor_id, prox_perdedor_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = ConnectionFactory.getConnection();
            conn.setAutoCommit(false);

            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            for (Partida p : partidas) {
                stmt.setString(1, p.getChave());
                stmt.setString(2, p.getFase());
                stmt.setInt(3, p.getNumeroPartida());

                if (p.getTimeAId() != null) stmt.setLong(4, p.getTimeAId()); else stmt.setNull(4, Types.BIGINT);
                if (p.getTimeBId() != null) stmt.setLong(5, p.getTimeBId()); else stmt.setNull(5, Types.BIGINT);
                if (p.getGolsA() != null) stmt.setInt(6, p.getGolsA()); else stmt.setNull(6, Types.INTEGER);
                if (p.getGolsB() != null) stmt.setInt(7, p.getGolsB()); else stmt.setNull(7, Types.INTEGER);
                if (p.getVencedorId() != null) stmt.setLong(8, p.getVencedorId()); else stmt.setNull(8, Types.BIGINT);
                if (p.getPerdedorId() != null) stmt.setLong(9, p.getPerdedorId()); else stmt.setNull(9, Types.BIGINT);
                if (p.getProximaPartidaVencedorId() != null) stmt.setLong(10, p.getProximaPartidaVencedorId()); else stmt.setNull(10, Types.BIGINT);
                if (p.getProximaPartidaPerdedorId() != null) stmt.setLong(11, p.getProximaPartidaPerdedorId()); else stmt.setNull(11, Types.BIGINT);

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        p.setId(rs.getLong(1));
                    }
                }
            }

            conn.commit();
            System.out.println(">> [PartidaDAO] Lote de " + partidas.size() + " partidas salvo com sucesso!");

        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Retorna todas as partidas cadastradas com os dados dos times carregados.
     */
    public List<Partida> listarTodas() throws SQLException {
        List<Partida> partidas = new ArrayList<>();
        String sql = "SELECT * FROM partidas ORDER BY id ASC";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Partida p = extrairPartida(rs);

                // Carrega os dados dos times
                if (p.getTimeAId() != null) p.setTimeA(timeDAO.buscarPorId(p.getTimeAId()));
                if (p.getTimeBId() != null) p.setTimeB(timeDAO.buscarPorId(p.getTimeBId()));
                if (p.getVencedorId() != null) p.setVencedor(timeDAO.buscarPorId(p.getVencedorId()));
                if (p.getPerdedorId() != null) p.setPerdedor(timeDAO.buscarPorId(p.getPerdedorId()));

                partidas.add(p);
            }
        }
        return partidas;
    }

    /**
     * Busca uma partida por ID.
     */
    public Partida buscarPorId(Long id) throws SQLException {
        String sql = "SELECT * FROM partidas WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Partida p = extrairPartida(rs);
                    if (p.getTimeAId() != null) p.setTimeA(timeDAO.buscarPorId(p.getTimeAId()));
                    if (p.getTimeBId() != null) p.setTimeB(timeDAO.buscarPorId(p.getTimeBId()));
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Atualiza o placar e os vencedores/perdedores de uma partida.
     */
    public void atualizar(Partida p) throws SQLException {
        String sql = """
            UPDATE partidas
            SET time_a_id = ?, time_b_id = ?, gols_a = ?, gols_b = ?,
                vencedor_id = ?, perdedor_id = ?,
                prox_vencedor_id = ?, prox_perdedor_id = ?
            WHERE id = ?
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (p.getTimeAId() != null) stmt.setLong(1, p.getTimeAId()); else stmt.setNull(1, Types.BIGINT);
            if (p.getTimeBId() != null) stmt.setLong(2, p.getTimeBId()); else stmt.setNull(2, Types.BIGINT);
            if (p.getGolsA() != null) stmt.setInt(3, p.getGolsA()); else stmt.setNull(3, Types.INTEGER);
            if (p.getGolsB() != null) stmt.setInt(4, p.getGolsB()); else stmt.setNull(4, Types.INTEGER);
            if (p.getVencedorId() != null) stmt.setLong(5, p.getVencedorId()); else stmt.setNull(5, Types.BIGINT);
            if (p.getPerdedorId() != null) stmt.setLong(6, p.getPerdedorId()); else stmt.setNull(6, Types.BIGINT);
            if (p.getProximaPartidaVencedorId() != null) stmt.setLong(7, p.getProximaPartidaVencedorId()); else stmt.setNull(7, Types.BIGINT);
            if (p.getProximaPartidaPerdedorId() != null) stmt.setLong(8, p.getProximaPartidaPerdedorId()); else stmt.setNull(8, Types.BIGINT);
            stmt.setLong(9, p.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Limpa o chaveamento existente para permitir um novo sorteio.
     */
    public void limparTudo() throws SQLException {
        String sql = "DELETE FROM partidas";
        try (Connection conn = ConnectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println(">> [PartidaDAO] Chaveamento resetado com sucesso.");
        }
    }

    /**
     * Calcula o total de gols marcados por um time em todo o torneio.
     */
    public int calcularGolsFeitos(Long timeId) throws SQLException {
        int totalGols = 0;
        String sqlA = "SELECT SUM(gols_a) FROM partidas WHERE time_a_id = ? AND gols_a IS NOT NULL";
        String sqlB = "SELECT SUM(gols_b) FROM partidas WHERE time_b_id = ? AND gols_b IS NOT NULL";

        try (Connection conn = ConnectionFactory.getConnection()) {
            try (PreparedStatement stmtA = conn.prepareStatement(sqlA)) {
                stmtA.setLong(1, timeId);
                try (ResultSet rs = stmtA.executeQuery()) {
                    if (rs.next()) totalGols += rs.getInt(1);
                }
            }
            try (PreparedStatement stmtB = conn.prepareStatement(sqlB)) {
                stmtB.setLong(1, timeId);
                try (ResultSet rs = stmtB.executeQuery()) {
                    if (rs.next()) totalGols += rs.getInt(1);
                }
            }
        }
        return totalGols;
    }

    /**
     * Conta quantas vitórias um time acumulou no campeonato.
     */
    public int contarVitorias(Long timeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM partidas WHERE vencedor_id = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, timeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Conta quantas partidas o time jogou.
     */
    public int contarPartidasJogadas(Long timeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM partidas WHERE (time_a_id = ? OR time_b_id = ?) AND vencedor_id IS NOT NULL";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, timeId);
            stmt.setLong(2, timeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private Partida extrairPartida(ResultSet rs) throws SQLException {
        Partida p = new Partida();
        p.setId(rs.getLong("id"));
        p.setChave(rs.getString("chave"));
        p.setFase(rs.getString("fase"));
        p.setNumeroPartida(rs.getInt("numero_partida"));

        long aId = rs.getLong("time_a_id");
        p.setTimeAId(rs.wasNull() ? null : aId);

        long bId = rs.getLong("time_b_id");
        p.setTimeBId(rs.wasNull() ? null : bId);

        int gA = rs.getInt("gols_a");
        p.setGolsA(rs.wasNull() ? null : gA);

        int gB = rs.getInt("gols_b");
        p.setGolsB(rs.wasNull() ? null : gB);

        long vId = rs.getLong("vencedor_id");
        p.setVencedorId(rs.wasNull() ? null : vId);

        long perId = rs.getLong("perdedor_id");
        p.setPerdedorId(rs.wasNull() ? null : perId);

        long pvId = rs.getLong("prox_vencedor_id");
        p.setProximaPartidaVencedorId(rs.wasNull() ? null : pvId);

        long ppId = rs.getLong("prox_perdedor_id");
        p.setProximaPartidaPerdedorId(rs.wasNull() ? null : ppId);

        return p;
    }
}

