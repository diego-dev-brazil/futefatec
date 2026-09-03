package br.com.fatec.futefatec.model;

import java.io.Serializable;

/**
 * Representa um confronto no chaveamento do torneio FuteFatec.
 * Pode pertencer à Chave Principal (Mata-Mata), à Chave de Repescagem ou à Grande Final.
 */
public class Partida implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String chave; // "PRINCIPAL", "REPESCAGEM", "GRANDE_FINAL"
    private String fase;  // "RODADA_1", "QUARTAS", "SEMIFINAL", "FINAL"
    private int numeroPartida;

    private Long timeAId;
    private Long timeBId;
    private Integer golsA;
    private Integer golsB;

    private Long vencedorId;
    private Long perdedorId;

    private Long proximaPartidaVencedorId;
    private Long proximaPartidaPerdedorId;

    // Campos auxiliares para facilitar o envio das informações completas ao front-end
    private Time timeA;
    private Time timeB;
    private Time vencedor;
    private Time perdedor;

    public Partida() {
    }

    public Partida(Long id, String chave, String fase, int numeroPartida, Long timeAId, Long timeBId) {
        this.id = id;
        this.chave = chave;
        this.fase = fase;
        this.numeroPartida = numeroPartida;
        this.timeAId = timeAId;
        this.timeBId = timeBId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getFase() {
        return fase;
    }

    public void setFase(String fase) {
        this.fase = fase;
    }

    public int getNumeroPartida() {
        return numeroPartida;
    }

    public void setNumeroPartida(int numeroPartida) {
        this.numeroPartida = numeroPartida;
    }

    public Long getTimeAId() {
        return timeAId;
    }

    public void setTimeAId(Long timeAId) {
        this.timeAId = timeAId;
    }

    public Long getTimeBId() {
        return timeBId;
    }

    public void setTimeBId(Long timeBId) {
        this.timeBId = timeBId;
    }

    public Integer getGolsA() {
        return golsA;
    }

    public void setGolsA(Integer golsA) {
        this.golsA = golsA;
    }

    public Integer getGolsB() {
        return golsB;
    }

    public void setGolsB(Integer golsB) {
        this.golsB = golsB;
    }

    public Long getVencedorId() {
        return vencedorId;
    }

    public void setVencedorId(Long vencedorId) {
        this.vencedorId = vencedorId;
    }

    public Long getPerdedorId() {
        return perdedorId;
    }

    public void setPerdedorId(Long perdedorId) {
        this.perdedorId = perdedorId;
    }

    public Long getProximaPartidaVencedorId() {
        return proximaPartidaVencedorId;
    }

    public void setProximaPartidaVencedorId(Long proximaPartidaVencedorId) {
        this.proximaPartidaVencedorId = proximaPartidaVencedorId;
    }

    public Long getProximaPartidaPerdedorId() {
        return proximaPartidaPerdedorId;
    }

    public void setProximaPartidaPerdedorId(Long proximaPartidaPerdedorId) {
        this.proximaPartidaPerdedorId = proximaPartidaPerdedorId;
    }

    public Time getTimeA() {
        return timeA;
    }

    public void setTimeA(Time timeA) {
        this.timeA = timeA;
    }

    public Time getTimeB() {
        return timeB;
    }

    public void setTimeB(Time timeB) {
        this.timeB = timeB;
    }

    public Time getVencedor() {
        return vencedor;
    }

    public void setVencedor(Time vencedor) {
        this.vencedor = vencedor;
    }

    public Time getPerdedor() {
        return perdedor;
    }

    public void setPerdedor(Time perdedor) {
        this.perdedor = perdedor;
    }

    public boolean isFinalizada() {
        return golsA != null && golsB != null && vencedorId != null;
    }
}

