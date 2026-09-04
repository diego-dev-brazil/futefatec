package br.com.fatec.futefatec.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.futefatec.dao.TimeDAO;
import br.com.fatec.futefatec.model.Jogador;
import br.com.fatec.futefatec.model.Time;
import br.com.fatec.futefatec.service.EmailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

/**
 * ============================================================================
 * SERVLET: InscricaoServlet (Integrado com JDBC / TimeDAO)
 * ============================================================================
 */
@WebServlet(name = "InscricaoServlet", urlPatterns = {"/inscrever", "/api/times"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,      // 1 MB
        maxFileSize = 1024 * 1024 * 10,       // 10 MB
        maxRequestSize = 1024 * 1024 * 25     // 25 MB
)
public class InscricaoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Camada de acesso a dados via JDBC (Relacional)
    private final TimeDAO timeDAO = new TimeDAO();

    // ObjectMapper do Jackson para converter objetos Java <-> JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Diretório onde os arquivos de logo serão salvos
    private String diretorioUploads;

    @Override
    public void init() throws ServletException {
        super.init();
        this.diretorioUploads = System.getProperty("user.dir") + File.separator + "uploads";
        File dir = new File(this.diretorioUploads);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        System.out.println(">> [InscricaoServlet] Inicializado com sucesso!");
        System.out.println(">> [InscricaoServlet] Diretório de uploads: " + this.diretorioUploads);
    }

    private void configurarCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
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
        resp.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = resp.getWriter();
        try {
            List<Time> times = timeDAO.listarTodos();
            out.print(objectMapper.writeValueAsString(times));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "erro");
            err.put("mensagem", "Erro ao consultar banco de dados: " + e.getMessage());
            out.print(objectMapper.writeValueAsString(err));
        }
        out.flush();
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        tratarDeletar(req, resp, out);
        out.flush();
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        tratarAtualizar(req, resp, out);
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        PrintWriter out = resp.getWriter();
        String uri = req.getRequestURI();

        if (uri.endsWith("/deletar")) {
            tratarDeletar(req, resp, out);
            out.flush();
            return;
        }

        if (uri.endsWith("/atualizar")) {
            tratarAtualizar(req, resp, out);
            out.flush();
            return;
        }

        Map<String, Object> resposta = new HashMap<>();

        try {
            String nomeTime = req.getParameter("Nome_do_Time");
            String capitao = req.getParameter("Capitao");
            String email = req.getParameter("Email");
            String qtdJogadoresStr = req.getParameter("Quantidade_Jogadores");

            if (nomeTime == null || nomeTime.trim().isEmpty() ||
                capitao == null || capitao.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resposta.put("status", "erro");
                resposta.put("mensagem", "Nome do time e nome do capitão são obrigatórios!");
                out.print(objectMapper.writeValueAsString(resposta));
                return;
            }

            int quantidadeJogadores = 5;
            if (qtdJogadoresStr != null && !qtdJogadoresStr.trim().isEmpty()) {
                try {
                    quantidadeJogadores = Integer.parseInt(qtdJogadoresStr);
                } catch (NumberFormatException ignored) {}
            }

            String nomeArquivoSalvo = "sem_logo.png";
            try {
                Part logoPart = req.getPart("Logo_do_Time");
                if (logoPart != null && logoPart.getSize() > 0) {
                    String nomeOriginal = Paths.get(logoPart.getSubmittedFileName()).getFileName().toString();
                    nomeArquivoSalvo = System.currentTimeMillis() + "_" + nomeOriginal.replaceAll("\\s+", "_");
                    String destinoFinal = this.diretorioUploads + File.separator + nomeArquivoSalvo;
                    logoPart.write(destinoFinal);
                    System.out.println(">> [Upload] Logo salvo em: " + destinoFinal);
                }
            } catch (Exception e) {
                System.err.println(">> [Aviso] Upload de logo não realizado ou padrão: " + e.getMessage());
            }

            Time time = new Time(nomeTime.trim(), capitao.trim(), email != null ? email.trim() : null, nomeArquivoSalvo, quantidadeJogadores);

            // Titulares
            processarTitular(req, time, "Jogador_Goleiro", "Goleiro");
            processarTitular(req, time, "Jogador_Fixo", "Fixo");
            processarTitular(req, time, "Jogador_Ala_Esq", "Ala Esquerda");
            processarTitular(req, time, "Jogador_Ala_Dir", "Ala Direita");
            processarTitular(req, time, "Jogador_Pivo", "Pivô");

            // Reservas
            for (int i = 1; i <= 3; i++) {
                String nomeReserva = req.getParameter("Jogador_Reserva_" + i);
                if (nomeReserva != null && !nomeReserva.trim().isEmpty()) {
                    time.adicionarJogador(new Jogador(nomeReserva.trim(), "Reserva " + i, false));
                }
            }

            // Persiste no banco via TimeDAO
            timeDAO.salvar(time);
            int totalTimes = timeDAO.listarTodos().size();
            System.out.println(">> [Banco de Dados] Time persistido com sucesso! ID: " + time.getId());

            // Envia e-mail de confirmação assincronamente (se e-mail estiver informado)
            EmailService.enviarConfirmacaoInscricao(time);

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Inscrição realizada com sucesso no FUTFATEC!");
            resposta.put("time", time);
            resposta.put("totalTimesInscritos", totalTimes);

            out.print(objectMapper.writeValueAsString(resposta));

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resposta.put("status", "erro");
            resposta.put("mensagem", "Erro interno no servidor: " + e.getMessage());
            out.print(objectMapper.writeValueAsString(resposta));
        } finally {
            out.flush();
        }
    }

    private void processarTitular(HttpServletRequest req, Time time, String paramName, String posicao) {
        String nome = req.getParameter(paramName);
        if (nome != null && !nome.trim().isEmpty()) {
            time.adicionarJogador(new Jogador(nome.trim(), posicao, true));
        } else {
            time.adicionarJogador(new Jogador("(Não informado)", posicao, true));
        }
    }

    /**
     * Processa a exclusão de um time e de seus jogadores associados.
     */
    private void tratarDeletar(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        if (!AdminServlet.isAdmin(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Acesso negado: apenas o administrador autenticado pode excluir equipes!\"}");
            return;
        }

        String idParam = req.getParameter("id");
        if (idParam == null || idParam.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Parâmetro 'id' do time é obrigatório!\"}");
            return;
        }

        try {
            long id = Long.parseLong(idParam.trim());
            Time timeParaDeletar = timeDAO.buscarPorId(id);

            timeDAO.deletar(id);

            // Notifica capitão por e-mail caso possua e-mail cadastrado
            if (timeParaDeletar != null && timeParaDeletar.getEmail() != null && !timeParaDeletar.getEmail().isBlank()) {
                EmailService.enviarNotificacaoExclusao(timeParaDeletar.getNome(), timeParaDeletar.getCapitao(), timeParaDeletar.getEmail());
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Time e seus jogadores excluídos com sucesso!");
            resposta.put("totalTimesRestantes", timeDAO.listarTodos().size());
            out.print(objectMapper.writeValueAsString(resposta));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro ao excluir time: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Processa a alteração de dados cadastrais (nome e capitão) do time.
     */
    private void tratarAtualizar(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        if (!AdminServlet.isAdmin(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Acesso negado: apenas o administrador autenticado pode alterar dados das equipes!\"}");
            return;
        }

        String idParam = req.getParameter("id");
        String nome = req.getParameter("nome");
        String capitao = req.getParameter("capitao");

        if (idParam == null || nome == null || capitao == null ||
            nome.trim().isEmpty() || capitao.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Parâmetros id, nome e capitão são obrigatórios para alteração!\"}");
            return;
        }

        try {
            long id = Long.parseLong(idParam.trim());
            Time timeExistente = timeDAO.buscarPorId(id);

            if (timeExistente == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"status\":\"erro\",\"mensagem\":\"Time com ID " + id + " não encontrado.\"}");
                return;
            }

            timeExistente.setNome(nome.trim());
            timeExistente.setCapitao(capitao.trim());
            timeDAO.atualizar(timeExistente);

            resp.setStatus(HttpServletResponse.SC_OK);
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Dados do time atualizados com sucesso!");
            resposta.put("time", timeExistente);
            out.print(objectMapper.writeValueAsString(resposta));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro ao atualizar time: " + e.getMessage() + "\"}");
        }
    }
}

