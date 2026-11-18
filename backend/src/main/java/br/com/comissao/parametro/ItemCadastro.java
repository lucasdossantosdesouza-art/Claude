package br.com.comissao.parametro;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** Base das listas simples configuráveis (formas de pagamento, motivos de estorno, produtos). */
@MappedSuperclass
@Getter
@Setter
public abstract class ItemCadastro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private boolean ativo = true;
}
