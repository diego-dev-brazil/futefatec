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

    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public static String getGmailWebhookUrl() {
        return getFirstEnvOrProperty("", "GMAIL_WEBHOOK_URL", "EMAIL_WEBHOOK_URL", "WEBHOOK_EMAIL_URL", "GOOGLE_SCRIPT_URL");
    }

    public static String getBrevoApiKey() {
        return getFirstEnvOrProperty("", "BREVO_API_KEY", "SENDINBLUE_API_KEY");
    }

    public static String getResendApiKey() {
        return getFirstEnvOrProperty("", "RESEND_API_KEY", "RESEND_KEY");
    }

    public static String getSendGridApiKey() {
        return getFirstEnvOrProperty("", "SENDGRID_API_KEY", "SENDGRID_KEY");
    }

    public static String getSmtpHost() {
        return getFirstEnvOrProperty("smtp.gmail.com", "SMTP_HOST", "MAIL_HOST", "EMAIL_HOST");
    }

    public static int getSmtpPort() {
        String p = getFirstEnvOrProperty("587", "SMTP_PORT", "MAIL_PORT", "EMAIL_PORT");
        try {
            return Integer.parseInt(p.trim());
        } catch (Exception e) {
            return 587;
        }
    }

    public static String getSmtpUser() {
        return getFirstEnvOrProperty("", "SMTP_USER", "SMTP_USERNAME", "MAIL_USERNAME", "EMAIL_USER", "SMTP_EMAIL", "EMAIL");
    }

    public static String getSmtpPassword() {
        String pass = getFirstEnvOrProperty("", "SMTP_PASSWORD", "SMTP_PASS", "MAIL_PASSWORD", "EMAIL_PASSWORD", "SMTP_SENHA", "SENHA_EMAIL");
        // Remove espaços de senhas de aplicativo do Gmail (ex: "abcd efgh ijkl mnop" -> "abcdefghijklmnop")
        return pass != null ? pass.replaceAll("\\s+", "") : "";
    }

    public static String getSmtpFrom() {
        String from = getFirstEnvOrProperty("", "SMTP_FROM", "MAIL_FROM", "EMAIL_FROM");
        if (from.isBlank()) {
            String user = getSmtpUser();
            return user.isBlank() ? "noreply@futfatec.com.br" : user;
        }
        return from;
    }

    public static boolean isSmtpConfigurado() {
        return !getGmailWebhookUrl().isBlank()
                || !getBrevoApiKey().isBlank()
                || !getResendApiKey().isBlank()
                || !getSendGridApiKey().isBlank()
                || (!getSmtpUser().isBlank() && !getSmtpPassword().isBlank());
    }

    public static String getProvedorAtivo() {
        if (!getGmailWebhookUrl().isBlank()) return "Google Apps Script Webhook (Porta 443 HTTPS - futfatec@gmail.com)";
        if (!getBrevoApiKey().isBlank()) return "Brevo API (Porta 443 HTTPS)";
        if (!getResendApiKey().isBlank()) return "Resend API (Porta 443 HTTPS)";
        if (!getSendGridApiKey().isBlank()) return "SendGrid API (Porta 443 HTTPS)";
        if (!getSmtpUser().isBlank() && !getSmtpPassword().isBlank()) {
            return "SMTP Direto (" + getSmtpHost() + ":" + getSmtpPort() + ")";
        }
        return "Nenhum (Modo Simulação/Aviso)";
    }

    public static String mascararUrl(String url) {
        if (url == null || url.isBlank()) return "(não configurada)";
        url = url.trim();
        int len = url.length();
        if (len <= 25) return "***";
        if (len > 45) {
            return url.substring(0, 35) + "..." + url.substring(len - 10);
        }
        return url.substring(0, 10) + "..." + url.substring(len - 5);
    }

    private static String getFirstEnvOrProperty(String defaultValue, String... keys) {
        for (String key : keys) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) {
                return limparAspas(val.trim());
            }
            val = System.getProperty(key);
            if (val != null && !val.isBlank()) {
                return limparAspas(val.trim());
            }
        }
        return defaultValue;
    }

    private static String limparAspas(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
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
     * Dispara o e-mail via Webhook Google, Brevo API, Resend API ou SMTP.
     */
    private static void dispararEmail(String destinatario, String assunto, String corpoHtml) {
        String webhookUrl = getGmailWebhookUrl();
        String brevoKey = getBrevoApiKey();
        String resendKey = getResendApiKey();
        String sendgridKey = getSendGridApiKey();

        String host = getSmtpHost();
        int port = getSmtpPort();
        String user = getSmtpUser();
        String pass = getSmtpPassword();
        String from = getSmtpFrom();

        System.out.println("================================================================================");
        System.out.println(">> [EmailService] INÍCIO DO DISPARO DE E-MAIL");
        System.out.println(">> [EmailService] Destinatário: " + destinatario);
        System.out.println(">> [EmailService] Assunto: " + assunto);
        System.out.println(">> [EmailService] Provedor Principal: " + getProvedorAtivo());
        if (!webhookUrl.isBlank()) {
            System.out.println(">> [EmailService] Endpoint Webhook: " + mascararUrl(webhookUrl));
        }
        System.out.println("================================================================================");

        try {
            boolean enviado = false;

            // 1. Google Apps Script Webhook (Prioridade #1 - Porta 443 HTTPS - livre no Render)
            if (!webhookUrl.isBlank()) {
                try {
                    enviarViaWebhook(webhookUrl, destinatario, assunto, corpoHtml);
                    enviado = true;
                } catch (Exception eWeb) {
                    System.err.println(">> [EmailService] ❌ Falha via Google Apps Script Webhook: " + eWeb.getMessage());
                    if (brevoKey.isBlank() && resendKey.isBlank() && sendgridKey.isBlank() && (user.isBlank() || pass.isBlank())) {
                        throw eWeb;
                    }
                    System.out.println(">> [EmailService] 🔄 Acionando provedor de fallback...");
                }
            }

            // 2. Brevo API (Fallback 1 - Porta 443 HTTPS)
            if (!enviado && !brevoKey.isBlank()) {
                try {
                    enviarViaBrevo(brevoKey, from, destinatario, assunto, corpoHtml);
                    enviado = true;
                } catch (Exception eBrevo) {
                    System.err.println(">> [EmailService] ❌ Falha via Brevo API: " + eBrevo.getMessage());
                    if (resendKey.isBlank() && sendgridKey.isBlank() && (user.isBlank() || pass.isBlank())) {
                        throw eBrevo;
                    }
                    System.out.println(">> [EmailService] 🔄 Acionando provedor de fallback seguinte...");
                }
            }

            // 3. Resend API (Fallback 2 - Porta 443 HTTPS)
            if (!enviado && !resendKey.isBlank()) {
                try {
                    enviarViaResend(resendKey, from, destinatario, assunto, corpoHtml);
                    enviado = true;
                } catch (Exception eResend) {
                    System.err.println(">> [EmailService] ❌ Falha via Resend API: " + eResend.getMessage());
                    if (sendgridKey.isBlank() && (user.isBlank() || pass.isBlank())) {
                        throw eResend;
                    }
                    System.out.println(">> [EmailService] 🔄 Acionando provedor de fallback seguinte...");
                }
            }

            // 4. SendGrid API (Fallback 3 - Porta 443 HTTPS)
            if (!enviado && !sendgridKey.isBlank()) {
                try {
                    enviarViaSendGrid(sendgridKey, from, destinatario, assunto, corpoHtml);
                    enviado = true;
                } catch (Exception eSendGrid) {
                    System.err.println(">> [EmailService] ❌ Falha via SendGrid API: " + eSendGrid.getMessage());
                    if (user.isBlank() || pass.isBlank()) {
                        throw eSendGrid;
                    }
                    System.out.println(">> [EmailService] 🔄 Acionando provedor de fallback seguinte...");
                }
            }

            // 5. SMTP Direto (Fallback 4)
            if (!enviado && !user.isBlank() && !pass.isBlank()) {
                enviarSmtp(host, port, user, pass, from, destinatario, assunto, corpoHtml);
                System.out.println(">> [EmailService] ✅ E-mail entregue com sucesso via SMTP para: " + destinatario);
                enviado = true;
            }

            if (!enviado && webhookUrl.isBlank() && brevoKey.isBlank() && resendKey.isBlank() && sendgridKey.isBlank() && user.isBlank()) {
                System.err.println("================================================================================");
                System.err.println(">> [EmailService] ⚠️ NENHUMA CONFIGURAÇÃO DE E-MAIL DETECTADA!");
                System.err.println(">> Destinatário: " + destinatario);
                System.err.println(">> Assunto: " + assunto);
                System.err.println(">> Configure GMAIL_WEBHOOK_URL nas variáveis de ambiente do Render.");
                System.err.println("================================================================================");
            }
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("================================================================================");
            System.err.println(">> [EmailService] ❌ ERRO DE CONEXÃO SMTP: Connect timed out na porta " + port);
            System.err.println(">> ⚠️ MOTIVO: O Render BLOQUEIA conexões de saída nas portas SMTP 25, 465 e 587 no plano gratuito!");
            System.err.println(">> 💡 SOLUÇÃO: Use a variável GMAIL_WEBHOOK_URL (Porta 443 HTTPS livre no Render).");
            System.err.println("================================================================================");
        } catch (Exception e) {
            System.err.println(">> [EmailService] ❌ Falha ao enviar e-mail para " + destinatario + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Envia via Google Apps Script Webhook (executa como futfatec@gmail.com sobre HTTPS porta 443).
     */
    private static void enviarViaWebhook(String webhookUrl, String to, String subject, String htmlBody) throws Exception {
        String maskedUrl = mascararUrl(webhookUrl);
        System.out.println(">> [EmailService] [Webhook] Enviando requisição HTTP POST para Google Apps Script...");
        System.out.println(">> [EmailService] [Webhook] Destinatário: " + to);
        System.out.println(">> [EmailService] [Webhook] Assunto: " + subject);
        System.out.println(">> [EmailService] [Webhook] Endpoint: " + maskedUrl);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("to", to.trim());
        payload.put("subject", subject);
        payload.put("html", htmlBody);
        payload.put("htmlBody", htmlBody);

        String json = objectMapper.writeValueAsString(payload);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(webhookUrl.trim()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String responseBody = response.body() != null ? response.body().trim() : "";

        System.out.println(">> [EmailService] [Webhook] Resposta HTTP Status: " + statusCode);
        System.out.println(">> [EmailService] [Webhook] Resposta Corpo: " + (responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody));

        if (statusCode < 200 || statusCode >= 400) {
            String preview = responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody;
            throw new RuntimeException("Google Apps Script retornou HTTP " + statusCode + ": " + preview);
        }

        // Validação estrita do corpo JSON retornado pelo Google Apps Script
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            String preview = responseBody.length() > 250 ? responseBody.substring(0, 250) + "..." : responseBody;
            throw new RuntimeException("Resposta do Google Apps Script não é JSON válido (verifique se a URL está correta e se a implantação do Apps Script foi publicada como 'Qualquer pessoa' / 'Anyone'). HTTP " + statusCode + " - Conteúdo retornado: " + preview);
        }

        if (root != null && root.has("status")) {
            String status = root.get("status").asText();
            if ("sucesso".equalsIgnoreCase(status)) {
                System.out.println(">> [EmailService] ✅ E-mail REAL entregue com sucesso via Google Apps Script para: " + to);
                return;
            } else if ("erro".equalsIgnoreCase(status)) {
                String mensagemErro = root.has("mensagem") ? root.get("mensagem").asText() : "Erro sem detalhes informado pelo Apps Script";
                throw new RuntimeException("Google Apps Script reportou erro ao enviar e-mail: " + mensagemErro);
            }
        }

        throw new RuntimeException("Google Apps Script retornou resposta com formato inesperado: " + responseBody);
    }

    /**
     * Envia via Brevo API v3 (porta 443 HTTPS).
     */
    private static void enviarViaBrevo(String apiKey, String from, String to, String subject, String htmlBody) throws Exception {
        String senderEmail = getSmtpUser();
        if (senderEmail.isBlank()) senderEmail = "futfatec@gmail.com";

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        java.util.Map<String, String> sender = new java.util.HashMap<>();
        sender.put("name", "FUTFATEC 2026");
        sender.put("email", senderEmail);
        payload.put("sender", sender);

        java.util.List<java.util.Map<String, String>> toList = new java.util.ArrayList<>();
        java.util.Map<String, String> recipient = new java.util.HashMap<>();
        recipient.put("email", to.trim());
        toList.add(recipient);
        payload.put("to", toList);

        payload.put("subject", subject);
        payload.put("htmlContent", htmlBody);

        String json = objectMapper.writeValueAsString(payload);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", apiKey.trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(20))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println(">> [EmailService] ✅ E-mail REAL entregue com sucesso via Brevo API para: " + to);
        } else {
            throw new RuntimeException("Brevo API retornou HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Envia via Resend API (porta 443 HTTPS).
     */
    private static void enviarViaResend(String apiKey, String from, String to, String subject, String htmlBody) throws Exception {
        String fromHeader = getFirstEnvOrProperty("FUTFATEC <onboarding@resend.dev>", "RESEND_FROM");

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("from", fromHeader);
        payload.put("to", java.util.List.of(to.trim()));
        payload.put("subject", subject);
        payload.put("html", htmlBody);

        String json = objectMapper.writeValueAsString(payload);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(20))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println(">> [EmailService] ✅ E-mail REAL entregue com sucesso via Resend API para: " + to);
        } else {
            throw new RuntimeException("Resend API retornou HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Envia via SendGrid API (porta 443 HTTPS).
     */
    private static void enviarViaSendGrid(String apiKey, String from, String to, String subject, String htmlBody) throws Exception {
        String senderEmail = getSmtpUser();
        if (senderEmail.isBlank()) senderEmail = "futfatec@gmail.com";

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("personalizations", java.util.List.of(java.util.Map.of("to", java.util.List.of(java.util.Map.of("email", to.trim())))));
        payload.put("from", java.util.Map.of("email", senderEmail, "name", "FUTFATEC 2026"));
        payload.put("subject", subject);
        payload.put("content", java.util.List.of(java.util.Map.of("type", "text/html", "value", htmlBody)));

        String json = objectMapper.writeValueAsString(payload);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(20))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println(">> [EmailService] ✅ E-mail REAL entregue com sucesso via SendGrid API para: " + to);
        } else {
            throw new RuntimeException("SendGrid API retornou HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Cliente SMTP direto em puro Java com suporte a STARTTLS (587), SSL direto (465),
     * autenticação AUTH LOGIN e AUTH PLAIN.
     */
    private static void enviarSmtp(String host, int port, String user, String pass, String from, String to, String subject, String htmlBody) throws Exception {
        Socket socket;
        if (port == 465) {
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(host, port);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 15000);
        }
        socket.setSoTimeout(20000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        String banner = lerResposta(reader); // 220
        if (!banner.startsWith("220")) {
            throw new RuntimeException("Banner inicial SMTP inválido: " + banner);
        }

        enviarComando(writer, "EHLO localhost");
        String ehloResp = lerResposta(reader);

        // Se for porta 587 ou se o servidor suportar STARTTLS (e não estiver já em SSL direto 465)
        if (port != 465 && (port == 587 || ehloResp.contains("STARTTLS"))) {
            enviarComando(writer, "STARTTLS");
            String startTlsResp = lerResposta(reader); // 220 Ready to start TLS
            if (!startTlsResp.startsWith("220")) {
                throw new RuntimeException("Falha ao negociar STARTTLS: " + startTlsResp);
            }

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true);
            sslSocket.startHandshake();

            reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));

            enviarComando(writer, "EHLO localhost");
            ehloResp = lerResposta(reader);
        }

        // Autenticação AUTH LOGIN com fallback para AUTH PLAIN
        enviarComando(writer, "AUTH LOGIN");
        String authPrompt = lerResposta(reader); // 334
        if (authPrompt.startsWith("334")) {
            enviarComando(writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
            lerResposta(reader); // 334

            enviarComando(writer, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
            String authResp = lerResposta(reader); // 235 Authentication succeeded
            if (!authResp.startsWith("235")) {
                throw new RuntimeException("Falha na autenticação SMTP: " + authResp);
            }
        } else if (ehloResp.contains("AUTH PLAIN") || ehloResp.contains("PLAIN")) {
            String plainToken = "\0" + user + "\0" + pass;
            enviarComando(writer, "AUTH PLAIN " + Base64.getEncoder().encodeToString(plainToken.getBytes(StandardCharsets.UTF_8)));
            String plainResp = lerResposta(reader);
            if (!plainResp.startsWith("235")) {
                throw new RuntimeException("Falha na autenticação SMTP (AUTH PLAIN): " + plainResp);
            }
        } else {
            throw new RuntimeException("Servidor SMTP não aceitou autenticação: " + authPrompt);
        }

        // Provedores como Gmail exigem que o MAIL FROM seja o endereço da conta autenticada
        String envelopeFrom = user.contains("@") ? user : from;
        enviarComando(writer, "MAIL FROM:<" + envelopeFrom + ">");
        String mailFromResp = lerResposta(reader); // 250
        if (!mailFromResp.startsWith("250")) {
            throw new RuntimeException("Erro no comando MAIL FROM: " + mailFromResp);
        }

        enviarComando(writer, "RCPT TO:<" + to.trim() + ">");
        String rcptResp = lerResposta(reader); // 250
        if (!rcptResp.startsWith("250")) {
            throw new RuntimeException("Erro no comando RCPT TO para " + to + ": " + rcptResp);
        }

        enviarComando(writer, "DATA");
        String dataResp = lerResposta(reader); // 354
        if (!dataResp.startsWith("354")) {
            throw new RuntimeException("Erro no comando DATA: " + dataResp);
        }

        // Cabeçalhos MIME
        String subjectEncoded = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String bodyBase64 = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8));

        StringBuilder emailMsg = new StringBuilder();
        emailMsg.append("From: FUTFATEC <").append(envelopeFrom).append(">\r\n");
        emailMsg.append("To: <").append(to.trim()).append(">\r\n");
        emailMsg.append("Subject: ").append(subjectEncoded).append("\r\n");
        emailMsg.append("MIME-Version: 1.0\r\n");
        emailMsg.append("Content-Type: text/html; charset=UTF-8\r\n");
        emailMsg.append("Content-Transfer-Encoding: base64\r\n");
        emailMsg.append("\r\n");
        emailMsg.append(bodyBase64).append("\r\n");
        emailMsg.append(".\r\n");

        writer.write(emailMsg.toString());
        writer.flush();
        String finalResp = lerResposta(reader); // 250 OK
        if (!finalResp.startsWith("250")) {
            throw new RuntimeException("Servidor SMTP não confirmou o envio: " + finalResp);
        }

        try {
            enviarComando(writer, "QUIT");
            lerResposta(reader);
        } catch (Exception ignored) {}

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
