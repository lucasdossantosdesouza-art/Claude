package br.com.comissao.parametro;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tipos de problema/reclamação do pós-venda (defeito, troca, retrabalho, insatisfação...). */
@Entity
@Table(name = "tipo_ocorrencia")
public class TipoOcorrencia extends ItemCadastro {
}
