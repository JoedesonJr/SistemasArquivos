# SistemasArquivos

Este sistema, fará a operação de um sistema de arquivos
FAT32 armazenado arquivos em um único arquivo do sistema, como se este arquivo fosse uma
unidade de armazenamento secundária. Através das chamadas à interface será permitido que
os usuários operem o sistema de arquivos.

Observações:
- Deverá ser desenvolvido utilizando a classe RandomAccessFile e a chamada seek(pos) para busca e leitura/gravação de blocos inteiros;
- Os blocos serão de 64KB;
- O tamanho do arquivo para armazenamento poderá ser definido na inicialização (caso o arquivo seja novo);
- O primeiro bloco será reservado para as informações do diretório (único), dentre as quais:
o Nome do arquivo: tamanho fixo de 8 caracteres e três para a extensão.
o Tamanho total do arquivo: inteiro de 32 bits.
o Bloco Inicial: inteiro de 32 bits.
- Os próximos blocos serão usados para o armazenamento da FAT (calcular a quantidade de blocos necessários de acordo com o 
tamanho do arquivo);
- Operações inválidas geram exceções como por exemplo: tentar ler um arquivo além
do seu tamanho, tentar gravar mais dados do que o espaço livre permite, etc.
