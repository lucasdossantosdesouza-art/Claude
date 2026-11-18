package br.com.comissao;

import br.com.comissao.importacao.ExtratorTexto;
import br.com.comissao.importacao.LeitorLayoutSafi;
import br.com.comissao.importacao.OsExtraida;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura da OS no layout do SAFI a partir do PDF. */
@SpringBootTest
@ActiveProfiles("test")
@Import(RelogioTeste.class)
class LeitorOsTest {

    @Autowired ExtratorTexto extrator;
    @Autowired LeitorLayoutSafi leitor;

    @Test
    void lePdfDoSafi() throws Exception {
        byte[] pdf = getClass().getResourceAsStream("/os_exemplo_safi.pdf").readAllBytes();
        OsExtraida o = leitor.ler(extrator.textoDoPdf(pdf));
        assertThat(o.codigo()).isEqualTo("48213");
        assertThat(o.dataEmissao()).isEqualTo("2026-11-05");
        assertThat(o.previsaoEntrega()).isEqualTo("2026-11-20");
        assertThat(o.cliente().nome()).isEqualTo("Pesca & Cia Esportes");
        assertThat(o.cliente().cnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(o.cliente().cidade()).isEqualTo("Santos");
        assertThat(o.cliente().uf()).isEqualTo("SP");
        assertThat(o.itens()).hasSize(4);
        assertThat(o.itens().get(3).tipo()).isEqualTo("SERVICO");
        assertThat(o.total()).isEqualByComparingTo("6000");
        assertThat(o.parcelas()).extracting(OsExtraida.ParcelaExtraida::forma).containsExactly("Pix", "Boleto", "Boleto");
        assertThat(o.parcelas()).extracting(OsExtraida.ParcelaExtraida::valor)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("2000"), new BigDecimal("2000"), new BigDecimal("2000"));
        assertThat(o.prazoPagamentoDias()).isEqualTo(30);
    }

    @Test
    void leFotoComOcrQuandoTesseractDisponivel() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(extrator.ocrDisponivel(), "Tesseract não instalado");
        byte[] png = getClass().getResourceAsStream("/os_exemplo_safi.png").readAllBytes();
        OsExtraida o = leitor.ler(extrator.ocrDaImagem(png, ".png"));
        assertThat(o.cliente().nome()).isEqualTo("Pesca & Cia Esportes");
        assertThat(o.total()).isEqualByComparingTo("6000");
        assertThat(o.itens()).hasSize(4);
    }
}
