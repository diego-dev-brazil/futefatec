package br.com.fatec.futefatec.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.futefatec.dao.PartidaDAO;
import br.com.fatec.futefatec.dao.TimeDAO;
import br.com.fatec.futefatec.model.Partida;
import br.com.fatec.futefatec.model.Time;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ============================================================================
 * SERVLET: ChaveamentoServlet
 * ============================================================================
 * Gerencia o chaveamento do torneio com suporte a eliminação dupla (repescagem),
 * cálculo automático de Byes para quantidades arbitrárias de equipes e
 * endpoint para o Modal de Síntese do Time (gols, partidas e escalação).
 * ============================================================================
 */
@WebServlet(name = "ChaveamentoServlet", urlPatterns = {
        "/api/chaveamento",
        "/api/chaveamento/gerar",
        "/api/chaveamento/placar",
        "/api/chaveamento/reset",
        "/api/times/sintese"
})
public class ChaveamentoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final PartidaDAO partidaDAO = new PartidaDAO();
    private final TimeDAO timeDAO = new TimeDAO();
    private final br.com.fatec.futefatec.dao.ConfiguracaoDAO configuracaoDAO = new br.com.fatec.futefatec.dao.ConfiguracaoDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void configurarCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
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

        // 1. Endpoint do Modal de Síntese: GET /api/times/sintese?id=X (Acesso Público para consulta)
        if (uri.endsWith("/sintese")) {
            tratarSinteseTime(req, resp, out);
            return;
        }

        // 2. Endpoint da Chave: GET /api/chaveamento
        boolean admin = AdminServlet.isAdmin(req);
        boolean liberado = configuracaoDAO.isChaveamentoLiberado();

        // Se NÃO for admin e o chaveamento ainda NÃO estiver liberado, bloqueia com a mensagem oficial
        if (!admin && !liberado) {
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "bloqueado");
            resposta.put("chaveamentoLiberado", false);
            resposta.put("isAdmin", false);
            resposta.put("mensagem", "O chaveamento será liberado quando todas as equipes estiverem cadastradas.");
            resposta.put("partidas", Collections.emptyList());
            out.print(objectMapper.writeValueAsString(resposta));
            out.flush();
            return;
        }

        try {
            List<Partida> partidas = partidaDAO.listarTodas();
            int totalTimes = timeDAO.listarTodos().size();

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "sucesso");
            resposta.put("chaveamentoLiberado", liberado);
            resposta.put("isAdmin", admin);
            resposta.put("totalTimesCadastrados", totalTimes);
            resposta.put("chaveamentoGerado", !partidas.isEmpty());
            resposta.put("partidas", partidas);

            out.print(objectMapper.writeValueAsString(resposta));

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro ao carregar chaveamento: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        configurarCORS(resp);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // Operações de alteração do chaveamento (gerar, placar, reset) exigem autenticação de Admin
        if (!AdminServlet.isAdmin(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Acesso negado: apenas o administrador autenticado pode realizar esta ação!\"}");
            out.flush();
            return;
        }

        String uri = req.getRequestURI();

        try {
            if (uri.endsWith("/gerar")) {
                gerarChaveamento(req, resp, out);
            } else if (uri.endsWith("/placar")) {
                atualizarPlacar(req, resp, out);
            } else if (uri.endsWith("/reset")) {
                resetarChaveamento(req, resp, out);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"status\":\"erro\",\"mensagem\":\"Rota não encontrada\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro no servidor: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp); // Mapeia DELETE para resetar chaveamento
    }

    /**
     * Retorna os dados agregados para o Modal de Síntese do Time.
     */
    private void tratarSinteseTime(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"ID do time não informado\"}");
            out.flush();
            return;
        }

        try {
            long timeId = Long.parseLong(idParam);
            Time time = timeDAO.buscarPorId(timeId);

            if (time == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"status\":\"erro\",\"mensagem\":\"Time não encontrado\"}");
                out.flush();
                return;
            }

            int golsFeitos = partidaDAO.calcularGolsFeitos(timeId);
            int vitorias = partidaDAO.contarVitorias(timeId);
            int jogosDisputados = partidaDAO.contarPartidasJogadas(timeId);

            Map<String, Object> sintese = new HashMap<>();
            sintese.put("status", "sucesso");
            sintese.put("time", time);
            sintese.put("golsFeitos", golsFeitos);
            sintese.put("vitorias", vitorias);
            sintese.put("jogosDisputados", jogosDisputados);

            out.print(objectMapper.writeValueAsString(sintese));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Erro ao obter síntese: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }

    /**
     * Reseta o chaveamento do torneio.
     */
    private void resetarChaveamento(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws SQLException, IOException {
        partidaDAO.limparTudo();
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("status", "sucesso");
        resposta.put("mensagem", "Chaveamento resetado com sucesso! Agora você pode gerar um novo sorteio.");
        out.print(objectMapper.writeValueAsString(resposta));
    }

    /**
     * Atualiza o placar de uma partida e propaga vencedores e perdedores.
     */
    private void atualizarPlacar(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws SQLException, IOException {
        String partidaIdStr = req.getParameter("partidaId");
        String golsAStr = req.getParameter("golsA");
        String golsBStr = req.getParameter("golsB");

        if (partidaIdStr == null || golsAStr == null || golsBStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Parâmetros partidaId, golsA e golsB são obrigatórios\"}");
            return;
        }

        long partidaId = Long.parseLong(partidaIdStr);
        int golsA = Integer.parseInt(golsAStr);
        int golsB = Integer.parseInt(golsBStr);

        if (golsA == golsB) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Mata-mata não pode terminar em empate! Em caso de empate, decida nos pênaltis.\"}");
            return;
        }

        Partida p = partidaDAO.buscarPorId(partidaId);
        if (p == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Partida não encontrada\"}");
            return;
        }

        p.setGolsA(golsA);
        p.setGolsB(golsB);

        Long vencedorId = (golsA > golsB) ? p.getTimeAId() : p.getTimeBId();
        Long perdedorId = (golsA > golsB) ? p.getTimeBId() : p.getTimeAId();

        p.setVencedorId(vencedorId);
        p.setPerdedorId(perdedorId);
        partidaDAO.atualizar(p);

        // Propaga o vencedor para a próxima partida
        if (p.getProximaPartidaVencedorId() != null) {
            Partida proxVenc = partidaDAO.buscarPorId(p.getProximaPartidaVencedorId());
            if (proxVenc != null) {
                if (proxVenc.getTimeAId() == null) {
                    proxVenc.setTimeAId(vencedorId);
                } else if (!proxVenc.getTimeAId().equals(vencedorId)) {
                    proxVenc.setTimeBId(vencedorId);
                }
                partidaDAO.atualizar(proxVenc);
            }
        }

        // Propaga o perdedor para a chave de repescagem
        if (p.getProximaPartidaPerdedorId() != null) {
            Partida proxPerd = partidaDAO.buscarPorId(p.getProximaPartidaPerdedorId());
            if (proxPerd != null) {
                if (proxPerd.getTimeAId() == null) {
                    proxPerd.setTimeAId(perdedorId);
                } else if (!proxPerd.getTimeAId().equals(perdedorId)) {
                    proxPerd.setTimeBId(perdedorId);
                }
                partidaDAO.atualizar(proxPerd);
            }
        }

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("status", "sucesso");
        resposta.put("mensagem", "Placar atualizado e equipes avançadas com sucesso!");
        resposta.put("partidas", partidaDAO.listarTodas());
        out.print(objectMapper.writeValueAsString(resposta));
    }

    /**
     * Algoritmo de Geração Dinâmica de Chaveamento com Repescagem (Double Elimination).
     */
    private void gerarChaveamento(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws SQLException, IOException {
        List<Time> times = timeDAO.listarTodos();

        if (times.size() < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"erro\",\"mensagem\":\"Cadastre pelo menos 2 times para gerar o chaveamento! (Atualmente há " + times.size() + ")\"}");
            return;
        }

        // Embaralha os times para sorteio justo
        List<Time> sorteados = new ArrayList<>(times);
        Collections.shuffle(sorteados);

        // Limpa chaveamento anterior
        partidaDAO.limparTudo();

        List<Partida> partidasParaSalvar = new ArrayList<>();
        int n = sorteados.size();

        if (n == 2) {
            // Caso com 2 times: Final Direta + Repescagem/Revanche
            Partida p1 = new Partida(null, "PRINCIPAL", "FINAL", 1, sorteados.get(0).getId(), sorteados.get(1).getId());
            Partida p2 = new Partida(null, "GRANDE_FINAL", "GRANDE FINAL", 2, null, null);
            partidasParaSalvar.add(p1);
            partidasParaSalvar.add(p2);
            partidaDAO.salvarLote(partidasParaSalvar);

            // Vincula proxima partida
            p1.setProximaPartidaVencedorId(p2.getId());
            p1.setProximaPartidaPerdedorId(p2.getId());
            partidaDAO.atualizar(p1);

        } else if (n <= 4) {
            // Caso com 3 ou 4 times: Semifinais Principais, Repescagem e Grande Final
            Partida semi1 = new Partida(null, "PRINCIPAL", "SEMIFINAL", 1, sorteados.get(0).getId(), (n >= 4 ? sorteados.get(3).getId() : null));
            Partida semi2 = new Partida(null, "PRINCIPAL", "SEMIFINAL", 2, sorteados.get(1).getId(), sorteados.get(2).getId());
            Partida finalPrincipal = new Partida(null, "PRINCIPAL", "FINAL", 3, null, null);
            Partida repescagem = new Partida(null, "REPESCAGEM", "FINAL REPESCAGEM", 4, null, null);
            Partida grandeFinal = new Partida(null, "GRANDE_FINAL", "GRANDE FINAL", 5, null, null);

            partidasParaSalvar.add(semi1);
            partidasParaSalvar.add(semi2);
            partidasParaSalvar.add(finalPrincipal);
            partidasParaSalvar.add(repescagem);
            partidasParaSalvar.add(grandeFinal);
            partidaDAO.salvarLote(partidasParaSalvar);

            // Encadeamento do fluxo
            semi1.setProximaPartidaVencedorId(finalPrincipal.getId());
            semi1.setProximaPartidaPerdedorId(repescagem.getId());
            partidaDAO.atualizar(semi1);

            semi2.setProximaPartidaVencedorId(finalPrincipal.getId());
            semi2.setProximaPartidaPerdedorId(repescagem.getId());
            partidaDAO.atualizar(semi2);

            finalPrincipal.setProximaPartidaVencedorId(grandeFinal.getId());
            partidaDAO.atualizar(finalPrincipal);

            repescagem.setProximaPartidaVencedorId(grandeFinal.getId());
            partidaDAO.atualizar(repescagem);

            // Se for 3 times (semi1 teve Bye para o Time 1), avança o Time 1 automaticamente
            if (n == 3) {
                semi1.setGolsA(1);
                semi1.setGolsB(0);
                semi1.setVencedorId(sorteados.get(0).getId());
                partidaDAO.atualizar(semi1);
                finalPrincipal.setTimeAId(sorteados.get(0).getId());
                partidaDAO.atualizar(finalPrincipal);
            }

        } else {
            // Caso com 5 até 8 times: Quartas -> Semis -> Final -> Repescagem -> Grande Final
            // Partidas das Quartas de Final
            Partida q1 = new Partida(null, "PRINCIPAL", "QUARTAS", 1, sorteados.get(0).getId(), (n >= 8 ? sorteados.get(7).getId() : null));
            Partida q2 = new Partida(null, "PRINCIPAL", "QUARTAS", 2, (n >= 4 ? sorteados.get(3).getId() : null), (n >= 5 ? sorteados.get(4).getId() : null));
            Partida q3 = new Partida(null, "PRINCIPAL", "QUARTAS", 3, (n >= 2 ? sorteados.get(1).getId() : null), (n >= 7 ? sorteados.get(6).getId() : null));
            Partida q4 = new Partida(null, "PRINCIPAL", "QUARTAS", 4, (n >= 3 ? sorteados.get(2).getId() : null), (n >= 6 ? sorteados.get(5).getId() : null));

            Partida s1 = new Partida(null, "PRINCIPAL", "SEMIFINAL", 5, null, null);
            Partida s2 = new Partida(null, "PRINCIPAL", "SEMIFINAL", 6, null, null);
            Partida finalP = new Partida(null, "PRINCIPAL", "FINAL", 7, null, null);

            Partida rep1 = new Partida(null, "REPESCAGEM", "RODADA 1", 8, null, null);
            Partida rep2 = new Partida(null, "REPESCAGEM", "RODADA 1", 9, null, null);
            Partida repFinal = new Partida(null, "REPESCAGEM", "FINAL REPESCAGEM", 10, null, null);

            Partida grandeFinal = new Partida(null, "GRANDE_FINAL", "GRANDE FINAL", 11, null, null);

            partidasParaSalvar.addAll(List.of(q1, q2, q3, q4, s1, s2, finalP, rep1, rep2, repFinal, grandeFinal));
            partidaDAO.salvarLote(partidasParaSalvar);

            // Vinculações
            q1.setProximaPartidaVencedorId(s1.getId()); q1.setProximaPartidaPerdedorId(rep1.getId()); partidaDAO.atualizar(q1);
            q2.setProximaPartidaVencedorId(s1.getId()); q2.setProximaPartidaPerdedorId(rep1.getId()); partidaDAO.atualizar(q2);
            q3.setProximaPartidaVencedorId(s2.getId()); q3.setProximaPartidaPerdedorId(rep2.getId()); partidaDAO.atualizar(q3);
            q4.setProximaPartidaVencedorId(s2.getId()); q4.setProximaPartidaPerdedorId(rep2.getId()); partidaDAO.atualizar(q4);

            s1.setProximaPartidaVencedorId(finalP.getId()); s1.setProximaPartidaPerdedorId(repFinal.getId()); partidaDAO.atualizar(s1);
            s2.setProximaPartidaVencedorId(finalP.getId()); s2.setProximaPartidaPerdedorId(repFinal.getId()); partidaDAO.atualizar(s2);

            rep1.setProximaPartidaVencedorId(repFinal.getId()); partidaDAO.atualizar(rep1);
            rep2.setProximaPartidaVencedorId(repFinal.getId()); partidaDAO.atualizar(rep2);

            finalP.setProximaPartidaVencedorId(grandeFinal.getId()); partidaDAO.atualizar(finalP);
            repFinal.setProximaPartidaVencedorId(grandeFinal.getId()); partidaDAO.atualizar(repFinal);

            // Auto-avanço de Byes se timeBId for nulo
            for (Partida q : List.of(q1, q2, q3, q4)) {
                if (q.getTimeAId() != null && q.getTimeBId() == null) {
                    q.setGolsA(1);
                    q.setGolsB(0);
                    q.setVencedorId(q.getTimeAId());
                    partidaDAO.atualizar(q);
                    // Avança para semifinal
                    Partida destS = (q == q1 || q == q2) ? s1 : s2;
                    if (destS.getTimeAId() == null) destS.setTimeAId(q.getTimeAId());
                    else destS.setTimeBId(q.getTimeAId());
                    partidaDAO.atualizar(destS);
                }
            }
        }

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("status", "sucesso");
        resposta.put("mensagem", "Chaveamento com repescagem gerado com sucesso para " + n + " equipes!");
        resposta.put("partidas", partidaDAO.listarTodas());
        out.print(objectMapper.writeValueAsString(resposta));
    }
}

