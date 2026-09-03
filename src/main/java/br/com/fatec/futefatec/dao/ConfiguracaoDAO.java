package br.com.fatec.futefatec.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ============================================================================
 * PADRÃO DAO: ConfiguracaoDAO
 * ============================================================================
 * Gerencia as configurações globais do torneio (ex: chaveamento liberado).
 * ============================================================================
 */
public class ConfiguracaoDAO {

    public static final String CHAVE_LIBERADO = "chaveamento_liberado";

    /**
     * Retorna se o chaveamento foi liberado pelo administrador para o público.
     */
    public boolean isChaveamentoLiberado() {
        String valor = obter(CHAVE_LIBERADO, "false");
        return "true".equalsIgnoreCase(valor);
    }

    /**
     * Atualiza o estado de liberação do chaveamento.
     */
    public void setChaveamentoLiberado(boolean liberado) throws SQLException {
        salvar(CHAVE_LIBERADO, String.valueOf(liberado));
    }

    /**
     * Obtém um valor de configuração por chave.
     */
    public String obter(String chave, String valorPadrao) {
        String sql = "SELECT valor FROM configuracoes WHERE chave = ?";

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chave);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("valor");
                }
            }
        } catch (SQLException e) {
            System.err.println(">> [ConfiguracaoDAO] Erro ao buscar configuração '" + chave + "': " + e.getMessage());
        }
        return valorPadrao;
    }

    /**
     * Salva ou atualiza uma configuração no banco.
     */
    public void salvar(String chave, String valor) throws SQLException {
        String sql = """
            MERGE INTO configuracoes KEY (chave) VALUES (?, ?)
        """;

        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chave);
            stmt.setString(2, valor);
            stmt.executeUpdate();
            System.out.println(">> [ConfiguracaoDAO] Configuração '" + chave + "' definida como '" + valor + "'.");
        }
    }
}

