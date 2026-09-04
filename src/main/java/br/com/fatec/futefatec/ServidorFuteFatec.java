package br.com.fatec.futefatec;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import br.com.fatec.futefatec.dao.ConnectionFactory;
import br.com.fatec.futefatec.servlet.ChaveamentoServlet;
import br.com.fatec.futefatec.servlet.InscricaoServlet;
import jakarta.servlet.MultipartConfigElement;

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

        // Detecta dinamicamente onde estão index.html, style.css e script.js
        // (funciona tanto localmente na sua máquina quanto no ambiente Docker / Render)
        File baseDir = new File(".").getCanonicalFile();
        if (!new File(baseDir, "index.html").exists()) {
            File dirApp = new File("/app");
            if (dirApp.exists() && new File(dirApp, "index.html").exists()) {
                baseDir = dirApp.getCanonicalFile();
            } else {
                File parent = baseDir.getParentFile();
                if (parent != null && new File(parent, "index.html").exists()) {
                    baseDir = parent.getCanonicalFile();
                }
            }
        }
        String docBase = baseDir.getAbsolutePath();
        System.out.println(">> [ServidorFuteFatec] Diretório base dos arquivos estáticos: " + docBase);
        System.out.println(">> [ServidorFuteFatec] Verificação de arquivos estáticos:");
        System.out.println("   - index.html: " + (new File(baseDir, "index.html").exists() ? "OK" : "NÃO ENCONTRADO"));
        System.out.println("   - style.css:  " + (new File(baseDir, "style.css").exists() ? "OK" : "NÃO ENCONTRADO"));
        System.out.println("   - script.js:  " + (new File(baseDir, "script.js").exists() ? "OK" : "NÃO ENCONTRADO"));

        Context ctx = tomcat.addContext("", docBase);

        ctx.addWelcomeFile("index.html");

        // Configura os MIME types para que navegadores apliquem o CSS e JS corretamente
        ctx.addMimeMapping("html", "text/html; charset=UTF-8");
        ctx.addMimeMapping("htm", "text/html; charset=UTF-8");
        ctx.addMimeMapping("css", "text/css; charset=UTF-8");
        ctx.addMimeMapping("js", "application/javascript; charset=UTF-8");
        ctx.addMimeMapping("json", "application/json; charset=UTF-8");
        ctx.addMimeMapping("png", "image/png");
        ctx.addMimeMapping("jpg", "image/jpeg");
        ctx.addMimeMapping("jpeg", "image/jpeg");
        ctx.addMimeMapping("gif", "image/gif");
        ctx.addMimeMapping("svg", "image/svg+xml");
        ctx.addMimeMapping("ico", "image/x-icon");

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

        // Registra o filtro NoCacheFilter para que navegadores e o Render nunca sirvam HTML/CSS/JS obsoletos
        org.apache.tomcat.util.descriptor.web.FilterDef filterDef = new org.apache.tomcat.util.descriptor.web.FilterDef();
        filterDef.setFilterName("NoCacheFilter");
        filterDef.setFilterClass(br.com.fatec.futefatec.servlet.NoCacheFilter.class.getName());
        ctx.addFilterDef(filterDef);

        org.apache.tomcat.util.descriptor.web.FilterMap filterMap = new org.apache.tomcat.util.descriptor.web.FilterMap();
        filterMap.setFilterName("NoCacheFilter");
        filterMap.addURLPattern("/*");
        ctx.addFilterMap(filterMap);

        System.out.println("===============================================================");
        System.out.println("        FUTFATEC - SERVIDOR INICIADO COM SUCESSO!             ");
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

