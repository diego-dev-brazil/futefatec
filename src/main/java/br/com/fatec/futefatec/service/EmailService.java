package br.com.fatec.futefatec.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import br.com.fatec.futefatec.model.Jogador;
import br.com.fatec.futefatec.model.Time;

/**
 * ============================================================================
 * SERVIÇO DE E-MAILS TRANSACIONAIS (EmailService)
 * ============================================================================
 * Envia e-mails formatados em HTML para confirmação de inscrição de equipes e
 * notificação de exclusão de equipes.
 *
 * Características arquiteturais:
 * 1. 100% Java Standard Library (Socket / SSLSocket) - sem dependências externas.
 * 2. Totalmente assíncrono (CompletableFuture) - nunca bloqueia a requisição HTTP.
 * 3. Modo simulação inteligente: se as variáveis de ambiente SMTP não estiverem
 *    configuradas, exibe no console o log completo formatado sem estourar erros.
 * 4. Variáveis de ambiente suportadas:
 *    - SMTP_HOST (padrão: smtp.gmail.com)
 *    - SMTP_PORT (padrão: 587)
 *    - SMTP_USER (e-mail do remetente / usuário SMTP)
 *    - SMTP_PASSWORD (senha do remetente ou App Password)
 *    - SMTP_FROM (endereço de envio exibido)
 * ============================================================================
 */
public class EmailService {

    private static final String SMTP_HOST = getEnvOrDefault("SMTP_HOST", "smtp.gmail.com");
    private static final int SMTP_PORT = Integer.parseInt(getEnvOrDefault("SMTP_PORT", "587"));
    private static final String SMTP_USER = getEnvOrDefault("SMTP_USER", "");
    private static final String SMTP_PASSWORD = getEnvOrDefault("SMTP_PASSWORD", "");
    private static final String SMTP_FROM = getEnvOrDefault("SMTP_FROM", SMTP_USER.isEmpty() ? "noreply@futfatec.com.br" : SMTP_USER);

    private static String getEnvOrDefault(String key, String def) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            val = System.getProperty(key);
        }
        return (val != null && !val.isBlank()) ? val.trim() : def;
    }

    /**
     * Envia o e-mail de confirmação de inscrição de forma assíncrona.
     */
    public static void enviarConfirmacaoInscricao(Time time) {
        if (time == null || time.getEmail() == null || time.getEmail().isBlank()) {
            System.out.println(">> [EmailService] Inscrição sem e-mail do capitão informado. E-mail não enviado.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String assunto = "Confirmação de Inscrição no FUTFATEC 2026 - Equipe " + time.getNome();
                String corpoHtml = gerarHtmlConfirmacaoInscricao(time);
                dispararEmail(time.getEmail(), assunto, corpoHtml);
            } catch (Exception e) {
                System.err.println(">> [EmailService] Erro ao enviar e-mail de inscrição: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Envia o e-mail de aviso de exclusão de equipe de forma assíncrona.
     */
    public static void enviarNotificacaoExclusao(String nomeTime, String capitao, String email) {
        if (email == null || email.isBlank()) {
            System.out.println(">> [EmailService] Equipe excluída não possuía e-mail cadastrado. Notificação não enviada.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String assunto = "Aviso de Exclusão de Equipe - FUTFATEC 2026";
                String corpoHtml = gerarHtmlExclusao(nomeTime, capitao);
                dispararEmail(email, assunto, corpoHtml);
            } catch (Exception e) {
                System.err.println(">> [EmailService] Erro ao enviar e-mail de exclusão: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Dispara o e-mail via SMTP real ou simula o envio caso não haja credenciais.
     */
    private static void dispararEmail(String destinatario, String assunto, String corpoHtml) {
        if (SMTP_USER.isBlank() || SMTP_PASSWORD.isBlank()) {
            // MODO SIMULAÇÃO (desenvolvimento / ambiente sem SMTP configurado)
            System.out.println("================================================================================");
            System.out.println(">> [EmailService] [SIMULAÇÃO DE ENVIO DE E-MAIL]");
            System.out.println(">> Para: " + destinatario);
            System.out.println(">> Assunto: " + assunto);
            System.out.println(">> Status: Simulado com sucesso (configure SMTP_USER e SMTP_PASSWORD para envio real)");
            System.out.println("================================================================================");
            return;
        }

        try {
            enviarSmtp(SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, SMTP_FROM, destinatario, assunto, corpoHtml);
            System.out.println(">> [EmailService] E-mail enviado com sucesso para: " + destinatario);
        } catch (Exception e) {
            System.err.println(">> [EmailService] Falha ao enviar via SMTP (" + SMTP_HOST + ":" + SMTP_PORT + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cliente SMTP direto em puro Java com suporte a STARTTLS e AUTH LOGIN.
     */
    private static void enviarSmtp(String host, int port, String user, String pass, String from, String to, String subject, String htmlBody) throws Exception {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(15000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        lerResposta(reader); // 220

        enviarComando(writer, "EHLO localhost");
        String ehloResp = lerResposta(reader);

        // Se for porta 587 ou se o servidor suportar STARTTLS
        if (port == 587 || ehloResp.contains("STARTTLS")) {
            enviarComando(writer, "STARTTLS");
            lerResposta(reader); // 220 Ready to start TLS

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true);
            sslSocket.startHandshake();

            reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));

            enviarComando(writer, "EHLO localhost");
            lerResposta(reader);
        }

        // Autenticação AUTH LOGIN
        enviarComando(writer, "AUTH LOGIN");
        lerResposta(reader); // 334

        enviarComando(writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
        lerResposta(reader); // 334

        enviarComando(writer, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
        String authResp = lerResposta(reader); // 235 Authentication succeeded
        if (!authResp.startsWith("235")) {
            throw new RuntimeException("Falha na autenticação SMTP: " + authResp);
        }

        enviarComando(writer, "MAIL FROM:<" + from + ">");
        lerResposta(reader); // 250

        enviarComando(writer, "RCPT TO:<" + to + ">");
        lerResposta(reader); // 250

        enviarComando(writer, "DATA");
        lerResposta(reader); // 354

        // Cabeçalhos MIME
        String subjectEncoded = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String bodyBase64 = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8));

        StringBuilder emailMsg = new StringBuilder();
        emailMsg.append("From: FUTFATEC <").append(from).append(">\r\n");
        emailMsg.append("To: <").append(to).append(">\r\n");
        emailMsg.append("Subject: ").append(subjectEncoded).append("\r\n");
        emailMsg.append("MIME-Version: 1.0\r\n");
        emailMsg.append("Content-Type: text/html; charset=UTF-8\r\n");
        emailMsg.append("Content-Transfer-Encoding: base64\r\n");
        emailMsg.append("\r\n");
        emailMsg.append(bodyBase64).append("\r\n");
        emailMsg.append(".\r\n");

        writer.write(emailMsg.toString());
        writer.flush();
        lerResposta(reader); // 250 OK

        enviarComando(writer, "QUIT");
        try {
            socket.close();
        } catch (Exception ignored) {}
    }

    private static void enviarComando(BufferedWriter writer, String cmd) throws Exception {
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private static String lerResposta(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            // Códigos SMTP: linhas intermediárias têm '-' após os 3 dígitos (ex: 250-SIZE), a final tem espaço (ex: 250 OK)
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Gera template HTML moderno e responsivo para confirmação de inscrição.
     */
    private static String gerarHtmlConfirmacaoInscricao(Time time) {
        StringBuilder jogadoresHtml = new StringBuilder();
        if (time.getJogadores() != null && !time.getJogadores().isEmpty()) {
            int num = 1;
            for (Jogador j : time.getJogadores()) {
                String tipo = j.isTitular() ? "<span style=\"color:#059669; font-weight:600;\">Titular</span>" : "<span style=\"color:#d97706;\">Reserva</span>";
                jogadoresHtml.append("<tr style=\"border-bottom: 1px solid #e2e8f0;\">")
                        .append("<td style=\"padding: 10px 14px; color: #64748b; font-size: 14px;\">").append(num++).append("</td>")
                        .append("<td style=\"padding: 10px 14px; color: #1e293b; font-weight: 500; font-size: 14px;\">").append(escapeHtml(j.getNome())).append("</td>")
                        .append("<td style=\"padding: 10px 14px; color: #475569; font-size: 14px;\">").append(escapeHtml(j.getPosicao())).append("</td>")
                        .append("<td style=\"padding: 10px 14px; font-size: 14px;\">").append(tipo).append("</td>")
                        .append("</tr>");
            }
        } else {
            jogadoresHtml.append("<tr><td colspan=\"4\" style=\"padding: 14px; text-align: center; color: #64748b;\">Nenhum jogador listado</td></tr>");
        }

        String template = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirmação de Inscrição - FUTFATEC</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 30px 15px;">
                    <tr>
                        <td align="center">
                            <table width="100%" max-width="620px" border="0" cellspacing="0" cellpadding="0" style="max-width: 620px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;">
                                <!-- Header -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 35px 30px; text-align: center; border-bottom: 4px solid #10b981;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: 1.5px;">⚽ FUTFATEC 2026</h1>
                                        <p style="color: #94a3b8; margin: 6px 0 0 0; font-size: 14px; text-transform: uppercase; letter-spacing: 1px;">Torneio Oficial de Futebol FATEC</p>
                                    </td>
                                </tr>
                                <!-- Conteúdo Principal -->
                                <tr>
                                    <td style="padding: 35px 30px;">
                                        <!-- Badge Status -->
                                        <div style="text-align: center; margin-bottom: 25px;">
                                            <span style="display: inline-block; background-color: #d1fae5; color: #065f46; font-size: 13px; font-weight: 700; padding: 6px 16px; border-radius: 20px; text-transform: uppercase; letter-spacing: 0.5px;">
                                                ✓ Inscrição Confirmada
                                            </span>
                                        </div>

                                        <h2 style="color: #0f172a; margin: 0 0 12px 0; font-size: 20px;">Olá, {{CAPITAO}}!</h2>
                                        <p style="color: #475569; font-size: 15px; line-height: 1.6; margin: 0 0 25px 0;">
                                            Temos o prazer de confirmar que a sua equipe <strong style="color: #0f172a;">{{NOME_TIME}}</strong> foi devidamente inscrita no campeonato <strong>FUTFATEC 2026</strong>!
                                        </p>

                                        <!-- Card Resumo -->
                                        <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 30px;">
                                            <tr>
                                                <td style="padding: 16px 20px; border-bottom: 1px solid #e2e8f0;">
                                                    <span style="color: #64748b; font-size: 13px; text-transform: uppercase; font-weight: 600;">Equipe</span><br>
                                                    <strong style="color: #0f172a; font-size: 16px;">{{NOME_TIME}}</strong>
                                                </td>
                                                <td style="padding: 16px 20px; border-bottom: 1px solid #e2e8f0;">
                                                    <span style="color: #64748b; font-size: 13px; text-transform: uppercase; font-weight: 600;">Capitão / Responsável</span><br>
                                                    <strong style="color: #0f172a; font-size: 16px;">{{CAPITAO}}</strong>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 16px 20px;">
                                                    <span style="color: #64748b; font-size: 13px; text-transform: uppercase; font-weight: 600;">Data e Hora do Cadastro</span><br>
                                                    <span style="color: #334155; font-size: 14px;">{{DATA_INSCRICAO}}</span>
                                                </td>
                                                <td style="padding: 16px 20px;">
                                                    <span style="color: #64748b; font-size: 13px; text-transform: uppercase; font-weight: 600;">Total de Atletas</span><br>
                                                    <span style="color: #334155; font-size: 14px;">{{TOTAL_ATLETAS}} jogadores</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Tabela de Jogadores -->
                                        <h3 style="color: #0f172a; font-size: 16px; margin: 0 0 12px 0;">📋 Elenco Inscrito</h3>
                                        <table width="100%" border="0" cellspacing="0" cellpadding="0" style="border-collapse: collapse; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; margin-bottom: 25px;">
                                            <thead>
                                                <tr style="background-color: #f1f5f9; text-align: left;">
                                                    <th style="padding: 10px 14px; font-size: 12px; color: #475569; text-transform: uppercase;">#</th>
                                                    <th style="padding: 10px 14px; font-size: 12px; color: #475569; text-transform: uppercase;">Nome</th>
                                                    <th style="padding: 10px 14px; font-size: 12px; color: #475569; text-transform: uppercase;">Posição</th>
                                                    <th style="padding: 10px 14px; font-size: 12px; color: #475569; text-transform: uppercase;">Tipo</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {{JOGADORES_ROWS}}
                                            </tbody>
                                        </table>

                                        <!-- Aviso e Próximos Passos -->
                                        <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 16px; border-radius: 0 8px 8px 0; margin-bottom: 25px;">
                                            <h4 style="margin: 0 0 6px 0; color: #1e40af; font-size: 14px;">📌 Próximos Passos & Informações Importantes</h4>
                                            <ul style="margin: 0; padding-left: 20px; color: #1e3a8a; font-size: 13px; line-height: 1.5;">
                                                <li>O chaveamento oficial dos jogos será divulgado assim que as inscrições forem concluídas.</li>
                                                <li>Acompanhe a tabela e o status das partidas diretamente no portal do FUTFATEC.</li>
                                                <li>Compareça ao campo com documento com foto e uniforme da equipe nos dias de jogos.</li>
                                            </ul>
                                        </div>

                                        <p style="color: #64748b; font-size: 14px; margin: 0; line-height: 1.5;">
                                            Boa sorte à sua equipe! Que vença o melhor futebol!<br>
                                            <strong>Comissão Organizadora FUTFATEC</strong>
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 30px; text-align: center;">
                                        <p style="color: #94a3b8; font-size: 12px; margin: 0; line-height: 1.4;">
                                            Este é um e-mail automático do sistema <strong>FUTFATEC</strong>.<br>
                                            Faculdade de Tecnologia de São Paulo (FATEC) • 2026
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """;

        return template
                .replace("{{CAPITAO}}", escapeHtml(time.getCapitao()))
                .replace("{{NOME_TIME}}", escapeHtml(time.getNome()))
                .replace("{{DATA_INSCRICAO}}", time.getDataHoraInscricao() != null ? time.getDataHoraInscricao() : "")
                .replace("{{TOTAL_ATLETAS}}", String.valueOf(time.getJogadores() != null ? time.getJogadores().size() : 0))
                .replace("{{JOGADORES_ROWS}}", jogadoresHtml.toString());
    }

    /**
     * Gera template HTML para notificação de exclusão de equipe.
     */
    private static String gerarHtmlExclusao(String nomeTime, String capitao) {
        String template = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Aviso de Exclusão - FUTFATEC</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 30px 15px;">
                    <tr>
                        <td align="center">
                            <table width="100%" max-width="620px" border="0" cellspacing="0" cellpadding="0" style="max-width: 620px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;">
                                <!-- Header -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 35px 30px; text-align: center; border-bottom: 4px solid #ef4444;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: 1.5px;">⚽ FUTFATEC 2026</h1>
                                        <p style="color: #94a3b8; margin: 6px 0 0 0; font-size: 14px; text-transform: uppercase; letter-spacing: 1px;">Torneio Oficial de Futebol FATEC</p>
                                    </td>
                                </tr>
                                <!-- Conteúdo Principal -->
                                <tr>
                                    <td style="padding: 35px 30px;">
                                        <!-- Badge Status -->
                                        <div style="text-align: center; margin-bottom: 25px;">
                                            <span style="display: inline-block; background-color: #fee2e2; color: #991b1b; font-size: 13px; font-weight: 700; padding: 6px 16px; border-radius: 20px; text-transform: uppercase; letter-spacing: 0.5px;">
                                                ✕ Inscrição Removida
                                            </span>
                                        </div>

                                        <h2 style="color: #0f172a; margin: 0 0 12px 0; font-size: 20px;">Olá, {{CAPITAO}}!</h2>
                                        <p style="color: #475569; font-size: 15px; line-height: 1.6; margin: 0 0 20px 0;">
                                            Comunicamos que a equipe <strong style="color: #dc2626;">{{NOME_TIME}}</strong> foi <strong>removida</strong> do cadastro de equipes do <strong>FUTFATEC 2026</strong> pela administração do torneio.
                                        </p>

                                        <div style="background-color: #fff7ed; border-left: 4px solid #f97316; padding: 16px; border-radius: 0 8px 8px 0; margin-bottom: 25px;">
                                            <h4 style="margin: 0 0 6px 0; color: #9a3412; font-size: 14px;">Motivos possíveis & Orientações:</h4>
                                            <ul style="margin: 0; padding-left: 20px; color: #7c2d12; font-size: 13px; line-height: 1.5;">
                                                <li>Solicitação direta do próprio capitão ou integrantes da equipe.</li>
                                                <li>Inconsistência nos dados informados ou regras de inscrição não atendidas.</li>
                                                <li>Ajustes e reformulação das chaves do torneio.</li>
                                            </ul>
                                        </div>

                                        <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 25px 0;">
                                            Caso a exclusão tenha ocorrido por engano ou se desejar obter mais esclarecimentos, procure a comissão organizadora ou realize uma nova inscrição pelo portal antes do término do prazo.
                                        </p>

                                        <p style="color: #64748b; font-size: 14px; margin: 0; line-height: 1.5;">
                                            Atenciosamente,<br>
                                            <strong>Comissão Organizadora FUTFATEC</strong>
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 30px; text-align: center;">
                                        <p style="color: #94a3b8; font-size: 12px; margin: 0; line-height: 1.4;">
                                            Este é um e-mail automático do sistema <strong>FUTFATEC</strong>.<br>
                                            Faculdade de Tecnologia de São Paulo (FATEC) • 2026
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """;

        return template
                .replace("{{CAPITAO}}", escapeHtml(capitao))
                .replace("{{NOME_TIME}}", escapeHtml(nomeTime));
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
