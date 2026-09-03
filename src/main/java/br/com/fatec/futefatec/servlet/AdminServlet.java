package br.com.fatec.futefatec.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

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
 * Gerencia a autenticação do administrador via HttpSession e o controle
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
    private static final String USUARIO_ADMIN_PADRAO = "admin";
    private static final String SENHA_ADMIN_PADRAO = "fatec2026";

    private final ConfiguracaoDAO configuracaoDAO = new ConfiguracaoDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSAO_ADMIN));
    }

    private String getSenhaAdminConfigurada() {
        String envPass = System.getenv("ADMIN_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass.trim();
        }
        return SENHA_ADMIN_PADRAO;
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
        String usuario = req.getParameter("usuario");
        String senha = req.getParameter("senha");

        String senhaEsperada = getSenhaAdminConfigurada();

        if (USUARIO_ADMIN_PADRAO.equals(usuario) && senhaEsperada.equals(senha)) {
            HttpSession session = req.getSession(true);
            session.setAttribute(SESSAO_ADMIN, Boolean.TRUE);

            System.out.println(">> [AdminServlet] Administrador logado com sucesso!");

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Login de administrador realizado com sucesso!");
            resposta.put("adminLogado", true);
            resposta.put("chaveamentoLiberado", configuracaoDAO.isChaveamentoLiberado());

            out.print(objectMapper.writeValueAsString(resposta));
        } else {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "erro");
            resposta.put("mensagem", "Usuário ou senha de administrador inválidos!");
            resposta.put("adminLogado", false);

            out.print(objectMapper.writeValueAsString(resposta));
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

