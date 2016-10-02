
package br.ufsm.csi.so;

public class Diretorio {
    private String nomeArquivo;
    private int primeiroBloco;
    private int tamanho;

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public int getPrimeiroBloco() {
        return primeiroBloco;
    }

    public void setPrimeiroBloco(int primeiroBloco) {
        this.primeiroBloco = primeiroBloco;
    }

    public int getTamanho() {
        return tamanho;
    }

    public void setTamanho(int tamanho) {
        this.tamanho = tamanho;
    }
}
