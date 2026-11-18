package br.com.comissao.os;

import br.com.comissao.cliente.Cliente;
import br.com.comissao.parametro.MotivoEstorno;
import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "ordem_servico")
@Getter
@Setter
@NoArgsConstructor
public class OrdemServico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    private SituacaoOs situacao = SituacaoOs.ABERTA;

    private LocalDate dataEmissao;
    private String hora;
    private LocalDate previsaoEntrega;
    private String servicos;
    private String observacoes;
    private BigDecimal totalProdutos = BigDecimal.ZERO;
    private BigDecimal totalServicos = BigDecimal.ZERO;
    private BigDecimal desconto = BigDecimal.ZERO;
    private BigDecimal total;
    private Integer prazoPagamentoDias;
    private boolean meiaMargem;
    private String documentoOrigem;

    private String cancelMotivo;
    private LocalDate cancelData;
    private boolean estorno;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estorno_motivo_id")
    private MotivoEstorno estornoMotivo;
    private BigDecimal estornoValor;
    private String estornoComprovante;

    private boolean entregue;
    private LocalDate dataEntregaReal;
    private LocalDateTime criadoEm;

    @Version
    private long version;

    @OneToMany(mappedBy = "os", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemOs> itens = new ArrayList<>();

    @OneToMany(mappedBy = "os", cascade = CascadeType.ALL)
    @OrderBy("numero")
    private List<Parcela> parcelas = new ArrayList<>();

    public boolean isCancelada() {
        return situacao == SituacaoOs.CANCELADA;
    }

    public void adicionarItem(ItemOs item) {
        item.setOs(this);
        itens.add(item);
    }

    public void adicionarParcela(Parcela p) {
        p.setOs(this);
        parcelas.add(p);
    }

    /** Renumera as parcelas pela data de vencimento. */
    public void renumerarParcelas() {
        parcelas.sort(Comparator.comparing(Parcela::getVencimento).thenComparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId()));
        for (int i = 0; i < parcelas.size(); i++) {
            parcelas.get(i).setNumero(i + 1);
        }
    }

    /** Fechada quando todas as parcelas não canceladas foram recebidas. */
    public void atualizarSituacao() {
        if (isCancelada()) {
            return;
        }
        List<Parcela> validas = parcelas.stream().filter(p -> p.getStatus() != StatusParcela.CANCELADA).toList();
        boolean todasRecebidas = !validas.isEmpty() && validas.stream().allMatch(p -> p.getStatus() == StatusParcela.RECEBIDA);
        situacao = todasRecebidas ? SituacaoOs.FECHADA : SituacaoOs.ABERTA;
    }

    public BigDecimal totalRecebido() {
        return parcelas.stream().filter(p -> p.getStatus() == StatusParcela.RECEBIDA)
                .map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
