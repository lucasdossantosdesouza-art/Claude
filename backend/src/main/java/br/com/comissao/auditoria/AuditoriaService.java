package br.com.comissao.auditoria;

import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Histórico de quem alterou o quê e quando (por OS e geral). */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final LogAlteracaoRepository repository;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public record LogDto(Long id, Long osId, String usuario, String acao, String detalhe, LocalDateTime criadoEm) {
    }

    @Transactional
    public void registrar(Long osId, Usuario usuario, String acao, String detalhe) {
        LogAlteracao l = new LogAlteracao();
        l.setOsId(osId);
        l.setUsuarioId(usuario == null ? null : usuario.getId());
        l.setAcao(acao);
        l.setDetalhe(detalhe);
        l.setCriadoEm(LocalDateTime.now(clock));
        repository.save(l);
    }

    @Transactional(readOnly = true)
    public List<LogDto> daOs(Long osId) {
        return converter(repository.findByOsIdOrderByIdDesc(osId));
    }

    @Transactional(readOnly = true)
    public List<LogDto> buscar(String q) {
        return converter(repository.buscar(q == null ? "" : q, PageRequest.of(0, 500)));
    }

    private List<LogDto> converter(List<LogAlteracao> logs) {
        Map<Long, String> nomes = usuarios.findAllById(logs.stream().map(LogAlteracao::getUsuarioId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome, (a, b) -> a));
        return logs.stream().map(l -> new LogDto(l.getId(), l.getOsId(),
                l.getUsuarioId() == null ? "sistema" : nomes.getOrDefault(l.getUsuarioId(), "?"),
                l.getAcao(), l.getDetalhe(), l.getCriadoEm())).toList();
    }
}
