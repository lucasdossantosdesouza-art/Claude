package br.com.comissao.parametro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "parametro")
@Getter
@Setter
@NoArgsConstructor
public class Parametro {
    @Id
    @Enumerated(EnumType.STRING)
    private ChaveParametro chave;
    private BigDecimal valor;
    private String descricao;
    private String tipo;
    private int ordem;
}
