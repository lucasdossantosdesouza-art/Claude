package br.com.comissao.parametro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Acesso tipado aos parâmetros configuráveis das regras de negócio. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametroService {

    private final ParametroRepository parametros;
    private final FaixaComissaoRepository faixasComissao;
    private final FaixaMetaSemanalRepository faixasMeta;

    public BigDecimal valor(ChaveParametro chave) {
        return parametros.findById(chave).map(Parametro::getValor).orElse(BigDecimal.ZERO);
    }

    public int inteiro(ChaveParametro chave) {
        return valor(chave).intValue();
    }

    public boolean ligado(ChaveParametro chave) {
        return valor(chave).compareTo(BigDecimal.ONE) >= 0;
    }

    public List<FaixaComissao> faixasComissao() {
        return faixasComissao.findAllByOrderByValorMinimo();
    }

    public List<FaixaMetaSemanal> faixasMetaSemanal() {
        return faixasMeta.findAllByOrderByValorMinimo();
    }
}
