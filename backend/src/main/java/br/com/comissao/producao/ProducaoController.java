package br.com.comissao.producao;

import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.parametro.ProdutoProducaoRepository;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Calendário de produção e avisos/urgências (perfil Chefe de Produção). */
@Tag(name = "Calendário de produção e avisos")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProducaoController {

    private final BloqueioCalendarioRepository bloqueios;
    private final AvisoRepository avisos;
    private final ProdutoProducaoRepository produtos;
    private final OrdemServicoRepository ordens;
    private final UsuarioAtualService usuarioAtual;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record BloqueioDto(LocalDate data, Long produtoId, String produto, String observacao) {
        static BloqueioDto de(BloqueioCalendario b) {
            return new BloqueioDto(b.getData(), b.getProduto().getId(), b.getProduto().getNome(), b.getObservacao());
        }
    }

    public record AvisoDto(Long id, TipoAviso tipo, String texto, boolean urgente, Long osId, String osCodigo,
                           Long produtoId, String produto, LocalDate data, String autor, boolean ativo,
                           LocalDateTime criadoEm) {
        static AvisoDto de(Aviso a) {
            return new AvisoDto(a.getId(), a.getTipo(), a.getTexto(), a.isUrgente(),
                    a.getOs() == null ? null : a.getOs().getId(), a.getOs() == null ? null : a.getOs().getCodigo(),
                    a.getProduto() == null ? null : a.getProduto().getId(), a.getProduto() == null ? null : a.getProduto().getNome(),
                    a.getData(), a.getAutor() == null ? null : a.getAutor().getNome(), a.isAtivo(), a.getCriadoEm());
        }
    }

    public record DiaCalendario(LocalDate data, List<BloqueioDto> bloqueios, List<AvisoDto> avisos, long entregas) {
    }

    @GetMapping("/calendario")
    @Transactional(readOnly = true)
    public List<DiaCalendario> calendario(@RequestParam(required = false) YearMonth mes) {
        YearMonth m = mes == null ? YearMonth.now(clock) : mes;
        LocalDate ini = m.atDay(1), fim = m.plusMonths(1).atDay(1);
        Map<LocalDate, List<BloqueioDto>> bl = bloqueios.findByDataGreaterThanEqualAndDataLessThanOrderByData(ini, fim)
                .stream().map(BloqueioDto::de).collect(Collectors.groupingBy(BloqueioDto::data));
        Map<LocalDate, List<AvisoDto>> av = avisos.findByAtivoTrueAndDataGreaterThanEqualAndDataLessThan(ini, fim)
                .stream().map(AvisoDto::de).collect(Collectors.groupingBy(AvisoDto::data));
        Map<LocalDate, Long> ent = ordens.entregasPorDia(ini, fim).stream()
                .collect(Collectors.toMap(r -> (LocalDate) r[0], r -> (Long) r[1]));
        return ini.datesUntil(fim).map(d -> new DiaCalendario(d, bl.getOrDefault(d, List.of()), av.getOrDefault(d, List.of()),
                ent.getOrDefault(d, 0L))).toList();
    }

    @GetMapping("/calendario/disponibilidade")
    @Transactional(readOnly = true)
    public DiaCalendario disponibilidade(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return new DiaCalendario(data, bloqueios.findByData(data).stream().map(BloqueioDto::de).toList(),
                avisos.findByAtivoTrueAndData(data).stream().map(AvisoDto::de).toList(), 0);
    }

    public record BloqueioRequest(List<Long> produtoIds, String observacao) {
    }

    @PutMapping("/calendario/{data}")
    @PreAuthorize("hasAnyRole('PRODUCAO','GESTOR')")
    @Transactional
    public DiaCalendario definirBloqueios(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                          @RequestBody BloqueioRequest req) {
        bloqueios.deleteByData(data);
        bloqueios.flush();
        for (Long pid : req.produtoIds() == null ? List.<Long>of() : req.produtoIds()) {
            BloqueioCalendario b = new BloqueioCalendario();
            b.setData(data);
            b.setProduto(produtos.findById(pid).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado")));
            b.setObservacao(req.observacao());
            bloqueios.save(b);
        }
        auditoria.registrar(null, usuarioAtual.atual(), "CALENDARIO", data + ": " + (req.produtoIds() == null ? 0 : req.produtoIds().size()) + " produto(s) bloqueado(s)");
        return disponibilidade(data);
    }

    // ------------------------------------------------ avisos

    @GetMapping("/avisos")
    @Transactional(readOnly = true)
    public List<AvisoDto> listarAvisos() {
        return avisos.findTop200ByOrderByAtivoDescIdDesc().stream().map(AvisoDto::de).toList();
    }

    @GetMapping("/avisos/urgentes")
    @Transactional(readOnly = true)
    public List<AvisoDto> urgentes() {
        return avisos.findByAtivoTrueAndUrgenteTrueOrderByCriadoEmDesc().stream().limit(3).map(AvisoDto::de).toList();
    }

    public record AvisoRequest(@NotNull TipoAviso tipo, @NotBlank String texto, boolean urgente, Long osId,
                               Long produtoId, LocalDate data) {
    }

    @PostMapping("/avisos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PRODUCAO','GESTOR')")
    @Transactional
    public AvisoDto criarAviso(@Valid @RequestBody AvisoRequest req) {
        boolean vinculado = req.tipo() == TipoAviso.VINCULADO;
        if (vinculado && req.osId() == null && req.produtoId() == null && req.data() == null) {
            throw new NegocioException("Aviso vinculado precisa de uma OS, produto ou data.");
        }
        var u = usuarioAtual.atual();
        Aviso a = new Aviso();
        a.setTipo(req.tipo());
        a.setTexto(req.texto().trim());
        a.setUrgente(req.urgente());
        if (vinculado) {
            a.setOs(req.osId() == null ? null : ordens.findById(req.osId()).orElseThrow(() -> new NaoEncontradoException("OS não encontrada")));
            a.setProduto(req.produtoId() == null ? null : produtos.findById(req.produtoId()).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado")));
            a.setData(req.data());
        }
        a.setAutor(u);
        a.setCriadoEm(LocalDateTime.now(clock));
        avisos.save(a);
        if (a.getOs() != null) {
            auditoria.registrar(a.getOs().getId(), u, "AVISO", a.getTexto());
        }
        return AvisoDto.de(a);
    }

    @PostMapping("/avisos/{id}/arquivamento")
    @PreAuthorize("hasAnyRole('PRODUCAO','GESTOR')")
    @Transactional
    public void arquivar(@PathVariable Long id) {
        avisos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aviso não encontrado")).setAtivo(false);
    }
}
