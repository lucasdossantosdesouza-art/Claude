package br.com.comissao.os;

import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.auditoria.AuditoriaService.LogDto;
import br.com.comissao.comissao.ComissaoRepository;
import br.com.comissao.comissao.StatusComissao;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Estornos (com indicador de motivos), comprovantes anexados e log de auditoria. */
@Tag(name = "Estornos, comprovantes e auditoria")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaOsController {

    private final OrdemServicoRepository ordens;
    private final ParcelaRepository parcelas;
    private final ComissaoRepository comissoes;
    private final AuditoriaService auditoria;
    private final UsuarioAtualService usuarioAtual;

    public record EstornoDto(Long osId, String osCodigo, String cliente, String vendedor, LocalDate cancelData,
                             String justificativa, boolean estorno, String motivo, BigDecimal valorEstornado,
                             String comprovante) {
    }

    public record IndicadorMotivo(String motivo, long quantidade, BigDecimal valor) {
    }

    public record PainelEstornos(List<EstornoDto> lista, List<IndicadorMotivo> indicador) {
    }

    @GetMapping("/estornos")
    @PreAuthorize("hasAnyRole('GESTOR','VENDEDOR')")
    public PainelEstornos estornos(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Long motivoId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        Usuario u = usuarioAtual.atual();
        String termo = q == null ? "" : q.toLowerCase(Locale.ROOT);
        List<EstornoDto> lista = ordens.findAll().stream()
                .filter(OrdemServico::isCancelada)
                .filter(o -> !u.isVendedor() || o.getVendedor().getId().equals(u.getId()))
                .filter(o -> motivoId == null || (o.getEstornoMotivo() != null && o.getEstornoMotivo().getId().equals(motivoId)))
                .filter(o -> inicio == null || !o.getCancelData().isBefore(inicio))
                .filter(o -> fim == null || !o.getCancelData().isAfter(fim))
                .filter(o -> termo.isEmpty() || o.getCliente().getNome().toLowerCase(Locale.ROOT).contains(termo)
                        || Objects.toString(o.getCancelMotivo(), "").toLowerCase(Locale.ROOT).contains(termo)
                        || Objects.toString(o.getCodigo(), "").contains(termo))
                .sorted(Comparator.comparing(OrdemServico::getCancelData).reversed())
                .map(o -> new EstornoDto(o.getId(), o.getCodigo(), o.getCliente().getNome(), o.getVendedor().getNome(),
                        o.getCancelData(), o.getCancelMotivo(), o.isEstorno(),
                        o.getEstornoMotivo() == null ? null : o.getEstornoMotivo().getNome(), o.getEstornoValor(),
                        o.getEstornoComprovante()))
                .toList();
        Map<String, IndicadorMotivo> ind = new LinkedHashMap<>();
        for (EstornoDto e : lista) {
            String chave = e.motivo() == null ? "Cancelamento sem estorno" : e.motivo();
            IndicadorMotivo atual = ind.getOrDefault(chave, new IndicadorMotivo(chave, 0, BigDecimal.ZERO));
            ind.put(chave, new IndicadorMotivo(chave, atual.quantidade() + 1,
                    atual.valor().add(e.valorEstornado() == null ? BigDecimal.ZERO : e.valorEstornado())));
        }
        return new PainelEstornos(lista, ind.values().stream()
                .sorted(Comparator.comparingLong(IndicadorMotivo::quantidade).reversed()).toList());
    }

    public record ComprovanteDto(String tipo, Long osId, String osCodigo, String cliente, String vendedor, Integer parcela,
                                 String forma, LocalDate data, BigDecimal valor, BigDecimal comissao,
                                 StatusComissao statusComissao, String arquivo) {
    }

    @GetMapping("/comprovantes")
    @PreAuthorize("hasRole('GESTOR')")
    public List<ComprovanteDto> comprovantes(@RequestParam(defaultValue = "") String q) {
        List<ComprovanteDto> l = new ArrayList<>();
        for (Parcela p : parcelas.comComprovante(q)) {
            var c = comissoes.findByParcelaId(p.getId()).orElse(null);
            l.add(new ComprovanteDto("RECEBIMENTO", p.getOs().getId(), p.getOs().getCodigo(), p.getOs().getCliente().getNome(),
                    p.getOs().getVendedor().getNome(), p.getNumero(), p.getForma(), p.getDataRecebimento(), p.getValor(),
                    c == null ? null : c.getValor(), c == null ? null : c.getStatus(), p.getComprovante()));
        }
        for (OrdemServico o : ordens.comComprovanteEstorno(q)) {
            l.add(new ComprovanteDto("ESTORNO", o.getId(), o.getCodigo(), o.getCliente().getNome(), o.getVendedor().getNome(),
                    null, null, o.getCancelData(), o.getEstornoValor(), null, null, o.getEstornoComprovante()));
        }
        for (OrdemServico o : ordens.comDocumentoOrigem(q, PageRequest.of(0, 100))) {
            l.add(new ComprovanteDto("DOCUMENTO_OS", o.getId(), o.getCodigo(), o.getCliente().getNome(), o.getVendedor().getNome(),
                    null, null, o.getDataEmissao(), o.getTotal(), null, null, o.getDocumentoOrigem()));
        }
        return l;
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasRole('GESTOR')")
    public List<LogDto> auditoria(@RequestParam(defaultValue = "") String q) {
        return auditoria.buscar(q);
    }
}
