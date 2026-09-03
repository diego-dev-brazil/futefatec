package br.com.fatec.futefatec;

import br.com.fatec.futefatec.dao.ConnectionFactory;
import br.com.fatec.futefatec.servlet.ChaveamentoServlet;
import br.com.fatec.futefatec.servlet.InscricaoServlet;
import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

/**
 * Classe principal para inicializar o servidor Apache Tomcat embutido.
 */
public class ServidorFuteFatec {
    public static final int PORTA_PADRAO = 8085;

    public static int getPorta() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return PORTA_PADRAO;
    }

    public static void main(String[] args) throws Exception {
        // 1. Inicializa o banco de dados relacional (times, jogadores, partidas)
        ConnectionFactory.inicializarBanco();

        int porta = getPorta();

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(porta);
        tomcat.getConnector(); // Inicializa o conector HTTP padrão

        // Diretório base da aplicação (onde estão index.html, script.js, style.css, uploads)
        String docBase = new File(".").getAbsolutePath();
        Context ctx = tomcat.addContext("", docBase);

        // Configura o servlet padrão do Tomcat para servir arquivos estáticos (HTML, CSS, JS, uploads)
        Tomcat.addServlet(ctx, "default", "org.apache.catalina.servlets.DefaultServlet");
        ctx.addServletMappingDecoded("/", "default");

        // Registra o Servlet de Inscrição
        InscricaoServlet inscricaoServlet = new InscricaoServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "InscricaoServlet", inscricaoServlet);

        // Ativa o suporte a requisições multipart/form-data
        wrapper.setMultipartConfigElement(new MultipartConfigElement(
                System.getProperty("java.io.tmpdir"),
                1024 * 1024 * 10,   // Tamanho máximo de arquivo: 10 MB
                1024 * 1024 * 25,   // Tamanho máximo da requisição: 25 MB
                1024 * 1024         // Limite em memória: 1 MB
        ));

        // Mapeia os endpoints do Servlet de Inscrição e Gerenciamento de Times
        ctx.addServletMappingDecoded("/inscrever", "InscricaoServlet");
        ctx.addServletMappingDecoded("/api/times", "InscricaoServlet");
        ctx.addServletMappingDecoded("/api/times/deletar", "InscricaoServlet");
        ctx.addServletMappingDecoded("/api/times/atualizar", "InscricaoServlet");

        // Registra e mapeia o Servlet de Chaveamento do Torneio
        ChaveamentoServlet chaveamentoServlet = new ChaveamentoServlet();
        Tomcat.addServlet(ctx, "ChaveamentoServlet", chaveamentoServlet);
        ctx.addServletMappingDecoded("/api/chaveamento", "ChaveamentoServlet");
        ctx.addServletMappingDecoded("/api/chaveamento/*", "ChaveamentoServlet");
        ctx.addServletMappingDecoded("/api/times/sintese", "ChaveamentoServlet");

        // Registra e mapeia o Servlet de Administração do Torneio
        br.com.fatec.futefatec.servlet.AdminServlet adminServlet = new br.com.fatec.futefatec.servlet.AdminServlet();
        Tomcat.addServlet(ctx, "AdminServlet", adminServlet);
        ctx.addServletMappingDecoded("/api/admin/*", "AdminServlet");

        System.out.println("===============================================================");
        System.out.println("        FUTEFATEC - SERVIDOR INICIADO COM SUCESSO!             ");
        System.out.println("===============================================================");
        System.out.println(">> Frontend (Inscrição e Chaveamento): http://localhost:" + porta + "/index.html");
        System.out.println(">> Endpoint Inscrição:                 http://localhost:" + porta + "/inscrever");
        System.out.println(">> API Chaveamento:                    http://localhost:" + porta + "/api/chaveamento");
        System.out.println(">> API Administração:                  http://localhost:" + porta + "/api/admin/status");
        System.out.println("===============================================================");
        System.out.println("Pressione CTRL+C no terminal para encerrar.");

        tomcat.start();
        tomcat.getServer().await();
    }
}

