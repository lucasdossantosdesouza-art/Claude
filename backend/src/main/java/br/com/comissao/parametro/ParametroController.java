package br.com.comissao.parametro;

import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.comissao.ComissaoService;
import br.com.comissao.importacao.LeitorIa;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Supplier;

/** Parametrização de valores, percentuais, prazos e listas do sistema (tela do Gestor). */
@Tag(name = "Parametrização")
@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametroController {

    private final ParametroRepository parametros;
    private final FaixaComissaoRepository faixasComissao;
    private final FaixaMetaSemanalRepository faixasMeta;
    private final FormaPagamentoRepository formas;
    private final MotivoEstornoRepository motivos;
    private final ProdutoProducaoRepository produtos;
    private final ComissaoService comissaoService;
    private final AuditoriaService auditoria;
    private final UsuarioAtualService usuarioAtual;
    private final LeitorIa leitorIa;
    private final Clock clock;

    public record ParametroDto(ChaveParametro chave, BigDecimal valor, String descricao, String tipo) {
    }

    public record FaixaDto(BigDecimal valorMinimo, BigDecimal valor) {
    }

    public record ItemDto(Long id, String nome, boolean ativo) {
    }

    public record Configuracao(List<ParametroDto> gerais, List<FaixaDto> faixasComissao, List<FaixaDto> faixasMetaSemanal,
                               List<ItemDto> formasPagamento, List<ItemDto> motivosEstorno, List<ItemDto> produtosProducao,
                               boolean iaConfigurada) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Configuracao configuracao() {
        return new Configuracao(
                parametros.findAllByOrderByOrdem().stream().map(p -> new ParametroDto(p.getChave(), p.getValor(), p.getDescricao(), p.getTipo())).toList(),
                faixasComissao.findAllByOrderByValorMinimo().stream().map(f -> new FaixaDto(f.getValorMinimo(), f.getPercentual())).toList(),
                faixasMeta.findAllByOrderByValorMinimo().stream().map(f -> new FaixaDto(f.getValorMinimo(), f.getBonus())).toList(),
                itens(formas.findAllByOrderByNome()), itens(motivos.findAllByOrderByNome()), itens(produtos.findAllByOrderByNome()),
                leitorIa.disponivel());
    }

    private static List<ItemDto> itens(List<? extends ItemCadastro> l) {
        return l.stream().map(i -> new ItemDto(i.getId(), i.getNome(), i.isAtivo())).toList();
    }

    @PutMapping("/gerais")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao salvarGerais(@RequestBody Map<ChaveParametro, BigDecimal> valores) {
        List<String> alterados = new ArrayList<>();
        valores.forEach((chave, valor) -> {
            Parametro p = parametros.findById(chave).orElseThrow(() -> new NaoEncontradoException("Parâmetro " + chave));
            if (valor == null || valor.signum() < 0) {
                throw new NegocioException("Valor inválido para " + p.getDescricao());
            }
            if (p.getValor().compareTo(valor) != 0) {
                alterados.add(chave + ": " + p.getValor().stripTrailingZeros().toPlainString() + " → " + valor.stripTrailingZeros().toPlainString());
                p.setValor(valor);
            }
        });
        if (!alterados.isEmpty()) {
            parametros.flush();
            auditoria.registrar(null, usuarioAtual.atual(), "PARAMETROS", String.join("; ", alterados));
            comissaoService.sincronizarMes(YearMonth.now(clock));
        }
        return configuracao();
    }

    @PutMapping("/faixas-comissao")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao salvarFaixasComissao(@RequestBody List<FaixaDto> faixas) {
        validar(faixas);
        faixasComissao.deleteAllInBatch();
        faixas.stream().sorted(Comparator.comparing(FaixaDto::valorMinimo))
                .forEach(f -> faixasComissao.save(new FaixaComissao(f.valorMinimo(), f.valor())));
        faixasComissao.flush();
        auditoria.registrar(null, usuarioAtual.atual(), "PARAMETROS", "Faixas de comissão: " + faixas);
        comissaoService.sincronizarMes(YearMonth.now(clock));
        return configuracao();
    }

    @PutMapping("/faixas-meta-semanal")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao salvarFaixasMeta(@RequestBody List<FaixaDto> faixas) {
        validar(faixas);
        faixasMeta.deleteAllInBatch();
        faixas.stream().sorted(Comparator.comparing(FaixaDto::valorMinimo))
                .forEach(f -> faixasMeta.save(new FaixaMetaSemanal(f.valorMinimo(), f.valor())));
        auditoria.registrar(null, usuarioAtual.atual(), "PARAMETROS", "Patamares da meta semanal: " + faixas);
        return configuracao();
    }

    private static void validar(List<FaixaDto> faixas) {
        if (faixas == null || faixas.isEmpty()) {
            throw new NegocioException("Informe ao menos uma faixa.");
        }
        for (FaixaDto f : faixas) {
            if (f.valorMinimo() == null || f.valorMinimo().signum() <= 0 || f.valor() == null || f.valor().signum() < 0) {
                throw new NegocioException("Faixa inválida: valores devem ser positivos.");
            }
        }
        if (faixas.stream().map(f -> f.valorMinimo().stripTrailingZeros()).distinct().count() != faixas.size()) {
            throw new NegocioException("Há faixas com o mesmo valor mínimo.");
        }
    }

    public record NovoItem(@NotBlank String nome) {
    }

    public record AtivoRequest(boolean ativo) {
    }

    @PostMapping("/{lista}")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao adicionar(@PathVariable String lista, @RequestBody NovoItem req) {
        switch (lista) {
            case "formas-pagamento" -> novo(formas, FormaPagamento::new, req.nome());
            case "motivos-estorno" -> novo(motivos, MotivoEstorno::new, req.nome());
            case "produtos-producao" -> novo(produtos, ProdutoProducao::new, req.nome());
            default -> throw new NaoEncontradoException("Lista inexistente");
        }
        return configuracao();
    }

    @PatchMapping("/{lista}/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao ativar(@PathVariable String lista, @PathVariable Long id, @RequestBody AtivoRequest req) {
        JpaRepository<? extends ItemCadastro, Long> repo = switch (lista) {
            case "formas-pagamento" -> formas;
            case "motivos-estorno" -> motivos;
            case "produtos-producao" -> produtos;
            default -> throw new NaoEncontradoException("Lista inexistente");
        };
        repo.findById(id).orElseThrow(() -> new NaoEncontradoException("Item não encontrado")).setAtivo(req.ativo());
        return configuracao();
    }

    private static <T extends ItemCadastro> void novo(JpaRepository<T, Long> repo, Supplier<T> fabrica, String nome) {
        if (repo.findAll().stream().anyMatch(i -> i.getNome().equalsIgnoreCase(nome.trim()))) {
            throw new NegocioException("Item já existe.");
        }
        T item = fabrica.get();
        item.setNome(nome.trim());
        repo.save(item);
    }
}
