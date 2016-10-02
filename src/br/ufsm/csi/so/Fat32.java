
package br.ufsm.csi.so;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;

public class Fat32 implements SistemaArquivos{
    
    private final int NUM_BLOCOS = 200;
    private final int TAM_BLOCOS = 65536;
    private final int[] FAT = new int[NUM_BLOCOS];
    private final int QTD_BLOCOS_FAT = ((NUM_BLOCOS * 4) / TAM_BLOCOS) + 1;
    private Disco disco;
    private ArrayList<Diretorio> diretorioRaiz = new ArrayList<Diretorio>();

    public Fat32() throws IOException {
        disco = new Disco(TAM_BLOCOS, NUM_BLOCOS);
        if(!disco.isFormatado()){
            formataDisco();
        }else {
            leDiretorio();  // do disco para memoria
            leFAT();  // do disco para memoria
        }
    }

    @Override
    public void create(String fileName, byte[] data) {
        ByteBuffer dados = ByteBuffer.wrap(data);
        int tamanho = dados.capacity();  // tamanho total do arquivo
        
        try {
            int blocoLivre = disco.blocoLivre();  // encontra um bloco livre
            
            Diretorio diretorio = new Diretorio(); 
                diretorio.setNomeArquivo(fileName);
                diretorio.setTamanho(tamanho);
                diretorio.setPrimeiroBloco(blocoLivre);
            diretorioRaiz.add(diretorio); 

            if(tamanho > TAM_BLOCOS){  // se o arquivo ocupa mais de 1 bloco
                int qtd_bloco = (tamanho/TAM_BLOCOS) + 1;  // quantidade de blocos que ele vai ocupar
                int[] blocosLivres = new int[qtd_bloco];  // lista dos blocos que este arquivo vai ocupar
  
                for(int i=0; i<qtd_bloco; i++){
                    blocosLivres[i] = blocoLivre;
                    blocoLivre = disco.blocoLivre(); 

                    FAT[blocosLivres[i]] = blocoLivre;
                    
                    byte[] dado = new byte[TAM_BLOCOS];
                    if(i == qtd_bloco-1){  // se for a ultima parte, le só a parte que falta
                        System.arraycopy(data, i * TAM_BLOCOS, dado, 0, (tamanho - (i * TAM_BLOCOS)));
                    } else {
                        System.arraycopy(data, i * TAM_BLOCOS, dado, 0, TAM_BLOCOS);
                    }
                    disco.escreveBloco(blocosLivres[i], dado);
                }
                FAT[blocosLivres[qtd_bloco-1]] = 0;
            } else {  // ocupa só um bloco
                FAT[blocoLivre] = 0;
                disco.escreveBloco(blocoLivre, data);
            }
            escreveDiretorio();
            escreveFAT();
        } catch (IOException ex) { }
    }

    @Override
    public void append(String fileName, byte[] data) {
        int primeiroBloco = -1;
        int tamanho = 0;
        int diretorio = 0;
        
        for(int i=0; i<diretorioRaiz.size() && primeiroBloco == -1; i++){
            if(diretorioRaiz.get(i).getNomeArquivo().equals(fileName)){
                primeiroBloco = diretorioRaiz.get(i).getPrimeiroBloco();
                tamanho = diretorioRaiz.get(i).getTamanho();
                diretorio = i;
            }
        }
        
        int ultimoBloco = primeiroBloco;  // qual o ultimo bloco do arquivo
        int numBlocos = 1;  // quantos blocos ele tem
        int qtdUltimoBloco = tamanho;  // e quantos bytes tem no ultimo bloco
        
        while(FAT[ultimoBloco] != 0){  // encontra o ultimo bloco do arquivo
            ultimoBloco = FAT[ultimoBloco];
            numBlocos++;
        }
        
        if(numBlocos > 1)  // arquivo ocupa mais de um bloco
            qtdUltimoBloco = (TAM_BLOCOS * (numBlocos--)) - tamanho;
        
        try {
            byte[] bloco = disco.leBloco(ultimoBloco);  // le o ultimo bloco
            
            if((qtdUltimoBloco + data.length) > TAM_BLOCOS){  // se a informação inserida precisar de mais um bloco
                int qtdDados = TAM_BLOCOS - qtdUltimoBloco;  // quantos dados vai para o ultimo bloco 
                System.arraycopy(data, 0, bloco, qtdUltimoBloco, qtdDados);
                disco.escreveBloco(ultimoBloco, bloco);
                
                qtdDados = data.length - qtdDados;  // quantidade de dados a inserir
                int blocoLivre;
                while(qtdDados > 0){
                    blocoLivre = disco.blocoLivre();
                    if(qtdDados > TAM_BLOCOS){  
                        System.arraycopy(data, (data.length - qtdDados), bloco, 0, TAM_BLOCOS);
                        qtdDados = TAM_BLOCOS - qtdDados;  
                        if(qtdDados < 0) qtdDados *= -1;
                    }else{  // quantidade de dados a inserir cabe em um bloco                   
                        System.arraycopy(data, (data.length - qtdDados), bloco, 0, qtdDados);
                        qtdDados = 0;
                    }
                    disco.escreveBloco(blocoLivre, bloco);
                    FAT[ultimoBloco] = blocoLivre;
                    FAT[blocoLivre] = 0;
                    ultimoBloco = blocoLivre;
                }
                escreveFAT();
            }else{  // cabem tudo em um bloco só
                System.arraycopy(data, 0, bloco, qtdUltimoBloco, data.length);
                disco.escreveBloco(ultimoBloco, bloco);       
            }
            
            diretorioRaiz.get(diretorio).setTamanho
                (diretorioRaiz.get(diretorio).getTamanho() + data.length);  // aumenta o tamanho do arquivo
            escreveDiretorio();
        } catch (IOException ex) { }
    }

    public byte[] read(String fileName, int offset, int limit) {
        int primeiroBloco = -1;
        int tamanho = 0;
        byte[] textoLido = new byte[limit - offset];
        
        for(int i=0; i<diretorioRaiz.size() && primeiroBloco == -1; i++){
            if(diretorioRaiz.get(i).getNomeArquivo().equals(fileName)){
                primeiroBloco = diretorioRaiz.get(i).getPrimeiroBloco();
                tamanho = diretorioRaiz.get(i).getTamanho();
            }
        }
        
        byte[] texto = new byte[tamanho];
        texto = leTodoArquivo(primeiroBloco, tamanho);  // le todo o arquivo 
        
        System.arraycopy(texto, offset, textoLido, 0, (limit-offset));  // le somente a parte escolhida
        
        return textoLido;
    }
    
    public byte[] leTodoArquivo(int primeiroBloco, int tamanho) {
        byte[] arquivo = new byte[tamanho];
        int dadosLidos = 0;
        ByteBuffer dados = ByteBuffer.allocate(TAM_BLOCOS);        
        int bloco = primeiroBloco;

        try {
            while(dadosLidos < tamanho){  // até que leia todos os dados do arquivo
                dados = ByteBuffer.wrap(disco.leBloco(bloco));
                              
                if(FAT[bloco] == 0){  // se é o ultimo bloco
                    System.arraycopy(dados.array(), 0, arquivo, dadosLidos, (tamanho - dadosLidos));
                    dadosLidos = tamanho;
                } else {                    
                    System.arraycopy(dados.array(), 0, arquivo, dadosLidos, TAM_BLOCOS);
                    dadosLidos += TAM_BLOCOS;
                    bloco = FAT[bloco];
                }     
            }
        } catch (IOException ex) { }
        
        return arquivo;
    }
    
    @Override
    public void remove(String fileName) {
        int primeiroBloco = -1;
        int tamanho;
        int diretorio = -1;
        
        for(int i=0; i<diretorioRaiz.size() && primeiroBloco == -1; i++){
            if(diretorioRaiz.get(i).getNomeArquivo().equals(fileName)){
                primeiroBloco = diretorioRaiz.get(i).getPrimeiroBloco();
                tamanho = diretorioRaiz.get(i).getTamanho();
                diretorio = i;
            }
        }
        
        int bloco = primeiroBloco;
        try {
        while(FAT[bloco] != 0){  // remove do primeiro bloco até o penultimo
            bloco = FAT[primeiroBloco];
            FAT[primeiroBloco] = -1;  
            disco.escreveBloco(primeiroBloco, new byte[] {0});  
            primeiroBloco = bloco;
        }
        FAT[bloco] = -1;  // remove o ultimo blocos
        disco.escreveBloco(primeiroBloco, new byte[] {0});
        diretorioRaiz.remove(diretorio);
        escreveDiretorio();
        escreveFAT();
        } catch (IOException ex) { }
    }

    @Override
    public int freeSpace() {
        int espaco = 0;
        for(int i=0; i < FAT.length; i++){
            if(FAT[i] == -1){
                espaco += TAM_BLOCOS;
            }
        }
        return espaco;
    }

    private void formataDisco() throws IOException {
        criaDiretorio();
        criaFat();
    }

    private void leDiretorio() throws IOException {
        byte[] bloco = disco.leBloco(0);
        ByteBuffer bbuffer = ByteBuffer.wrap(bloco);
        int quant = bbuffer.getInt();
        for(int i=0; i < quant; i++){
            Diretorio entr = new Diretorio();
            StringBuffer sb = new StringBuffer();
            for(int j=0; j < 12; j++){
                char c = bbuffer.getChar();
                sb.append(c);
            }
            entr.setNomeArquivo(sb.toString());
            entr.setTamanho(bbuffer.getInt());
            entr.setPrimeiroBloco(bbuffer.getInt());

            diretorioRaiz.add(entr);
        }
    }

    private void criaDiretorio() throws IOException {
        ByteBuffer bbuffer = ByteBuffer.allocate(TAM_BLOCOS);
        bbuffer.putInt(0);
        disco.escreveBloco(0, bbuffer.array());
    }

    private void criaFat() throws IOException {
        FAT[0] = 0;
        for(int i = 1; i <= QTD_BLOCOS_FAT; i++){
            FAT[i] = 0;
        }
        for(int i = QTD_BLOCOS_FAT + 1; i < FAT.length; i++){
            FAT[i] = -1;
        }
        escreveFAT();
    }

    private void leFAT() throws IOException {
        byte[] bbuffer = new byte[QTD_BLOCOS_FAT * TAM_BLOCOS];
        for(int i=0; i < QTD_BLOCOS_FAT; i++){
            byte[] bloco = disco.leBloco(i+1);
            ByteBuffer buf = ByteBuffer.wrap(bloco);
            System.arraycopy(bloco, 0, bbuffer, i * TAM_BLOCOS, TAM_BLOCOS);
        }
        ByteBuffer buf = ByteBuffer.wrap(bbuffer);
        for(int i=0; i < FAT.length; i++){
            FAT[i] = buf.getInt();
        }
    }

    private void escreveFAT() throws IOException {
        ByteBuffer b = ByteBuffer.allocate(TAM_BLOCOS);
        int bloco = 1;
        for(int i=0; i < FAT.length; i++){
            b.putInt(FAT[i]);
            if(b.position() == TAM_BLOCOS){
                disco.escreveBloco(bloco, b.array());
                bloco++;
            }
        }
        disco.escreveBloco(bloco, b.array());
    }
    
    private void escreveDiretorio() throws IOException {
        ByteBuffer b = ByteBuffer.allocate(TAM_BLOCOS);
        int bloco = 0;
        b.putInt(diretorioRaiz.size());
        for(int i=0; i < diretorioRaiz.size(); i++){
            for(int j=0; j<12; j++){
                char c = diretorioRaiz.get(i).getNomeArquivo().charAt(j);
                b.putChar(c);
            }
            b.putInt(diretorioRaiz.get(i).getTamanho());
            b.putInt(diretorioRaiz.get(i).getPrimeiroBloco());
            if(b.position() == TAM_BLOCOS){
                disco.escreveBloco(bloco, b.array());
                bloco++;
            }
        }
        disco.escreveBloco(bloco, b.array());
    }
    
    private void casoTeste() throws IOException {
        int opcao;
        String novoArquivo = "";
        String nomeArquivo = "";
        String extensao = "";
        String conteudo = "";
        int tamanho;
        int blocoLivre;
        int numBloco = 1;
        int offset;
        int limit;
        
        Scanner ler = new Scanner(System.in);
        do{
            System.out.println("\n-----------------");
            System.out.println("### Menu ###");
            System.out.println("1. Criar arquivo");
            System.out.println("2. Adicionar dados");
            System.out.println("3. Ler arquivo");
            System.out.println("4. Remover arquivo");
            System.out.println("5. Espaço livre");
            System.out.println("6. Ver diretorio e FAT");
            System.out.println("0. Sair");
            System.out.println("\nEscolha: ");
            opcao = ler.nextInt();
            
            switch(opcao){
                case 1:{
                    System.out.println("\n-----------------");
                    System.out.println("### Criar arquivo ###");
                    System.out.println("Nome do arquivo: ");  ler.nextLine(); nomeArquivo = ler.nextLine();
                    System.out.println("Extensão: ");  extensao = ler.nextLine();
                    System.out.println("Conteúdo: ");  conteudo = ler.nextLine();
                    
                    if(nomeArquivo.length() < 8){  
                        while(nomeArquivo.length() < 8)
                            nomeArquivo += " ";  
                    }
                    if(extensao.length() < 3){ 
                        while(extensao.length() < 3)
                            extensao += " ";  
                    }
                    
                    novoArquivo = nomeArquivo.substring(0, 8) + "." + extensao.substring(0, 3);
                    byte[] dados = conteudo.getBytes();
                    create(novoArquivo, dados);
                    System.out.println("Arquivo criado!");
                    break;
                }
                case 2:{
                    System.out.println("\n-----------------");
                    System.out.println("### Adicionar dados ###");
                    System.out.println("Nome do arquivo: "); ler.nextLine(); nomeArquivo = ler.nextLine();
                    boolean encontrado = false;
                    for(int i=0; i<diretorioRaiz.size() && encontrado == false; i++){
                        if(diretorioRaiz.get(i).getNomeArquivo().equals(nomeArquivo)){
                            System.out.println("Conteudo: "); conteudo = ler.nextLine();
                            byte[] dados = conteudo.getBytes();
                            append(nomeArquivo, dados);
                            System.out.println("Dados adicionados!");
                            encontrado = true;   
                        }
                    }
                    if(encontrado == false)
                        System.out.println("Arquivo não encontrado");

                    break;
                }
                case 3:{
                    System.out.println("\n-----------------");
                    System.out.println("### Ler arquivo ###");
                    System.out.println("Nome do arquivo: "); ler.nextLine(); nomeArquivo = ler.nextLine();
                    boolean encontrado = false;
                    for(int i=0; i<diretorioRaiz.size() && encontrado == false; i++){
                        if(diretorioRaiz.get(i).getNomeArquivo().equals(nomeArquivo)){
                            nomeArquivo = diretorioRaiz.get(i).getNomeArquivo();
                            
                            System.out.println("Arquivo encontrado: " +diretorioRaiz.get(i).getNomeArquivo()
                                + "  |  Tamanho: " +diretorioRaiz.get(i).getTamanho()
                                + "  |  Bloco inicial: " +diretorioRaiz.get(i).getPrimeiroBloco());
                            
                            System.out.println("Ler a partir de qual posicao (0 até " +(diretorioRaiz.get(i).getTamanho()-1) +"): ");
                            do { offset = ler.nextInt(); } while(offset < 0 || offset > diretorioRaiz.get(i).getTamanho() - 1);
                            System.out.println("Ler até (" +(offset+1) +" até " +diretorioRaiz.get(i).getTamanho() +", ou -1 até o final" +"): "); 
                            do { limit = ler.nextInt(); } while(limit < -1 || limit == 0 || limit > diretorioRaiz.get(i).getTamanho());
                            
                            if(limit == -1)
                                limit = diretorioRaiz.get(i).getTamanho();
    
                            byte[] byteTexto = read(nomeArquivo, offset, limit);
                            System.out.println("Texto: " +new String(byteTexto));

                            encontrado = true;   
                        }
                    }
                    if(encontrado == false)
                        System.out.println("Arquivo não encontrado");
                    break;
                }
                case 4:{
                    System.out.println("\n-----------------");
                    System.out.println("### Remover arquivo ###");
                    System.out.println("Nome do arquivo: "); ler.nextLine(); nomeArquivo = ler.nextLine();
                    boolean encontrado = false;
                    for(int i=0; i<diretorioRaiz.size() && encontrado == false; i++){
                        if(diretorioRaiz.get(i).getNomeArquivo().equals(nomeArquivo)){
                            nomeArquivo = diretorioRaiz.get(i).getNomeArquivo();
                            remove(nomeArquivo);
                            System.out.println("Arquivo removido!");
                            encontrado = true;   
                        }
                    }
                    if(encontrado == false)
                        System.out.println("Arquivo não encontrado");
                    break;
                }
                case 5:{
                    System.out.println("\n-----------------");
                    System.out.println("### Espaço livre: " +freeSpace() +" de " +(TAM_BLOCOS*NUM_BLOCOS) +""
                            + " -> " +(100-(freeSpace()*100)/(TAM_BLOCOS*NUM_BLOCOS)) +"% ###");
                    break;
                }
                case 6:{
                    System.out.println("\n-----------------");
                    System.out.println("### Diretorio ###");
                    for(int i=0; i<diretorioRaiz.size(); i++){
                        System.out.println("-----------------");
                        System.out.println("Nome: " +diretorioRaiz.get(i).getNomeArquivo());
                        System.out.println("Tamanho: " +diretorioRaiz.get(i).getTamanho());
                        System.out.println("Primeiro Bloco: " +diretorioRaiz.get(i).getPrimeiroBloco());
                    }
                    System.out.println("-----------------");
                    for(int i=0; i<NUM_BLOCOS; i++){
                        if(FAT[i] != -1 ){
                            System.out.println("FAT[" +i +"]: " +FAT[i]);
                        }
                    }
                    break;
                }
                default: if(opcao != 0) System.out.println("Opção invalida!");
            }
        }while(opcao != 0);
    }
    
    public static void main(String[] args) throws IOException{
        Fat32 fat = new Fat32();
        fat.casoTeste();        
    }

}
