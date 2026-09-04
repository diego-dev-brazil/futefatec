package br.com.fatec.futefatec.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.futefatec.dao.ConfiguracaoDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * ============================================================================
 * SERVLET: AdminServlet
 * ============================================================================
 * Gerencia a autenticação do administrador via HttpSession com proteção contra
 * ataques de força bruta (Rate Limiting), comparação constante e controle
 * de liberação do chaveamento para o público geral.
 * ============================================================================
 */
@WebServlet(name = "AdminServlet", urlPatterns = {
        "/api/admin/login",
        "/api/admin/logout",
        "/api/admin/status",
        "/api/admin/toggle-chaveamento"
})
public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public static final String SESSAO_ADMIN = "adminLogado";
    private static final String USUARIO_ADMIN_PADRAO = "adminfatec";
    private static final String SENHA_ADMIN_PADRAO = "adminfatec2026";

    // Proteção contra ataques de força bruta (Rate Limiting em memória por IP)
    private static final Map<String, Integer> tentativasPorIp = new ConcurrentHashMap<>();
    private static final Map<String, Long> bloqueiosPorIp = new ConcurrentHashMap<>();
    private static final int MAX_TENTATIVAS = 5;
    private static final long TEMPO_BLOQUEIO_MS = 15 * 60 * 1000; // 15 minutos de bloqueio

    private final ConfiguracaoDAO configuracaoDAO = new ConfiguracaoDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSAO_ADMIN));
    }

    private String getUsuarioAdminConfigurado() {
        String envUser = System.getenv("ADMIN_USER");
        if (envUser != null && !envUser.trim().isEmpty()) {
            return envUser.trim();
        }
        return USUARIO_ADMIN_PADRAO;
    }

    private String getSenhaAdminConfigurada() {
        String envPass = System.getenv("ADMIN_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass.trim();
        }
        return SENHA_ADMIN_PADRAO;
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private void configurarCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        configurarCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String uri = req.getRequestURI();

        if (uri.endsWith("/status")) {
            boolean admin = isAdmin(req);
            boolean liberado = configuracaoDAO.isChaveamentoLiberado();

            Map<String, Object> dados = new HashMap<>();
            dados.put("status", "sucesso");
            dados.put("adminLogado", admin);
            dados.put("chaveamentoLiberado", liberado);

            out.print(objectMapper.writeValueAsString(dados));
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Rota não encontrada\"}");
        }
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String uri = req.getRequestURI();

        if (uri.endsWith("/login")) {
            tratarLogin(req, resp, out);
        } else if (uri.endsWith("/logout")) {
            tratarLogout(req, resp, out);
        } else if (uri.endsWith("/toggle-chaveamento")) {
            tratarToggleChaveamento(req, resp, out);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Rota não encontrada\"}");
            out.flush();
        }
    }

    private void tratarLogin(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        String ip = getClientIp(req);

        // 1. Verifica se o IP está bloqueado temporariamente por excesso de tentativas falhas
        Long bloqueadoAte = bloqueiosPorIp.get(ip);
        if (bloqueadoAte != null) {
            if (System.currentTimeMillis() < bloqueadoAte) {
                long minutosRestantes = Math.max(1, (bloqueadoAte - System.currentTimeMillis()) / (60 * 1000));
                resp.setStatus(429); // 429 Too Many Requests
                Map<String, Object> resposta = new HashMap<>();
                resposta.put("status", "erro");
                resposta.put("mensagem", "Muitas tentativas incorretas. Acesso bloqueado por mais " + minutosRestantes + " minuto(s) por segurança.");
                resposta.put("adminLogado", false);
                out.print(objectMapper.writeValueAsString(resposta));
                out.flush();
                return;
            } else {
                bloqueiosPorIp.remove(ip);
                tentativasPorIp.remove(ip);
            }
        }

        String usuario = req.getParameter("usuario");
        String senha = req.getParameter("senha");

        String usuarioEsperado = getUsuarioAdminConfigurado();
        String senhaEsperada = getSenhaAdminConfigurada();

        // 2. Comparação em tempo constante (anti-timing attack)
        boolean usuarioValido = usuario != null && MessageDigest.isEqual(
                usuarioEsperado.getBytes(StandardCharsets.UTF_8),
                usuario.trim().getBytes(StandardCharsets.UTF_8)
        );
        boolean senhaValida = senha != null && MessageDigest.isEqual(
                senhaEsperada.getBytes(StandardCharsets.UTF_8),
                senha.getBytes(StandardCharsets.UTF_8)
        );

        if (usuarioValido && senhaValida) {
            // Sucesso: limpa histórico de tentativas do IP
            tentativasPorIp.remove(ip);
            bloqueiosPorIp.remove(ip);

            HttpSession session = req.getSession(true);
            session.setAttribute(SESSAO_ADMIN, Boolean.TRUE);
            session.setMaxInactiveInterval(15 * 60); // 15 minutos de inatividade

            System.out.println(">> [AdminServlet] Administrador logado com sucesso! IP: " + ip);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Login de administrador realizado com sucesso!");
            resposta.put("adminLogado", true);
            resposta.put("chaveamentoLiberado", configuracaoDAO.isChaveamentoLiberado());

            out.print(objectMapper.writeValueAsString(resposta));
        } else {
            // Falha: incrementa contador de tentativas falhas
            int falhas = tentativasPorIp.getOrDefault(ip, 0) + 1;
            tentativasPorIp.put(ip, falhas);
            System.out.println(">> [ALERTA SEGURANÇA] Tentativa de login admin FALHOU (" + falhas + "/" + MAX_TENTATIVAS + "). IP: " + ip);

            if (falhas >= MAX_TENTATIVAS) {
                bloqueiosPorIp.put(ip, System.currentTimeMillis() + TEMPO_BLOQUEIO_MS);
                tentativasPorIp.remove(ip);
                System.out.println(">> [ALERTA SEGURANÇA] IP BLOQUEADO por 15 minutos: " + ip);

                resp.setStatus(429); // Too Many Requests
                Map<String, Object> resposta = new HashMap<>();
                resposta.put("status", "erro");
                resposta.put("mensagem", "Limite de 5 tentativas excedido. Acesso bloqueado por 15 minutos por segurança!");
                resposta.put("adminLogado", false);
                out.print(objectMapper.writeValueAsString(resposta));
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Map<String, Object> resposta = new HashMap<>();
                resposta.put("status", "erro");
                resposta.put("mensagem", "Usuário ou senha de administrador inválidos! (Tentativa " + falhas + " de " + MAX_TENTATIVAS + ")");
                resposta.put("adminLogado", false);
                out.print(objectMapper.writeValueAsString(resposta));
            }
        }
        out.flush();
    }

    private void tratarLogout(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        System.out.println(">> [AdminServlet] Administrador desconectado.");

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("status", "sucesso");
        resposta.put("mensagem", "Sessão de administrador encerrada.");
        resposta.put("adminLogado", false);

        out.print(objectMapper.writeValueAsString(resposta));
        out.flush();
    }

    private void tratarToggleChaveamento(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        if (!isAdmin(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Acesso negado: apenas o administrador pode liberar ou bloquear o chaveamento!\"}");
            out.flush();
            return;
        }

        try {
            String liberarParam = req.getParameter("liberar");
            boolean novoEstado;

            if (liberarParam != null && !liberarParam.trim().isEmpty()) {
                novoEstado = Boolean.parseBoolean(liberarParam);
            } else {
                novoEstado = !configuracaoDAO.isChaveamentoLiberado();
            }

            configuracaoDAO.setChaveamentoLiberado(novoEstado);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("chaveamentoLiberado", novoEstado);
            resposta.put("mensagem", novoEstado
                    ? "Chaveamento liberado para visualização do público!"
                    : "Chaveamento bloqueado para o público.");

            out.print(objectMapper.writeValueAsString(resposta));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro ao alterar estado do chaveamento: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }
}

