
package br.ufsm.csi.so;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Random;

public class Disco {
    
    private final int TAM_BLOCO;
    private final int NUM_BLOCO;
    private RandomAccessFile rf;
    private boolean formatado;
    
    public boolean isFormatado() {
        return formatado;
    }
    
    public Disco (int tamBloco, int numBlocos) throws IOException {
        this.NUM_BLOCO = numBlocos;
        this.TAM_BLOCO = tamBloco;
        
        File f = new File("disco.fat");
        
        if(f.exists()){
            rf = new RandomAccessFile(f, "rw");
            if(rf.length() != numBlocos * tamBloco){
                throw new IllegalStateException("Disco com tamanho invalido");
            }
            formatado = true;
        }else{
            f.createNewFile();
            rf = new RandomAccessFile(f, "rw");
            rf.setLength(numBlocos * tamBloco);
            rf.getChannel().force(true);
            formatado = false;
        }
    }
    
    public void escreveBloco(int nBloco, byte[] dados) throws IOException{
        if(nBloco >= NUM_BLOCO) {
            throw new IllegalStateException("Numero de bloco invalido");
        }
        if(dados.length > TAM_BLOCO){
            throw new IllegalStateException("Bloco com tamanho invalido");
        }
        rf.seek(nBloco * TAM_BLOCO);
        rf.write(dados);
        rf.getChannel().force(true);
    }
    
    public byte[] leBloco(int nBloco) throws IOException{
        if(nBloco >= NUM_BLOCO) {
            throw new IllegalStateException("Numero de bloco invalido");
        }
        byte[] dados = new byte [TAM_BLOCO];
        rf.seek(nBloco * TAM_BLOCO);
        rf.read(dados);
        
        return dados;
    }
    
    public int blocoLivre() throws IOException {
        Random random = new Random();
        int numBloco;
        int tentativas = 0;
        
        ByteBuffer bloco = ByteBuffer.allocate(TAM_BLOCO);
        do{  // procura blocos aleatorio de 5 a 199 (5 primeiros para FAT e Diretorio)
            numBloco = random.nextInt(194)+5;
            bloco = ByteBuffer.wrap(leBloco(numBloco));
            tentativas++;
        }while(bloco.getInt() != 0 && tentativas < NUM_BLOCO);
        
        if(tentativas >= NUM_BLOCO){  // se ele nao encontra blocos procurando aleatoriamente, varre sequencial
            numBloco = -1;  // se não achar na tentativa sequencia, vai retornar -1 = não possui blocos 
            for(int i=2; i<NUM_BLOCO && tentativas >= NUM_BLOCO; i++){
                bloco = ByteBuffer.wrap(leBloco(i));
                if(bloco.getInt() == 0){
                    numBloco = i;
                    tentativas = -1;
                }
            }
            
            if(numBloco == -1) {
                throw new IllegalStateException("Não possui blocos livres");
            }
        }   
        return numBloco;
    }
}

