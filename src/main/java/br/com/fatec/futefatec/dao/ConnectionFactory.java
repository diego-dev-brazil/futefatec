package br.com.fatec.futefatec.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * ============================================================================
 * PADRÃO FABRICA DE CONEXÕES (ConnectionFactory)
 * ============================================================================
 * Centraliza a criação de conexões JDBC com o banco de dados relacional.
 * 
 * Usamos o H2 Database em modo arquivo (embutido).
 * Os dados são gravados permanentemente no arquivo: ./data/futefatec.mv.db
 * ============================================================================
 */
public class ConnectionFactory {

    // Caminho do banco em disco (arquivo local ./data/futefatec.mv.db)
    private static final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private static final String URL = "jdbc:h2:file:" + DATA_DIR + File.separator + "futefatec;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASS = "";

    static {
        try {
            // Garante que a pasta "data" exista
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // Carrega explicitamente a classe do driver JDBC do H2
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Erro ao carregar o driver JDBC do H2: " + e.getMessage(), e);
        }
    }

    /**
     * Obtém uma nova conexão JDBC com o banco de dados.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /**
     * Cria as tabelas do sistema se ainda não existirem (DDL - Data Definition Language).
     * Chamado na inicialização do servidor.
     */
    public static void inicializarBanco() {
        String sqlTimes = """
            CREATE TABLE IF NOT EXISTS times (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                nome VARCHAR(100) NOT NULL,
                capitao VARCHAR(100) NOT NULL,
                email VARCHAR(150),
                logo_arquivo VARCHAR(255),
                qtd_jogadores INT NOT NULL,
                data_inscricao VARCHAR(30) NOT NULL
            );
        """;

        String sqlJogadores = """
            CREATE TABLE IF NOT EXISTS jogadores (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                time_id BIGINT NOT NULL,
                nome VARCHAR(100) NOT NULL,
                posicao VARCHAR(50) NOT NULL,
                titular BOOLEAN NOT NULL,
                FOREIGN KEY (time_id) REFERENCES times(id) ON DELETE CASCADE
            );
        """;

        String sqlPartidas = """
            CREATE TABLE IF NOT EXISTS partidas (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                chave VARCHAR(20) NOT NULL,
                fase VARCHAR(30) NOT NULL,
                numero_partida INT NOT NULL,
                time_a_id BIGINT,
                time_b_id BIGINT,
                gols_a INT,
                gols_b INT,
                vencedor_id BIGINT,
                perdedor_id BIGINT,
                prox_vencedor_id BIGINT,
                prox_perdedor_id BIGINT,
                FOREIGN KEY (time_a_id) REFERENCES times(id) ON DELETE SET NULL,
                FOREIGN KEY (time_b_id) REFERENCES times(id) ON DELETE SET NULL
            );
        """;

        String sqlConfiguracoes = """
            CREATE TABLE IF NOT EXISTS configuracoes (
                chave VARCHAR(50) PRIMARY KEY,
                valor VARCHAR(255) NOT NULL
            );
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(sqlTimes);
            stmt.execute(sqlJogadores);
            stmt.execute(sqlPartidas);
            stmt.execute(sqlConfiguracoes);

            // Migração segura para bancos existentes: adiciona coluna email se não existir
            try {
                stmt.execute("ALTER TABLE times ADD COLUMN IF NOT EXISTS email VARCHAR(150)");
            } catch (SQLException ignored) {
                // Coluna já existe ou não suportado
            }

            // Insere configuração inicial de chaveamento liberado = false caso ainda não exista
            stmt.execute("""
                INSERT INTO configuracoes (chave, valor)
                SELECT 'chaveamento_liberado', 'false'
                WHERE NOT EXISTS (SELECT 1 FROM configuracoes WHERE chave = 'chaveamento_liberado')
            """);

            System.out.println(">> [Banco de Dados] Tabelas 'times', 'jogadores', 'partidas' e 'configuracoes' verificadas/criadas!");
            System.out.println(">> [Banco de Dados] Arquivo do banco localizado em: " + DATA_DIR);

        } catch (SQLException e) {
            System.err.println(">> [Erro] Falha ao inicializar o banco de dados relacional: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
