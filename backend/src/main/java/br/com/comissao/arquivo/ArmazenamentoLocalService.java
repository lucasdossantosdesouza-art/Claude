package br.com.comissao.arquivo;

import br.com.comissao.config.AppProperties;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ArmazenamentoLocalService implements ArmazenamentoService {

    static final Set<String> EXTENSOES = Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif");
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path raiz;

    public ArmazenamentoLocalService(AppProperties props) {
        this.raiz = Path.of(props.storage().diretorio()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório de arquivos " + raiz, e);
        }
    }

    @Override
    public String salvar(MultipartFile arquivo, String prefixo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Anexe o arquivo.");
        }
        try {
            return salvar(arquivo.getBytes(), arquivo.getOriginalFilename(), prefixo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String salvar(byte[] conteudo, String nomeOriginal, String prefixo) {
        String ext = extensao(nomeOriginal);
        if (!EXTENSOES.contains(ext)) {
            throw new NegocioException("Envie PDF ou imagem (JPG, PNG, WEBP).");
        }
        String nome = prefixo + "_" + LocalDateTime.now().format(CARIMBO) + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        try {
            Files.write(raiz.resolve(nome), conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return nome;
    }

    @Override
    public Resource carregar(String nome) {
        Path p = raiz.resolve(Path.of(nome).getFileName().toString()).normalize();
        if (!p.startsWith(raiz) || !Files.exists(p)) {
            throw new NaoEncontradoException("Arquivo não encontrado");
        }
        return new PathResource(p);
    }

    static String extensao(String nome) {
        if (nome == null || !nome.contains(".")) {
            return "";
        }
        return nome.substring(nome.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
