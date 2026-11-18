package br.com.comissao.importacao;

import br.com.comissao.arquivo.ArmazenamentoService;
import br.com.comissao.cliente.Cliente;
import br.com.comissao.cliente.ClienteRepository;
import br.com.comissao.importacao.OsExtraida.ItemExtraido;
import br.com.comissao.importacao.OsExtraida.ParcelaExtraida;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.producao.BloqueioCalendarioRepository;
import br.com.comissao.shared.NegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static br.com.comissao.shared.Valores.formatarMoeda;
import static br.com.comissao.shared.Valores.nz;

/**
 * Leitura inteligente do documento da OS em camadas:
 * 1) IA (Claude) quando configurada — qualquer layout, PDF ou foto;
 * 2) leitor do layout fixo SAFI sobre o texto do PDF ou o OCR (Tesseract) da foto / PDF digitalizado.
 * O resultado volta para a tela para o vendedor conferir antes de salvar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoService {

    private final LeitorIa leitorIa;
    private final LeitorLayoutSafi leitorSafi;
    private final ExtratorTexto extrator;
    private final ParametroService parametros;
    private final ArmazenamentoService armazenamento;
    private final ClienteRepository clientes;
    private final BloqueioCalendarioRepository bloqueios;
    private final Clock clock;

    public enum Metodo { IA, PDF_TEXTO, PDF_OCR, OCR }

    public record ResultadoImportacao(Metodo metodo, double confianca, List<String> avisos, OsExtraida dados,
                                      String documentoOrigem, Long clienteExistenteId, List<String> bloqueiosEntrega,
                                      String textoExtraido) {
    }

    @Transactional(readOnly = true)
    public ResultadoImportacao importar(byte[] conteudo, String nomeArquivo) {
        String mime = detectarMime(conteudo, nomeArquivo);
        List<String> avisos = new ArrayList<>();
        OsExtraida dados = null;
        Metodo metodo = null;
        String texto = "";

        if (leitorIa.disponivel() && parametros.ligado(ChaveParametro.LEITURA_IA_HABILITADA)) {
            try {
                Optional<OsExtraida> ia = leitorIa.ler(conteudo, mime);
                if (ia.isPresent()) {
                    dados = ia.get();
                    metodo = Metodo.IA;
                } else {
                    avisos.add("A IA não conseguiu ler o documento; usado o leitor de layout SAFI.");
                }
            } catch (Exception e) {
                log.warn("Falha na leitura por IA", e);
                avisos.add("Leitura por IA indisponível no momento; usado o leitor de layout SAFI.");
            }
        }
        if (dados == null) {
            try {
                if ("application/pdf".equals(mime)) {
                    texto = extrator.textoDoPdf(conteudo);
                    metodo = Metodo.PDF_TEXTO;
                    if (texto.strip().length() < 30) {
                        texto = extrator.ocrDoPdf(conteudo);
                        metodo = Metodo.PDF_OCR;
                    }
                } else {
                    texto = extrator.ocrDaImagem(conteudo, "." + mime.substring(mime.indexOf('/') + 1));
                    metodo = Metodo.OCR;
                }
            } catch (IOException e) {
                throw new NegocioException("Não foi possível ler o documento: " + e.getMessage());
            }
            dados = leitorSafi.ler(texto);
        }
        dados = completar(dados);

        BigDecimal somaParcelas = dados.parcelas().stream().map(p -> nz(p.valor())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dados.total() != null && !dados.parcelas().isEmpty()
                && somaParcelas.subtract(dados.total()).abs().compareTo(new BigDecimal("0.05")) > 0) {
            avisos.add("Soma das parcelas (" + formatarMoeda(somaParcelas) + ") difere do total do pedido ("
                    + formatarMoeda(dados.total()) + "). Confira antes de salvar.");
        }
        if (dados.cliente() == null || dados.cliente().nome() == null) {
            avisos.add("Nome do cliente não identificado.");
        }
        if (dados.itens().isEmpty()) {
            avisos.add("Nenhum item identificado na tabela de produtos.");
        }
        long campos = Stream.of(dados.codigo(), dados.dataEmissao(), dados.cliente() == null ? null : dados.cliente().nome(),
                dados.cliente() == null ? null : dados.cliente().cnpj(), dados.total()).filter(java.util.Objects::nonNull).count()
                + Math.min(dados.itens().size(), 3) + Math.min(dados.parcelas().size(), 2);

        String documento = armazenamento.salvar(conteudo, nomeArquivo == null ? "os" + extensao(mime) : ajustarNome(nomeArquivo, mime), "os_origem");
        Long clienteId = null;
        if (dados.cliente() != null) {
            Optional<Cliente> c = Optional.empty();
            if (dados.cliente().cnpj() != null) {
                c = clientes.findFirstByCnpj(dados.cliente().cnpj());
            }
            if (c.isEmpty() && dados.cliente().nome() != null) {
                c = clientes.findFirstByNomeIgnoreCase(dados.cliente().nome());
            }
            clienteId = c.map(Cliente::getId).orElse(null);
        }
        List<String> bloq = dados.previsaoEntrega() == null ? List.of()
                : bloqueios.findByData(LocalDate.parse(dados.previsaoEntrega())).stream().map(b -> b.getProduto().getNome()).toList();
        return new ResultadoImportacao(metodo, Math.min(1.0, campos / 10.0), avisos, dados, documento, clienteId, bloq,
                texto.length() > 5000 ? texto.substring(0, 5000) : texto);
    }

    /** Ajustes finais: tipo de item, totais derivados e vencimento das parcelas sem data. */
    OsExtraida completar(OsExtraida o) {
        List<ItemExtraido> itens = new ArrayList<>();
        for (ItemExtraido i : Optional.ofNullable(o.itens()).orElse(List.of())) {
            String tipo = i.tipo() != null && i.tipo().toUpperCase(Locale.ROOT).startsWith("SERV") ? "SERVICO" : "PRODUTO";
            BigDecimal qtd = i.quantidade() == null ? BigDecimal.ONE : i.quantidade();
            BigDecimal total = i.valorTotal() != null && i.valorTotal().signum() != 0 ? i.valorTotal()
                    : qtd.multiply(nz(i.valorUnitario())).subtract(nz(i.desconto())).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemExtraido(tipo, i.codigo(), i.descricao(), i.unidade(), qtd, nz(i.valorUnitario()), nz(i.desconto()), total));
        }
        BigDecimal totProd = o.totalProdutos(), totServ = o.totalServicos(), total = o.total();
        if (!itens.isEmpty()) {
            if (totProd == null) totProd = soma(itens, "PRODUTO");
            if (totServ == null) totServ = soma(itens, "SERVICO");
        }
        if (total == null && (totProd != null || totServ != null)) {
            total = nz(totProd).add(nz(totServ)).subtract(nz(o.desconto()));
        }
        LocalDate base = o.dataEmissao() != null ? LocalDate.parse(o.dataEmissao()) : LocalDate.now(clock);
        int prazo = o.prazoPagamentoDias() == null ? 0 : o.prazoPagamentoDias();
        List<ParcelaExtraida> parcelas = Optional.ofNullable(o.parcelas()).orElse(List.of()).stream()
                .map(p -> new ParcelaExtraida(p.forma() == null ? "Boleto" : LeitorLayoutSafi.normalizarForma(p.forma()), p.valor(),
                        p.vencimento() != null ? p.vencimento() : base.plusDays(prazo).toString()))
                .toList();
        return new OsExtraida(o.codigo(), o.situacao(), o.dataEmissao(), o.hora(), o.previsaoEntrega(), o.vendedor(),
                o.cliente(), o.servicos(), itens, totProd, totServ, o.desconto(), total, o.prazoPagamentoDias(),
                new ArrayList<>(parcelas), o.observacoes());
    }

    private static BigDecimal soma(List<ItemExtraido> itens, String tipo) {
        return itens.stream().filter(i -> tipo.equals(i.tipo())).map(ItemExtraido::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String detectarMime(byte[] c, String nome) {
        if (c.length > 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F') return "application/pdf";
        if (c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (c.length > 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G') return "image/png";
        if (c.length > 12 && c[0] == 'R' && c[1] == 'I' && c[8] == 'W' && c[9] == 'E') return "image/webp";
        throw new NegocioException("Formato não suportado. Envie PDF, JPG, PNG ou WEBP.");
    }

    private static String extensao(String mime) {
        return switch (mime) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private static String ajustarNome(String nome, String mime) {
        return nome.contains(".") ? nome : nome + extensao(mime);
    }
}
