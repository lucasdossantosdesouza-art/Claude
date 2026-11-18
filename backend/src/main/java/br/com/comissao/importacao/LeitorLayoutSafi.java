package br.com.comissao.importacao;

import br.com.comissao.importacao.OsExtraida.ClienteExtraido;
import br.com.comissao.importacao.OsExtraida.ItemExtraido;
import br.com.comissao.importacao.OsExtraida.ParcelaExtraida;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor do layout fixo da OS emitida pelo SAFI: localiza os campos pelos rótulos impressos
 * (Código da OS, Cliente, CNPJ, tabela de itens, Total do Pedido, parcelas...).
 * Funciona sobre o texto do PDF ou sobre o texto obtido por OCR.
 */
@Component
public class LeitorLayoutSafi {

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;
    private static final String DATA = "(\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{2,4})";
    private static final String DINHEIRO = "(?:R\\$\\s*)?(-?[\\d.]+,\\d{2}|-?\\d+\\.\\d{2})";
    private static final String UNIDADES = "(?:UN|UND|UNID|PC|PÇ|PCS|M|MT|M2|M²|KG|CX|PCT|SV|SRV|SERV|H|HR|JG|PAR|KIT|RL|FL|CJ)";
    private static final String FORMAS = "(Pix|Boleto|Cart[ãa]o(?:\\s*de)?\\s*(?:Cr[ée]dito|D[ée]bito)?|Dinheiro|Transfer[êe]ncia|Cheque|Dep[óo]sito)";

    private static final Pattern ITEM = Pattern.compile(
            "^\\s*(\\S+)\\s+(.+?)\\s+(" + UNIDADES + ")\\s+([\\d.,]+)\\s+(?:R\\$\\s*)?([\\d.,]+)\\s+(?:R\\$\\s*)?([\\d.,]+)\\s+(?:R\\$\\s*)?([\\d.,]+)\\s*$", F);
    private static final Pattern FORMA = Pattern.compile(FORMAS, F);
    private static final Pattern DATA_P = Pattern.compile(DATA);
    private static final Pattern VALOR_BR = Pattern.compile("(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");

    public OsExtraida ler(String texto) {
        String t = texto.replace("\r", "");

        String codigo = primeiro(t,
                "(?:C[óo]d(?:igo)?\\.?\\s*(?:da\\s*)?O\\.?\\s*S\\.?|O\\.?\\s*S\\.?\\s*(?:N[º°o]\\.?|n[uú]mero))\\s*:?\\s*([\\w\\-/]+)",
                "ORDEM DE SERVI[ÇC]O\\s*(?:N[º°o]\\.?)?\\s*:?\\s*(\\d[\\w\\-/]*)");
        String situacao = busca(t, "Situa[çc][ãa]o\\s*:?\\s*(Aberta|Fechada|Cancelada)");
        String emissao = dataIso(busca(t, "(?:Data\\s*(?:de\\s*)?)?Emiss[ãa]o\\s*:?\\s*" + DATA));
        String hora = busca(t, "Hora\\s*:?\\s*(\\d{1,2}:\\d{2})");
        String previsao = dataIso(busca(t, "Previs[ãa]o(?:\\s*de\\s*entrega)?\\s*:?\\s*" + DATA));
        String vendedor = busca(t, "Vendedor(?:\\s*respons[áa]vel)?\\s*:\\s*([^\\n]+?)(?:\\s{2,}|\\s+Prazo|\\s+Forma|$)");

        String endereco = busca(t, "(?:Endere[çc]o|Rua|Logradouro)\\s*:?\\s*([^\\n,]+?)(?:,|\\s+N[º°o]\\.?\\s|\\s+N[uú]mero|\\s{2,}|$)");
        String numero = null;
        if (endereco != null) {
            numero = busca(t, "(?:Endere[çc]o|Rua)\\s*:?\\s*[^\\n,]+,\\s*(\\d+\\w?)");
            if (numero == null) {
                numero = busca(t, "(?:N[º°o]\\.?|N[uú]mero)\\s*:?\\s*(\\d+\\w?)\\b(?!\\s*Conta)");
            }
        }
        ClienteExtraido cliente = new ClienteExtraido(
                busca(t, "(?:N[º°o]\\.?\\s*)?Conta(?:\\s*(?:do\\s*)?Cliente)?\\s*:?\\s*(\\d+)"),
                primeiro(t, "^\\s*Cliente\\s*(?:\\(nome fantasia\\))?\\s*:\\s*([^\\n]+?)(?:\\s{2,}|\\s+C[óo]d|\\s+Raz|\\s+CNP|$)",
                        "Nome\\s*Fantasia\\s*:?\\s*([^\\n]+?)(?:\\s{2,}|$)"),
                busca(t, "Raz[ãa]o\\s*Social\\s*:?\\s*([^\\n]+?)(?:\\s{2,}|\\s+CNP|\\s+CPF|$)"),
                primeiro(t, "(\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b)",
                        "(?:CNP\\S?|CPF)(?:/CPF)?\\s*:?\\s*(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})"),
                busca(t, "C[óo]d(?:igo)?\\.?\\s*(?:do\\s*)?Cliente\\s*:?\\s*(\\w+)"),
                busca(t, "(?:Telefone|Fone|Tel\\.?|Celular)\\s*:?\\s*(\\(?\\d{2}\\)?\\s*\\d{4,5}[\\-\\s]?\\d{4})"),
                endereco, numero,
                busca(t, "Bairro\\s*:?\\s*([^\\n]+?)(?:\\s{2,}|\\s+Cidade|\\s+CEP|$)"),
                busca(t, "(?:Cidade|Munic[íi]pio)\\s*:?\\s*([^\\n/]+?)(?:\\s*/\\s*[A-Z]{2}|\\s{2,}|\\s+(?:UF|Estado)|\\s+CEP|$)"),
                primeiroSensivel(t, "(?:UF|Estado)\\s*:?\\s*([A-Z]{2})\\b", "(?:Cidade|Munic[íi]pio)\\s*:?\\s*[^\\n/]+/\\s*([A-Z]{2})\\b"),
                busca(t, "CEP\\s*:?\\s*(\\d{5}-?\\d{3}|\\d{2}\\.\\d{3}-\\d{3})"));

        String servicos = secao(t, "Servi[çc]os\\s*a\\s*executar",
                "\\n\\s*C[óo]d(?:igo)?\\.?\\s+Descri", "\\n\\s*Produtos", "\\n\\s*Itens", "\\n\\s*Total");

        List<ItemExtraido> itens = new ArrayList<>();
        Matcher m = ITEM.matcher(t);
        while (m.find()) {
            String cod = m.group(1);
            if (cod.matches("(?i)(total|subtotal|c[óo]d).*")) {
                continue;
            }
            String desc = m.group(2).trim();
            String un = m.group(3).toUpperCase(Locale.ROOT);
            boolean servico = List.of("SV", "SRV", "SERV", "H", "HR").contains(un)
                    || Pattern.compile("servi[çc]o|m[ãa]o de obra|arte|cria[çc][ãa]o", F).matcher(desc).find();
            itens.add(new ItemExtraido(servico ? "SERVICO" : "PRODUTO", cod, desc, un,
                    numeroBr(m.group(4)), numeroBr(m.group(5)), numeroBr(m.group(6)), numeroBr(m.group(7))));
        }

        BigDecimal totalProdutos = numeroBr(busca(t, "Total\\s*(?:de\\s*)?Produtos\\s*:?\\s*" + DINHEIRO));
        BigDecimal totalServicos = numeroBr(busca(t, "Total\\s*(?:de\\s*)?Servi[çc]os\\s*:?\\s*" + DINHEIRO));
        BigDecimal desconto = numeroBr(busca(t, "Desconto(?:\\s*geral)?\\s*:?\\s*" + DINHEIRO));
        BigDecimal total = numeroBr(busca(t, "Total\\s*(?:do\\s*)?(?:Pedido|Geral|da\\s*OS)\\s*:?\\s*" + DINHEIRO));
        String prazo = busca(t, "Prazo\\s*(?:de\\s*)?(?:pagamento|pgto\\.?)?\\s*:?\\s*(\\d+)\\s*(?:dias|d\\b)");

        List<ParcelaExtraida> parcelas = new ArrayList<>();
        for (String linha : t.split("\n")) {
            Matcher fm = FORMA.matcher(linha);
            if (!fm.find() || Pattern.compile("Total|Forma\\s*de\\s*pagamento\\s*:", F).matcher(linha).find()) {
                continue;
            }
            Matcher vm = VALOR_BR.matcher(linha);
            String ultimoValor = null;
            while (vm.find()) {
                ultimoValor = vm.group(1);
            }
            if (ultimoValor == null) {
                continue;
            }
            Matcher dm = DATA_P.matcher(linha);
            parcelas.add(new ParcelaExtraida(normalizarForma(fm.group(1)), numeroBr(ultimoValor),
                    dm.find() ? dataIso(dm.group(1)) : null));
        }
        if (parcelas.isEmpty()) {
            String fp = busca(t, "Forma\\s*(?:de\\s*)?pagamento\\s*:?\\s*" + FORMAS);
            if (fp != null && total != null) {
                parcelas.add(new ParcelaExtraida(normalizarForma(fp), total, null));
            }
        }
        String observacoes = secao(t, "Observa[çc][õo]es(?:\\s*gerais)?", "\\n\\s*Assinatura", "\\n\\s*_{5,}", "\\n\\s*Vendedor");

        return new OsExtraida(codigo, situacao == null ? null : capitalizar(situacao), emissao, hora, previsao, vendedor,
                cliente, servicos, itens, totalProdutos, totalServicos, desconto, total,
                prazo == null ? null : Integer.parseInt(prazo), parcelas, observacoes);
    }

    // ------------------------------------------------------------ utilitários

    static String busca(String texto, String regex) {
        return grupo(Pattern.compile(regex, F).matcher(texto));
    }

    private static String primeiro(String texto, String... regexes) {
        for (String r : regexes) {
            String v = busca(texto, r);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** Busca sensível a maiúsculas (siglas de UF). */
    private static String primeiroSensivel(String texto, String... regexes) {
        for (String r : regexes) {
            String v = grupo(Pattern.compile(r, Pattern.MULTILINE).matcher(texto));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String grupo(Matcher m) {
        if (!m.find()) {
            return null;
        }
        String v = m.group(1).replaceAll("^[\\s:\\-]+|[\\s:\\-]+$", "");
        return v.isEmpty() ? null : v;
    }

    private static String secao(String texto, String inicio, String... fins) {
        Matcher m = Pattern.compile(inicio + "\\s*:?\\s*\\n?(.*?)(?=" + String.join("|", fins) + "|\\z)",
                F | Pattern.DOTALL).matcher(texto);
        if (!m.find()) {
            return null;
        }
        String s = m.group(1).strip();
        return s.isEmpty() ? null : s;
    }

    public static BigDecimal numeroBr(String txt) {
        if (txt == null) {
            return null;
        }
        String t = txt.replaceAll("[^\\d,.\\-]", "");
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        } else if (t.chars().filter(c -> c == '.').count() > 1) {
            t = t.replace(".", "");
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String dataIso(String txt) {
        if (txt == null) {
            return null;
        }
        for (String fmt : List.of("dd/MM/yyyy", "dd/MM/yy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy")) {
            try {
                return LocalDate.parse(txt.trim(), DateTimeFormatter.ofPattern(fmt)).toString();
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    public static String normalizarForma(String txt) {
        String t = txt.toLowerCase(Locale.ROOT);
        if (t.contains("pix")) return "Pix";
        if (t.contains("boleto")) return "Boleto";
        if (t.contains("créd") || t.contains("cred")) return "Cartão de crédito";
        if (t.contains("déb") || t.contains("deb")) return "Cartão de débito";
        if (t.contains("cart")) return "Cartão de crédito";
        if (t.contains("dinheiro")) return "Dinheiro";
        if (t.contains("transf")) return "Transferência";
        return capitalizar(txt.trim());
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
