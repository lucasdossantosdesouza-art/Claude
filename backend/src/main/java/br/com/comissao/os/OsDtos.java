package br.com.comissao.os;

import br.com.comissao.auditoria.AuditoriaService.LogDto;
import br.com.comissao.comissao.FaixaCalculada;
import br.com.comissao.comissao.StatusComissao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos da API de Ordens de Serviço. */
public final class OsDtos {

    private OsDtos() {
    }

    public record ClienteDto(Long id, @NotBlank(message = "informe o nome do cliente") String nome, String razaoSocial,
                             String cnpj, String codigo, String conta, String telefone, String endereco, String numero,
                             String bairro, String cidade, String uf, String cep) {
    }

    public record ItemDto(Long id, TipoItem tipo, String codigo, @NotBlank String descricao, String unidade,
                          BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal desconto, BigDecimal valorTotal) {
    }

    public record ParcelaRequest(Long id, @NotBlank String forma, @NotNull @Positive BigDecimal valor,
                                 @NotNull LocalDate vencimento) {
    }

    public record OsRequest(
            String codigo,
            Long vendedorId,
            @NotNull LocalDate dataEmissao,
            String hora,
            LocalDate previsaoEntrega,
            String servicos,
            String observacoes,
            BigDecimal desconto,
            @NotNull @Positive(message = "informe o valor da venda") BigDecimal total,
            Integer prazoPagamentoDias,
            boolean meiaMargem,
            String documentoOrigem,
            @NotNull @Valid ClienteDto cliente,
            List<@Valid ItemDto> itens,
            @NotEmpty(message = "informe ao menos uma parcela") List<@Valid ParcelaRequest> parcelas) {
    }

    public record OsResumo(Long id, String codigo, LocalDate dataEmissao, LocalDate previsaoEntrega, boolean entregue,
                           Long clienteId, String cliente, Long vendedorId, String vendedor, SituacaoOs situacao,
                           boolean meiaMargem, BigDecimal total, BigDecimal recebido, BigDecimal comissaoAPagar,
                           boolean atrasada) {
    }

    public record ParcelaDto(Long id, int numero, String forma, BigDecimal valor, LocalDate vencimento,
                             StatusParcela status, LocalDate dataRecebimento, String comprovante, boolean atrasada,
                             BigDecimal comissao, BigDecimal percentualComissao, StatusComissao statusComissao) {
    }

    public record ResumoComissaoOs(BigDecimal percentualAplicado, boolean faixaTravada, FaixaCalculada faixaDoMes,
                                   BigDecimal pendente, BigDecimal aPagar, BigDecimal paga) {
    }

    public record OcorrenciaDto(Long id, Long osId, String osCodigo, String cliente, String vendedor, String tipo,
                                String descricao, String status, String usuario, LocalDateTime criadoEm,
                                LocalDateTime resolvidoEm) {
    }

    public record AvisoResumo(Long id, String texto, boolean urgente) {
    }

    public record OsDetalhe(
            Long id, String codigo, SituacaoOs situacao, LocalDate dataEmissao, String hora, LocalDate previsaoEntrega,
            Long vendedorId, String vendedor, String servicos, String observacoes, BigDecimal totalProdutos,
            BigDecimal totalServicos, BigDecimal desconto, BigDecimal total, BigDecimal recebido,
            Integer prazoPagamentoDias, boolean meiaMargem, String documentoOrigem,
            String cancelMotivo, LocalDate cancelData, boolean estorno, String estornoMotivo, BigDecimal estornoValor,
            String estornoComprovante, boolean entregue, LocalDate dataEntregaReal,
            ClienteDto cliente, List<ItemDto> itens, List<ParcelaDto> parcelas, ResumoComissaoOs comissao,
            List<OcorrenciaDto> ocorrencias, List<LogDto> historico, List<AvisoResumo> avisos,
            List<String> bloqueiosNaEntrega) {
    }

    public record CancelamentoRequest(@NotBlank String motivo, boolean estorno, Long motivoEstornoId,
                                      BigDecimal valorEstorno) {
    }

    public record EntregaRequest(LocalDate dataEntregaReal) {
    }

    public record OcorrenciaRequest(@NotBlank String tipo, @NotBlank String descricao) {
    }
}
