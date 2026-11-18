package br.com.comissao.arquivo;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Armazenamento de comprovantes e documentos. A implementação padrão grava em disco;
 * pode ser trocada por um bucket em nuvem (S3, GCS, Azure Blob) implementando esta interface.
 */
public interface ArmazenamentoService {

    /** Salva o arquivo e devolve o identificador (nome) gerado. */
    String salvar(MultipartFile arquivo, String prefixo);

    String salvar(byte[] conteudo, String nomeOriginal, String prefixo);

    Resource carregar(String nome);
}
