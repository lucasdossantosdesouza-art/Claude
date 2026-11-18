package br.com.comissao.comissao;

/**
 * Ciclo de vida da comissão de uma parcela.
 */
public enum StatusComissao {
    /** Parcela ainda não recebida (previsão). */
    PENDENTE_RECEBIMENTO("Pendente de recebimento"),
    /** Recebida, mas o mês da venda ainda não foi fechado: a faixa (%) pode mudar. */
    PROVISORIA("Recebida (mês aberto)"),
    /** Recebida e com faixa travada: autorizada para pagamento. */
    AUTORIZADA("Autorizada p/ pagamento"),
    PAGA("Paga"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusComissao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
