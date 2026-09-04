package br.com.fatec.futefatec.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma equipe inscrita no FuteFatec.
 * Contém os dados do time, logo, capitão e a lista de jogadores (titulares e reservas).
 */
public class Time implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nome;
    private String capitao;
    private String email;
    private String nomeArquivoLogo;
    private int quantidadeJogadores;
    private List<Jogador> jogadores = new ArrayList<>();
    private String dataHoraInscricao;

    public Time() {
        this.dataHoraInscricao = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    public Time(Long id, String nome, String capitao, String email, String nomeArquivoLogo, int quantidadeJogadores, String dataHoraInscricao) {
        this.id = id;
        this.nome = nome;
        this.capitao = capitao;
        this.email = email;
        this.nomeArquivoLogo = nomeArquivoLogo;
        this.quantidadeJogadores = quantidadeJogadores;
        this.dataHoraInscricao = dataHoraInscricao;
    }

    public Time(Long id, String nome, String capitao, String nomeArquivoLogo, int quantidadeJogadores, String dataHoraInscricao) {
        this(id, nome, capitao, null, nomeArquivoLogo, quantidadeJogadores, dataHoraInscricao);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Time(String nome, String capitao, String email, String nomeArquivoLogo, int quantidadeJogadores) {
        this();
        this.nome = nome;
        this.capitao = capitao;
        this.email = email;
        this.nomeArquivoLogo = nomeArquivoLogo;
        this.quantidadeJogadores = quantidadeJogadores;
    }

    public Time(String nome, String capitao, String nomeArquivoLogo, int quantidadeJogadores) {
        this(nome, capitao, null, nomeArquivoLogo, quantidadeJogadores);
    }

    public void adicionarJogador(Jogador jogador) {
        if (this.jogadores == null) {
            this.jogadores = new ArrayList<>();
        }
        this.jogadores.add(jogador);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCapitao() {
        return capitao;
    }

    public void setCapitao(String capitao) {
        this.capitao = capitao;
    }

    public String getNomeArquivoLogo() {
        return nomeArquivoLogo;
    }

    public void setNomeArquivoLogo(String nomeArquivoLogo) {
        this.nomeArquivoLogo = nomeArquivoLogo;
    }

    public int getQuantidadeJogadores() {
        return quantidadeJogadores;
    }

    public void setQuantidadeJogadores(int quantidadeJogadores) {
        this.quantidadeJogadores = quantidadeJogadores;
    }

    public List<Jogador> getJogadores() {
        return jogadores;
    }

    public void setJogadores(List<Jogador> jogadores) {
        this.jogadores = jogadores;
    }

    public String getDataHoraInscricao() {
        return dataHoraInscricao;
    }

    public void setDataHoraInscricao(String dataHoraInscricao) {
        this.dataHoraInscricao = dataHoraInscricao;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Time{" +
                "nome='" + nome + '\'' +
                ", capitao='" + capitao + '\'' +
                ", email='" + email + '\'' +
                ", logo='" + nomeArquivoLogo + '\'' +
                ", qtdJogadores=" + quantidadeJogadores +
                ", totalCadastrados=" + (jogadores != null ? jogadores.size() : 0) +
                ", inscricao='" + dataHoraInscricao + '\'' +
                '}';
    }
}
