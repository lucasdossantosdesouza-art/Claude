package br.com.comissao.parametro;

import br.com.comissao.importacao.LeitorIa;
import br.com.comissao.parametro.ParametrizacaoService.AlteracaoVigencia;
import br.com.comissao.parametro.ParametrizacaoService.Faixa;
import br.com.comissao.parametro.ParametrizacaoService.TipoTabela;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

/** Parametrização de valores, percentuais, prazos e listas do sistema, com vigência (tela do Gestor). */
@Tag(name = "Parametrização")
@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametroController {

    private final ParametroRepository catalogo;
    private final ParametroVigenciaRepository vigencias;
    private final ParametroService parametros;
    private final ParametrizacaoService parametrizacao;
    private final FormaPagamentoRepository formas;
    private final MotivoEstornoRepository motivos;
    private final ProdutoProducaoRepository produtos;
    private final TipoOcorrenciaRepository tiposOcorrencia;
    private final UsuarioAtualService usuarioAtual;
    private final LeitorIa leitorIa;
    private final Clock clock;

    public record ParametroDto(ChaveParametro chave, BigDecimal valor, String vigenteDesde, String descricao, String tipo,
                               String grupo, String origem, BigDecimal valorDocumento) {
    }

    public record TabelaFaixas(String vigenteDesde, List<Faixa> faixas, List<Faixa> documento) {
    }

    public record ItemDto(Long id, String nome, boolean ativo) {
    }

    public record Configuracao(String mes, String mesAtual, String primeiroMesEditavel, List<ParametroDto> gerais,
                               TabelaFaixas faixasComissao, TabelaFaixas faixasMetaSemanal,
                               List<ItemDto> formasPagamento, List<ItemDto> motivosEstorno, List<ItemDto> produtosProducao,
                               List<ItemDto> tiposOcorrencia, boolean iaConfigurada) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Parametrização vigente no mês informado (padrão: mês atual)")
    public Configuracao configuracao(@RequestParam(required = false) YearMonth mes) {
        YearMonth m = mes == null ? YearMonth.now(clock) : mes;
        List<ParametroDto> gerais = catalogo.findAllByOrderByOrdem().stream().map(p -> {
            var v = vigencias.findFirstByChaveAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(p.getChave(), m.toString());
            return new ParametroDto(p.getChave(), v.map(ParametroVigencia::getValor).orElse(BigDecimal.ZERO),
                    v.map(ParametroVigencia::getVigenteDesde).orElse(null), p.getDescricao(), p.getTipo(), p.getGrupo(),
                    p.getOrigem(), p.getValorDocumento());
        }).toList();
        return new Configuracao(m.toString(), YearMonth.now(clock).toString(), parametrizacao.primeiroMesEditavel().toString(), gerais,
                tabela(TipoTabela.COMISSAO, m, ValoresDocumento.FAIXAS_COMISSAO),
                tabela(TipoTabela.META_SEMANAL, m, ValoresDocumento.FAIXAS_META_SEMANAL),
                itens(formas.findAllByOrderByNome()), itens(motivos.findAllByOrderByNome()),
                itens(produtos.findAllByOrderByNome()), itens(tiposOcorrencia.findAllByOrderByNome()),
                leitorIa.disponivel());
    }

    private TabelaFaixas tabela(TipoTabela tipo, YearMonth m, List<ValoresDocumento.Faixa> doc) {
        String vig = tipo == TipoTabela.COMISSAO
                ? parametros.faixasComissao(m).stream().findFirst().map(FaixaComissao::getVigenteDesde).orElse(null)
                : parametros.faixasMetaSemanal(m).stream().findFirst().map(FaixaMetaSemanal::getVigenteDesde).orElse(null);
        return new TabelaFaixas(vig, parametrizacao.faixasVigentes(tipo, m),
                doc.stream().map(f -> new Faixa(f.valorMinimo(), f.valor())).toList());
    }

    private static List<ItemDto> itens(List<? extends ItemCadastro> l) {
        return l.stream().map(i -> new ItemDto(i.getId(), i.getNome(), i.isAtivo())).toList();
    }

    public record AlteracaoGerais(@NotNull YearMonth vigenteDesde, @NotNull Map<ChaveParametro, BigDecimal> valores) {
    }

    public record AlteracaoFaixas(@NotNull YearMonth vigenteDesde, @NotNull List<Faixa> faixas) {
    }

    public record VigenciaRequest(@NotNull YearMonth vigenteDesde) {
    }

    public record Resultado(List<String> alteracoes, Configuracao configuracao) {
    }

    @PutMapping("/gerais")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Altera parâmetros gerais a partir do mês informado (meses anteriores não mudam)")
    public Resultado salvarGerais(@RequestBody AlteracaoGerais req) {
        var alt = parametrizacao.salvarGerais(req.vigenteDesde(), req.valores(), usuarioAtual.atual());
        return new Resultado(alt, configuracao(req.vigenteDesde()));
    }

    @PutMapping("/faixas-comissao")
    @PreAuthorize("hasRole('GESTOR')")
    public Resultado salvarFaixasComissao(@RequestBody AlteracaoFaixas req) {
        boolean mudou = parametrizacao.salvarFaixas(TipoTabela.COMISSAO, req.vigenteDesde(), req.faixas(), usuarioAtual.atual());
        return new Resultado(mudou ? List.of("Faixas de comissão alteradas") : List.of(), configuracao(req.vigenteDesde()));
    }

    @PutMapping("/faixas-meta-semanal")
    @PreAuthorize("hasRole('GESTOR')")
    public Resultado salvarFaixasMeta(@RequestBody AlteracaoFaixas req) {
        boolean mudou = parametrizacao.salvarFaixas(TipoTabela.META_SEMANAL, req.vigenteDesde(), req.faixas(), usuarioAtual.atual());
        return new Resultado(mudou ? List.of("Patamares da meta semanal alterados") : List.of(), configuracao(req.vigenteDesde()));
    }

    @PostMapping("/restauracao-documento")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Volta aos valores do documento de requisitos a partir do mês informado")
    public Resultado restaurarDocumento(@RequestBody VigenciaRequest req) {
        var alt = parametrizacao.restaurarDocumento(req.vigenteDesde(), usuarioAtual.atual());
        return new Resultado(alt, configuracao(req.vigenteDesde()));
    }

    @GetMapping("/vigencias")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Linha do tempo das alterações: histórico, vigente e agendadas")
    public List<AlteracaoVigencia> vigencias() {
        return parametrizacao.linhaDoTempo();
    }

    @DeleteMapping("/vigencias/{mes}")
    @PreAuthorize("hasRole('GESTOR')")
    public List<AlteracaoVigencia> excluirVigencia(@PathVariable YearMonth mes) {
        parametrizacao.excluirVigencia(mes, usuarioAtual.atual());
        return parametrizacao.linhaDoTempo();
    }

    // ------------------------------------------------ listas configuráveis

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
            case "tipos-ocorrencia" -> novo(tiposOcorrencia, TipoOcorrencia::new, req.nome());
            default -> throw new NaoEncontradoException("Lista inexistente");
        }
        return configuracao(null);
    }

    @PatchMapping("/{lista}/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    @Transactional
    public Configuracao ativar(@PathVariable String lista, @PathVariable Long id, @RequestBody AtivoRequest req) {
        JpaRepository<? extends ItemCadastro, Long> repo = switch (lista) {
            case "formas-pagamento" -> formas;
            case "motivos-estorno" -> motivos;
            case "produtos-producao" -> produtos;
            case "tipos-ocorrencia" -> tiposOcorrencia;
            default -> throw new NaoEncontradoException("Lista inexistente");
        };
        repo.findById(id).orElseThrow(() -> new NaoEncontradoException("Item não encontrado")).setAtivo(req.ativo());
        return configuracao(null);
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
