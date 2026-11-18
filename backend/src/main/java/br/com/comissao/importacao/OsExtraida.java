package br.com.comissao.importacao;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados extraídos do documento da OS (mesmo formato para o leitor SAFI e para a IA).
 * Datas em ISO (AAAA-MM-DD); valores sem símbolo de moeda.
 */
public record OsExtraida(
        @JsonPropertyDescription("Código/número da OS") String codigo,
        @JsonPropertyDescription("Aberta ou Fechada") String situacao,
        @JsonPropertyDescription("Data de emissão AAAA-MM-DD") String dataEmissao,
        @JsonPropertyDescription("Hora HH:MM") String hora,
        @JsonPropertyDescription("Previsão de entrega AAAA-MM-DD") String previsaoEntrega,
        @JsonPropertyDescription("Nome do vendedor") String vendedor,
        ClienteExtraido cliente,
        @JsonPropertyDescription("Texto livre de 'Serviços a executar'") String servicos,
        List<ItemExtraido> itens,
        BigDecimal totalProdutos,
        BigDecimal totalServicos,
        @JsonPropertyDescription("Desconto geral") BigDecimal desconto,
        @JsonPropertyDescription("Total do pedido") BigDecimal total,
        Integer prazoPagamentoDias,
        List<ParcelaExtraida> parcelas,
        String observacoes) {

    public record ClienteExtraido(
            @JsonPropertyDescription("Nº Conta Cliente") String conta,
            @JsonPropertyDescription("Nome fantasia") String nome,
            String razaoSocial,
            @JsonPropertyDescription("CNPJ ou CPF") String cnpj,
            @JsonPropertyDescription("Código do cliente") String codigo,
            String telefone,
            @JsonPropertyDescription("Rua/logradouro") String endereco,
            String numero,
            String bairro,
            String cidade,
            @JsonPropertyDescription("Sigla do estado com 2 letras") String uf,
            String cep) {
    }

    public record ItemExtraido(
            @JsonPropertyDescription("PRODUTO ou SERVICO") String tipo,
            String codigo,
            String descricao,
            String unidade,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            BigDecimal desconto,
            BigDecimal valorTotal) {
    }

    public record ParcelaExtraida(
            @JsonPropertyDescription("Forma de pagamento, ex.: Pix, Boleto") String forma,
            BigDecimal valor,
            @JsonPropertyDescription("Vencimento AAAA-MM-DD") String vencimento) {
    }

    public static OsExtraida vazia() {
        return new OsExtraida(null, null, null, null, null, null, null, null, new ArrayList<>(), null, null, null,
                null, null, new ArrayList<>(), null);
    }
}
