package br.com.comissao.parametro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * Acesso tipado aos parâmetros das regras de negócio, sempre pela vigência:
 * o valor usado em um mês é o da versão mais recente com "vigente desde" ≤ mês.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametroService {

    private final ParametroVigenciaRepository vigencias;
    private final FaixaComissaoRepository faixasComissao;
    private final FaixaMetaSemanalRepository faixasMeta;
    private final Clock clock;

    public BigDecimal valor(ChaveParametro chave, YearMonth mes) {
        return vigencias.findFirstByChaveAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(chave, mes.toString())
                .map(ParametroVigencia::getValor).orElse(BigDecimal.ZERO);
    }

    /** Valor vigente no mês atual. */
    public BigDecimal valor(ChaveParametro chave) {
        return valor(chave, YearMonth.now(clock));
    }

    public int inteiro(ChaveParametro chave) {
        return valor(chave).intValue();
    }

    public boolean ligado(ChaveParametro chave) {
        return valor(chave).compareTo(BigDecimal.ONE) >= 0;
    }

    public List<FaixaComissao> faixasComissao(YearMonth mes) {
        String v = faixasComissao.vigenciaEm(mes.toString());
        return v == null ? List.of() : faixasComissao.findByVigenteDesdeOrderByValorMinimo(v);
    }

    public List<FaixaMetaSemanal> faixasMetaSemanal(YearMonth mes) {
        String v = faixasMeta.vigenciaEm(mes.toString());
        return v == null ? List.of() : faixasMeta.findByVigenteDesdeOrderByValorMinimo(v);
    }
}
