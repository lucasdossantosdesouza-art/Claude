package br.com.comissao;

import br.com.comissao.comissao.*;
import br.com.comissao.os.OrdemServicoService;
import br.com.comissao.os.OsDtos.ClienteDto;
import br.com.comissao.os.OsDtos.OsRequest;
import br.com.comissao.os.OsDtos.ParcelaRequest;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.ParametrizacaoService;
import br.com.comissao.parametro.ParametrizacaoService.Faixa;
import br.com.comissao.parametro.ParametrizacaoService.TipoTabela;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parâmetros com vigência: valores do documento como padrão, alterações futuras e histórico preservado. */
@SpringBootTest
@ActiveProfiles("test")
@Import(RelogioTeste.class)
@Transactional
class VigenciaParametrosTest {

    static final YearMonth NOV = YearMonth.of(2026, 11);   // mês atual no relógio de teste
    static final YearMonth JAN = YearMonth.of(2027, 1);

    @Autowired ParametroService parametros;
    @Autowired ParametrizacaoService parametrizacao;
    @Autowired RegraComissaoService regras;
    @Autowired ComissaoService comissaoService;
    @Autowired ComissaoRepository comissoes;
    @Autowired OrdemServicoService osService;
    @Autowired UsuarioRepository usuarios;

    Usuario ana;

    @BeforeEach
    void setUp() {
        Usuario u = new Usuario();
        u.setKeycloakId(UUID.randomUUID().toString());
        u.setLogin("ana");
        u.setNome("Ana");
        u.setPerfil(Perfil.VENDEDOR);
        ana = usuarios.save(u);
    }

    Long venda(String data, String total) {
        var req = new OsRequest(null, null, LocalDate.parse(data), "10:00", null, null, null, BigDecimal.ZERO,
                new BigDecimal(total), 30, false, null,
                new ClienteDto(null, "Cliente", null, null, null, null, null, null, null, null, null, null, null),
                List.of(), List.of(new ParcelaRequest(null, "Pix", new BigDecimal(total), LocalDate.parse(data))));
        Long id = osService.salvar(null, req, ana).getId();
        var p = osService.carregar(id, ana).getParcelas().getFirst();
        osService.receberParcela(id, p.getId(), LocalDate.parse(data), new MockMultipartFile("c", "c.png", "image/png", new byte[]{1}), ana);
        return id;
    }

    static List<Faixa> faixas(String... v) {
        List<Faixa> l = new java.util.ArrayList<>();
        for (int i = 0; i < v.length; i += 2) l.add(new Faixa(new BigDecimal(v[i]), new BigDecimal(v[i + 1])));
        return l;
    }

    @Test
    void valoresDoDocumentoVemCadastradosComoPadrao() {
        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, NOV)).isEqualByComparingTo("1500");
        assertThat(parametros.valor(ChaveParametro.TETO_FATURAMENTO_EMPRESA, NOV)).isEqualByComparingTo("230000");
        assertThat(parametros.valor(ChaveParametro.PERCENTUAL_POS_TETO, NOV)).isEqualByComparingTo("1");
        assertThat(parametros.valor(ChaveParametro.FATOR_MEIA_MARGEM, NOV)).isEqualByComparingTo("0.5");
        assertThat(parametrizacao.faixasVigentes(TipoTabela.COMISSAO, NOV))
                .extracting(f -> f.valorMinimo().intValue() + "→" + f.valor().stripTrailingZeros().toPlainString())
                .containsExactly("58000→1", "70000→2", "80000→3");
        assertThat(parametrizacao.faixasVigentes(TipoTabela.META_SEMANAL, NOV))
                .extracting(f -> f.valorMinimo().intValue() + "→" + f.valor().intValue())
                .containsExactly("20133→100", "23810→150", "26690→200");
    }

    @Test
    void alteracaoAgendadaSoValeAPartirDoMesEscolhido() {
        parametrizacao.salvarGerais(JAN, Map.of(ChaveParametro.SALARIO_FIXO, new BigDecimal("1800")), null);
        parametrizacao.salvarFaixas(TipoTabela.COMISSAO, JAN, faixas("60000", "1.5", "90000", "3.5"), null);

        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, NOV)).isEqualByComparingTo("1500");
        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, YearMonth.of(2026, 12))).isEqualByComparingTo("1500");
        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, JAN)).isEqualByComparingTo("1800");
        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, YearMonth.of(2027, 6))).isEqualByComparingTo("1800");
        assertThat(parametros.faixasComissao(NOV)).hasSize(3);
        assertThat(parametros.faixasComissao(JAN)).hasSize(2);

        var linha = parametrizacao.linhaDoTempo();
        assertThat(linha.getFirst().mes()).isEqualTo("2027-01");
        assertThat(linha.getFirst().situacao()).isEqualTo("AGENDADA");
        assertThat(linha.getFirst().alteracoes()).anyMatch(a -> a.contains("1500 → 1800"));
        assertThat(linha).anyMatch(v -> v.mes().equals("2000-01") && v.situacao().equals("VIGENTE") && !v.removivel());

        parametrizacao.excluirVigencia(JAN, null);
        assertThat(parametros.valor(ChaveParametro.SALARIO_FIXO, JAN)).isEqualByComparingTo("1500");
        assertThat(parametros.faixasComissao(JAN)).hasSize(3);
    }

    @Test
    void alteracaoNoMesAbertoRecalculaComissoesEMesFechadoNaoMuda() {
        Long ago = venda("2026-08-10", "85000");
        comissaoService.fecharMes(YearMonth.of(2026, 8), null);
        Long nov = venda("2026-11-10", "85000");
        assertThat(comissoes.findByOsId(nov).getFirst().getValor()).isEqualByComparingTo("2550.00"); // 3%

        // não é possível alterar a partir de um mês já fechado
        assertThatThrownBy(() -> parametrizacao.salvarFaixas(TipoTabela.COMISSAO, YearMonth.of(2026, 8), faixas("80000", "4"), null))
                .hasMessageContaining("já foi fechado");
        assertThat(parametrizacao.primeiroMesEditavel()).isEqualTo(YearMonth.of(2026, 9));

        parametrizacao.salvarFaixas(TipoTabela.COMISSAO, NOV, faixas("58000", "1", "70000", "2", "80000", "4"), null);
        assertThat(comissoes.findByOsId(nov).getFirst().getValor()).isEqualByComparingTo("3400.00");  // recalculada com 4%
        assertThat(comissoes.findByOsId(ago).getFirst().getValor()).isEqualByComparingTo("2550.00");  // agosto travado em 3%

        parametrizacao.restaurarDocumento(NOV, null);
        assertThat(comissoes.findByOsId(nov).getFirst().getValor()).isEqualByComparingTo("2550.00");
    }

    @Test
    void validaValores() {
        assertThatThrownBy(() -> parametrizacao.salvarGerais(NOV, Map.of(ChaveParametro.PERCENTUAL_POS_TETO, new BigDecimal("120")), null))
                .hasMessageContaining("100%");
        assertThatThrownBy(() -> parametrizacao.salvarGerais(NOV, Map.of(ChaveParametro.DIA_INICIO_SEMANA, new BigDecimal("8")), null))
                .hasMessageContaining("segunda");
        assertThatThrownBy(() -> parametrizacao.salvarGerais(NOV, Map.of(ChaveParametro.FATOR_MEIA_MARGEM, new BigDecimal("2")), null))
                .hasMessageContaining("entre 0 e 1");
        assertThatThrownBy(() -> parametrizacao.salvarFaixas(TipoTabela.COMISSAO, NOV, faixas("58000", "1", "58000", "2"), null))
                .hasMessageContaining("mesmo valor");
    }
}
