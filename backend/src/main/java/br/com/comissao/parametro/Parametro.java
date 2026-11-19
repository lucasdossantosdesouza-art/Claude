package br.com.comissao.parametro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Catálogo do parâmetro (descrição, tipo, origem no documento). O valor fica em {@link ParametroVigencia}. */
@Entity
@Table(name = "parametro")
@Getter
@Setter
@NoArgsConstructor
public class Parametro {
    @Id
    @Enumerated(EnumType.STRING)
    private ChaveParametro chave;
    private String descricao;
    private String tipo;
    private int ordem;
    private String grupo;
    /** Onde a regra aparece no documento de requisitos. */
    private String origem;
    /** Valor definido no documento de requisitos (null quando o documento não define). */
    private BigDecimal valorDocumento;
}
