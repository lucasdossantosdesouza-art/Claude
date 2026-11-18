package br.com.comissao.financeiro;

import br.com.comissao.financeiro.FinanceiroService.PainelFinanceiro;
import br.com.comissao.financeiro.FinanceiroService.PainelVendedor;
import br.com.comissao.financeiro.FinanceiroService.ParcelaReceber;
import br.com.comissao.os.OrdemServicoService;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioAtualService;
import br.com.comissao.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Financeiro e painéis")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService service;
    private final OrdemServicoService osService;
    private final UsuarioAtualService usuarioAtual;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    @GetMapping("/financeiro/painel")
    @PreAuthorize("hasRole('GESTOR')")
    public PainelFinanceiro painel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.painel(data == null ? LocalDate.now(clock) : data, osService);
    }

    @GetMapping("/financeiro/contas-receber")
    @PreAuthorize("hasAnyRole('GESTOR','VENDEDOR')")
    public List<ParcelaReceber> contasAReceber(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                               @RequestParam(required = false) String cliente,
                                               @RequestParam(required = false) Long vendedorId) {
        Usuario u = usuarioAtual.atual();
        return service.contasAReceber(inicio, fim, cliente, u.isVendedor() ? u.getId() : vendedorId);
    }

    /** Painel do vendedor; o gestor pode consultar o painel de qualquer vendedor. */
    @GetMapping("/painel/vendedor")
    @PreAuthorize("hasAnyRole('GESTOR','VENDEDOR')")
    public PainelVendedor painelVendedor(@RequestParam(required = false) Long vendedorId) {
        Usuario u = usuarioAtual.atual();
        Usuario alvo = u.isVendedor() || vendedorId == null ? u
                : usuarios.findById(vendedorId).orElseThrow(() -> new NaoEncontradoException("Vendedor não encontrado"));
        return service.painelVendedor(alvo);
    }
}
