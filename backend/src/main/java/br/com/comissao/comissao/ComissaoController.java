package br.com.comissao.comissao;

import br.com.comissao.comissao.ComissaoConsultaService.ComissoesDoMes;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.UsuarioAtualService;
import br.com.comissao.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Tag(name = "Comissões, fechamento e metas")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
public class ComissaoController {

    private final ComissaoConsultaService consulta;
    private final ComissaoService comissaoService;
    private final RegraComissaoService regras;
    private final MetaSemanalService metas;
    private final FechamentoMensalRepository fechamentos;
    private final UsuarioRepository usuarios;
    private final UsuarioAtualService usuarioAtual;
    private final Clock clock;

    @GetMapping("/comissoes")
    public ComissoesDoMes comissoes(@RequestParam(required = false) YearMonth mes,
                                    @RequestParam(required = false) StatusComissao status,
                                    @RequestParam(required = false) Long vendedorId) {
        return consulta.doMes(mes == null ? YearMonth.now(clock) : mes, usuarioAtual.atual(), status, vendedorId);
    }

    public record PagamentoRequest(@NotEmpty List<Long> ids) {
    }

    @PostMapping("/comissoes/pagamento")
    @PreAuthorize("hasRole('GESTOR')")
    public Map<String, Integer> pagar(@RequestBody PagamentoRequest req) {
        return Map.of("pagas", comissaoService.pagar(req.ids(), usuarioAtual.atual()));
    }

    // ------------------------------------------------ fechamento mensal

    public record PreviaFechamento(String vendedor, FaixaCalculada faixa) {
    }

    public record HistoricoFechamento(String mes, String vendedor, java.math.BigDecimal totalVendido,
                                      java.math.BigDecimal faturamentoEmpresa, java.math.BigDecimal percentual,
                                      String regra, java.math.BigDecimal salarioFixo, java.time.LocalDateTime fechadoEm) {
    }

    public record PainelFechamento(String mesAtual, List<String> pendentes, List<PreviaFechamento> previa,
                                   List<HistoricoFechamento> historico) {
    }

    @GetMapping("/fechamentos")
    @PreAuthorize("hasRole('GESTOR')")
    public PainelFechamento fechamentos() {
        YearMonth atual = YearMonth.now(clock);
        return new PainelFechamento(atual.toString(),
                comissaoService.mesesPendentesDeFechamento().stream().map(YearMonth::toString).toList(),
                usuarios.findByPerfilAndAtivoTrueOrderByNome(Perfil.VENDEDOR).stream()
                        .map(v -> new PreviaFechamento(v.getNome(), regras.calcularFaixaAberta(v.getId(), atual))).toList(),
                fechamentos.historico().stream().map(f -> new HistoricoFechamento(f.getMesRef(), f.getVendedor().getNome(),
                        f.getTotalVendido(), f.getFaturamentoEmpresa(), f.getPercentual(), f.getRegra(), f.getSalarioFixo(),
                        f.getFechadoEm())).toList());
    }

    @PostMapping("/fechamentos/{mes}")
    @PreAuthorize("hasRole('GESTOR')")
    public Map<String, Integer> fechar(@PathVariable YearMonth mes) {
        return Map.of("vendedores", comissaoService.fecharMes(mes, usuarioAtual.atual()).size());
    }

    // ------------------------------------------------ metas semanais

    public record MetasVendedor(Long vendedorId, String vendedor, List<MetaSemanalService.MetaSemanal> semanas) {
    }

    @GetMapping("/metas-semanais")
    public List<MetasVendedor> metas(@RequestParam(required = false) YearMonth mes) {
        var u = usuarioAtual.atual();
        YearMonth m = mes == null ? YearMonth.now(clock) : mes;
        return usuarios.findByPerfilAndAtivoTrueOrderByNome(Perfil.VENDEDOR).stream()
                .filter(v -> !u.isVendedor() || v.getId().equals(u.getId()))
                .map(v -> new MetasVendedor(v.getId(), v.getNome(), metas.semanasDoMes(v.getId(), m))).toList();
    }

    public record PagamentoMetaRequest(Long vendedorId, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaInicio) {
    }

    @PostMapping("/metas-semanais/pagamento")
    @PreAuthorize("hasRole('GESTOR')")
    public MetaSemanalService.MetaSemanal pagarMeta(@RequestBody PagamentoMetaRequest req) {
        return metas.pagar(req.vendedorId(), req.semanaInicio(), usuarioAtual.atual());
    }
}
