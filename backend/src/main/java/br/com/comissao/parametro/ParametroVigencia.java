package br.com.comissao.parametro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Valor de um parâmetro válido a partir de um mês (AAAA-MM) até a próxima vigência. */
@Entity
@Table(name = "parametro_vigencia")
@Getter
@Setter
@NoArgsConstructor
public class ParametroVigencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private ChaveParametro chave;
    private BigDecimal valor;
    private String vigenteDesde;
    private LocalDateTime registradoEm;
    private String registradoPor;
}
