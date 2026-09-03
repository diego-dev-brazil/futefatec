package br.com.fatec.futefatec.dao;

import br.com.fatec.futefatec.model.Jogador;
import br.com.fatec.futefatec.model.Time;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * PADRÃO DAO (Data Access Object) - TimeDAO
 * ============================================================================
 * Isola todo o código SQL e acesso a dados do restante da aplicação.
 * O Servlet nunca escreve "INSERT" ou "SELECT"; ele apenas chama o DAO!
 * 
 * Inclui:
 * 1. PreparedStatement (para evitar ataques de SQL Injection)
 * 2. Transações ACID com commit/rollback (para atomicidade ao salvar Time + Jogadores)
 * ============================================================================
 */
public class TimeDAO {

    /**
     * Salva um time e todos os seus jogadores usando uma TRANSAÇÃO ATÔMICA.
     * Ou salva tudo com sucesso, ou desfaz tudo se houver qualquer erro!
     */
    public void salvar(Time time) throws SQLException {
        String sqlTime = """
            INSERT INTO times (nome, capitao, logo_arquivo, qtd_jogadores, data_inscricao)
            VALUES (?, ?, ?, ?, ?)
        """;

        String sqlJogador = """
            INSERT INTO jogadores (time_id, nome, posicao, titular)
            VALUES (?, ?, ?, ?)
        """;

        Connection conn = null;
        PreparedStatement stmtTime = null;
        PreparedStatement stmtJogador = null;

        try {
            conn = ConnectionFactory.getConnection();
            
            // 1. INICIA A TRANSAÇÃO: desativa o auto-commit
            conn.setAutoCommit(false);

            // 2. Insere o time e solicita a chave primária gerada (AUTO_INCREMENT)
            stmtTime = conn.prepareStatement(sqlTime, Statement.RETURN_GENERATED_KEYS);
            stmtTime.setString(1, time.getNome());
            stmtTime.setString(2, time.getCapitao());
            stmtTime.setString(3, time.getNomeArquivoLogo());
            stmtTime.setInt(4, time.getQuantidadeJogadores());
            stmtTime.setString(5, time.getDataHoraInscricao());
            stmtTime.executeUpdate();

            // 3. Recupera o ID gerado pelo banco para este time
            try (ResultSet rsChaves = stmtTime.getGeneratedKeys()) {
                if (rsChaves.next()) {
                    long idGerado = rsChaves.getLong(1);
                    time.setId(idGerado);
                } else {
                    throw new SQLException("Falha ao obter o ID gerado para o time!");
                }
            }

            // 4. Insere cada jogador associado ao time_id recém-criado
            stmtJogador = conn.prepareStatement(sqlJogador, Statement.RETURN_GENERATED_KEYS);
            for (Jogador jogador : time.getJogadores()) {
                stmtJogador.setLong(1, time.getId());
                stmtJogador.setString(2, jogador.getNome());
                stmtJogador.setString(3, jogador.getPosicao());
                stmtJogador.setBoolean(4, jogador.isTitular());
                stmtJogador.executeUpdate();

                try (ResultSet rsJog = stmtJogador.getGeneratedKeys()) {
                    if (rsJog.next()) {
                        jogador.setId(rsJog.getLong(1));
                        jogador.setTimeId(time.getId());
                    }
                }
            }

            // 5. EFETIVA A TRANSAÇÃO: Grava tudo definitivamente no banco
            conn.commit();
            System.out.println(">> [TimeDAO] Time e jogadores persistidos com sucesso! ID: " + time.getId());

        } catch (SQLException e) {
            // Em caso de qualquer erro, desfaz qualquer alteração parcial!
            if (conn != null) {
                try {
                    System.err.println(">> [TimeDAO] Erro detectado! Executando ROLLBACK da transação...");
                    conn.rollback();
                } catch (SQLException exRollback) {
                    exRollback.printStackTrace();
                }
            }
            throw e; // Repassa a exceção para o Servlet tratar e informar o usuário
        } finally {
            // Fecha recursos na ordem inversa de abertura
            if (stmtJogador != null) stmtJogador.close();
            if (stmtTime != null) stmtTime.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Retorna todos os times com seus respectivos jogadores cadastrados.
     */
    public List<Time> listarTodos() throws SQLException {
        List<Time> times = new ArrayList<>();
        String sql = "SELECT id, nome, capitao, logo_arquivo, qtd_jogadores, data_inscricao FROM times ORDER BY id ASC";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Time time = new Time(
                        rs.getLong("id"),
                        rs.getString("nome"),
                        rs.getString("capitao"),
                        rs.getString("logo_arquivo"),
                        rs.getInt("qtd_jogadores"),
                        rs.getString("data_inscricao")
                );

                // Carrega os jogadores deste time
                carregarJogadoresDoTime(conn, time);
                times.add(time);
            }
        }
        return times;
    }

    /**
     * Busca um time por ID com seus jogadores.
     */
    public Time buscarPorId(Long id) throws SQLException {
        String sql = "SELECT id, nome, capitao, logo_arquivo, qtd_jogadores, data_inscricao FROM times WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Time time = new Time(
                            rs.getLong("id"),
                            rs.getString("nome"),
                            rs.getString("capitao"),
                            rs.getString("logo_arquivo"),
                            rs.getInt("qtd_jogadores"),
                            rs.getString("data_inscricao")
                    );
                    carregarJogadoresDoTime(conn, time);
                    return time;
                }
            }
        }
        return null;
    }

    /**
     * Deleta um time pelo seu ID.
     * Graças à restrição FOREIGN KEY ... ON DELETE CASCADE definida no banco,
     * todos os jogadores vinculados a este time são removidos automaticamente!
     */
    public void deletar(Long id) throws SQLException {
        String sql = "DELETE FROM times WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            int linhasAfetadas = stmt.executeUpdate();
            if (linhasAfetadas == 0) {
                throw new SQLException("Nenhum time encontrado com o ID: " + id);
            }
            System.out.println(">> [TimeDAO] Time ID " + id + " e seus jogadores excluídos com sucesso via CASCADE!");
        }
    }

    /**
     * Atualiza os dados principais de um time (nome e capitão).
     */
    public void atualizar(Time time) throws SQLException {
        String sql = "UPDATE times SET nome = ?, capitao = ? WHERE id = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, time.getNome());
            stmt.setString(2, time.getCapitao());
            stmt.setLong(3, time.getId());
            int linhasAfetadas = stmt.executeUpdate();
            if (linhasAfetadas == 0) {
                throw new SQLException("Nenhum time encontrado para atualização com o ID: " + time.getId());
            }
            System.out.println(">> [TimeDAO] Time ID " + time.getId() + " atualizado com sucesso!");
        }
    }

    /**
     * Método auxiliar privado para buscar os jogadores de um time específico.
     */
    private void carregarJogadoresDoTime(Connection conn, Time time) throws SQLException {
        String sql = "SELECT id, time_id, nome, posicao, titular FROM jogadores WHERE time_id = ? ORDER BY id ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, time.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Jogador j = new Jogador(
                            rs.getLong("id"),
                            rs.getLong("time_id"),
                            rs.getString("nome"),
                            rs.getString("posicao"),
                            rs.getBoolean("titular")
                    );
                    time.adicionarJogador(j);
                }
            }
        }
    }
}

