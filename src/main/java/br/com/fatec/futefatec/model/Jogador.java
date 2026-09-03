package br.com.fatec.futefatec.model;

import java.io.Serializable;

/**
 * Representa um jogador inscrito no torneio FuteFatec.
 * Pode ser titular (Goleiro, Fixo, Ala, Pivô) ou reserva.
 */
public class Jogador implements Serializable {
    private Long id;
    private Long timeId;
    private String nome;
    private String posicao;
    private boolean titular;

    public Jogador() {
    }

    public Jogador(String nome, String posicao, boolean titular) {
        this.nome = nome;
        this.posicao = posicao;
        this.titular = titular;
    }

    public Jogador(Long id, Long timeId, String nome, String posicao, boolean titular) {
        this.id = id;
        this.timeId = timeId;
        this.nome = nome;
        this.posicao = posicao;
        this.titular = titular;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTimeId() {
        return timeId;
    }

    public void setTimeId(Long timeId) {
        this.timeId = timeId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getPosicao() {
        return posicao;
    }

    public void setPosicao(String posicao) {
        this.posicao = posicao;
    }

    public boolean isTitular() {
        return titular;
    }

    public void setTitular(boolean titular) {
        this.titular = titular;
    }

    @Override
    public String toString() {
        return "Jogador{" +
                "nome='" + nome + '\'' +
                ", posicao='" + posicao + '\'' +
                ", titular=" + titular +
                '}';
    }
}