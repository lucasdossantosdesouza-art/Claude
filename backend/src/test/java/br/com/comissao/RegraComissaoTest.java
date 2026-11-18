package br.com.comissao;

import br.com.comissao.comissao.*;
import br.com.comissao.os.OrdemServicoService;
import br.com.comissao.os.OsDtos.*;
import br.com.comissao.os.SituacaoOs;
import br.com.comissao.parametro.FaixaComissaoRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de comissão do documento de requisitos (itens 3.1 e 3.2). */
@SpringBootTest
@ActiveProfiles("test")
@Import(RelogioTeste.class)
@Transactional
class RegraComissaoTest {

    @Autowired OrdemServicoService osService;
    @Autowired ComissaoService comissaoService;
    @Autowired RegraComissaoService regras;
    @Autowired MetaSemanalService metas;
    @Autowired ComissaoRepository comissoes;
    @Autowired UsuarioRepository usuarios;
    @Autowired FaixaComissaoRepository faixas;

    Usuario ana;
    Usuario bia;

    @BeforeEach
    void setUp() {
        ana = vendedor("ana");
        bia = vendedor("bia");
    }

    Usuario vendedor(String login) {
        Usuario u = new Usuario();
        u.setKeycloakId(UUID.randomUUID().toString());
        u.setLogin(login);
        u.setNome(login.toUpperCase());
        u.setPerfil(Perfil.VENDEDOR);
        return usuarios.save(u);
    }

    Long venda(Usuario v, String data, String total, boolean meiaMargem, Object... parcelas) {
        List<ParcelaRequest> ps = new java.util.ArrayList<>();
        for (int i = 0; i < parcelas.length; i += 2) {
            ps.add(new ParcelaRequest(null, "Boleto", new BigDecimal((String) parcelas[i]), LocalDate.parse((String) parcelas[i + 1])));
        }
        var req = new OsRequest(null, null, LocalDate.parse(data), "10:00", null, null, null, BigDecimal.ZERO,
                new BigDecimal(total), 30, meiaMargem, null,
                new ClienteDto(null, "Cliente X", null, null, null, null, null, null, null, null, null, null, null),
                List.of(), ps);
        return osService.salvar(null, req, v).getId();
    }

    void receber(Long osId, int numero, String data, Usuario v) {
        var os = osService.carregar(osId, v);
        var p = os.getParcelas().stream().filter(x -> x.getNumero() == numero).findFirst().orElseThrow();
        osService.receberParcela(osId, p.getId(), LocalDate.parse(data),
                new MockMultipartFile("comprovante", "pix.png", "image/png", new byte[]{1, 2, 3}), v);
    }

    List<Comissao> comissoesDa(Long osId) {
        return comissoes.findByOsId(osId).stream().sorted(Comparator.comparing(c -> c.getParcela().getNumero())).toList();
    }

    @Test
    void faixasMensaisPorTotalVendido() {
        venda(ana, "2026-08-10", "57999", false, "57999", "2026-08-20");
        assertThat(regras.calcularFaixaAberta(ana.getId(), YearMonth.of(2026, 8)).percentual()).isEqualByComparingTo("0");
        venda(ana, "2026-08-11", "1", false, "1", "2026-08-20");
        assertThat(regras.calcularFaixaAberta(ana.getId(), YearMonth.of(2026, 8)).percentual()).isEqualByComparingTo("1");
        venda(ana, "2026-08-12", "12000", false, "12000", "2026-08-20");
        assertThat(regras.calcularFaixaAberta(ana.getId(), YearMonth.of(2026, 8)).percentual()).isEqualByComparingTo("2");
        venda(ana, "2026-08-13", "10000", false, "10000", "2026-08-20");
        var f = regras.calcularFaixaAberta(ana.getId(), YearMonth.of(2026, 8));
        assertThat(f.percentual()).isEqualByComparingTo("3");
        assertThat(f.proximaFaixaValor()).isNull();
    }

    @Test
    void tetoDaEmpresaGarante1PorCentoAbaixoDaMeta() {
        venda(ana, "2026-08-05", "50000", false, "50000", "2026-08-30");
        venda(bia, "2026-08-06", "185000", false, "185000", "2026-08-30"); // empresa = 235 mil > 230 mil
        var f = regras.calcularFaixaAberta(ana.getId(), YearMonth.of(2026, 8));
        assertThat(f.percentual()).isEqualByComparingTo("1");
        assertThat(f.regra()).contains("teto");
        assertThat(regras.calcularFaixaAberta(bia.getId(), YearMonth.of(2026, 8)).percentual()).isEqualByComparingTo("3");
    }

    @Test
    void exemploDoDocumentoPagaSoSobreRecebidoComFaixaTravada() {
        // vendeu 50 mil (1% pela regra do teto); recebeu 20 mil até o fechamento -> 1% sobre 20 mil
        Long os = venda(ana, "2026-08-05", "50000", false, "20000", "2026-08-20", "30000", "2026-09-20");
        venda(bia, "2026-08-06", "190000", false, "190000", "2026-08-30");
        receber(os, 1, "2026-08-20", ana);
        assertThat(comissoesDa(os)).extracting(Comissao::getStatus)
                .containsExactly(StatusComissao.PROVISORIA, StatusComissao.PENDENTE_RECEBIMENTO);

        comissaoService.fecharMes(YearMonth.of(2026, 8), null);
        var cm = comissoesDa(os);
        assertThat(cm.get(0).getStatus()).isEqualTo(StatusComissao.AUTORIZADA);
        assertThat(cm.get(0).getValor()).isEqualByComparingTo("200.00");
        assertThat(cm.get(1).getStatus()).isEqualTo(StatusComissao.PENDENTE_RECEBIMENTO);

        // alterar as faixas depois do fechamento não muda o mês travado
        faixas.findAll().forEach(f -> f.setPercentual(f.getPercentual().multiply(BigDecimal.TEN)));
        receber(os, 2, "2026-09-21", ana);
        cm = comissoesDa(os);
        assertThat(cm.get(1).getStatus()).isEqualTo(StatusComissao.AUTORIZADA);
        assertThat(cm.get(1).getValor()).isEqualByComparingTo("300.00");
        assertThat(osService.carregar(os, ana).getSituacao()).isEqualTo(SituacaoOs.FECHADA);
    }

    @Test
    void meiaMargemUsaMetadeDaFaixa() {
        Long os = venda(ana, "2026-07-01", "85000", true, "85000", "2026-07-10");
        receber(os, 1, "2026-07-10", ana);
        comissaoService.fecharMes(YearMonth.of(2026, 7), null);
        Comissao c = comissoesDa(os).getFirst();
        assertThat(c.getPercentual()).isEqualByComparingTo("1.5");
        assertThat(c.getValor()).isEqualByComparingTo("1275.00");
    }

    @Test
    void comissaoPagaNaoERecalculadaECancelamentoCancelaPendentes() {
        Long os = venda(ana, "2026-06-01", "60000", false, "30000", "2026-06-10", "30000", "2026-07-10");
        receber(os, 1, "2026-06-10", ana);
        comissaoService.fecharMes(YearMonth.of(2026, 6), null);
        comissaoService.pagar(List.of(comissoesDa(os).getFirst().getId()), null);
        osService.cancelar(os, new CancelamentoRequest("Cliente desistiu do restante", false, null, null), null, ana);
        assertThat(comissoesDa(os)).extracting(Comissao::getStatus)
                .containsExactly(StatusComissao.PAGA, StatusComissao.CANCELADA);
    }

    @Test
    void naoFechaMesCorrenteNemPagaProvisoria() {
        Long os = venda(ana, "2026-11-02", "1000", false, "1000", "2026-11-05");
        receber(os, 1, "2026-11-05", ana);
        assertThatThrownBy(() -> comissaoService.fecharMes(YearMonth.of(2026, 11), null)).hasMessageContaining("encerrados");
        assertThatThrownBy(() -> comissaoService.pagar(List.of(comissoesDa(os).getFirst().getId()), null))
                .hasMessageContaining("provisórias");
    }

    @Test
    void metaSemanalPorPatamar() {
        venda(ana, "2026-11-02", "15000", false, "15000", "2026-11-30"); // segunda-feira
        venda(ana, "2026-11-05", "9000", false, "9000", "2026-11-30");
        var m = metas.semana(ana.getId(), LocalDate.of(2026, 11, 2));
        assertThat(m.total()).isEqualByComparingTo("24000");
        assertThat(m.bonus()).isEqualByComparingTo("150");
        assertThat(m.encerrada()).isTrue();
        assertThat(metas.semana(ana.getId(), LocalDate.of(2026, 11, 9)).bonus()).isEqualByComparingTo("0");
        assertThat(metas.pagar(ana.getId(), LocalDate.of(2026, 11, 2), null).paga()).isTrue();
    }

    @Test
    void edicaoNaoRemoveParcelaRecebida() {
        Long os = venda(ana, "2026-11-01", "1000", false, "500", "2026-11-05", "500", "2026-12-05");
        receber(os, 1, "2026-11-05", ana);
        var atual = osService.carregar(os, ana);
        Long recebidaId = atual.getParcelas().getFirst().getId();
        var req = new OsRequest(null, null, LocalDate.parse("2026-11-01"), "10:00", null, null, null, BigDecimal.ZERO,
                new BigDecimal("1000"), 30, false, null,
                new ClienteDto(atual.getCliente().getId(), "Cliente X", null, null, null, null, null, null, null, null, null, null, null),
                List.of(), List.of(new ParcelaRequest(recebidaId, "Pix", new BigDecimal("999"), LocalDate.parse("2026-01-01")),
                new ParcelaRequest(null, "Pix", new BigDecimal("250"), LocalDate.parse("2026-12-05")),
                new ParcelaRequest(null, "Pix", new BigDecimal("250"), LocalDate.parse("2027-01-05"))));
        var editada = osService.salvar(os, req, ana);
        assertThat(editada.getParcelas()).hasSize(3);
        assertThat(editada.getParcelas().getFirst().getValor()).isEqualByComparingTo("500"); // recebida inalterada
    }
}
