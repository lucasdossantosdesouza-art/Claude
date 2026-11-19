package br.com.comissao.comissao;

import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.os.OrdemServico;
import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.os.Parcela;
import br.com.comissao.os.StatusParcela;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static br.com.comissao.shared.Valores.aplicarPercentual;
import static br.com.comissao.shared.Valores.formatarMoeda;

/**
 * Geração da comissão parcela a parcela, fechamento mensal (trava da faixa) e pagamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComissaoService {

    private final ComissaoRepository comissoes;
    private final FechamentoMensalRepository fechamentos;
    private final OrdemServicoRepository ordens;
    private final UsuarioRepository usuarios;
    private final RegraComissaoService regras;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock clock;

    /** Gera/atualiza a comissão de cada parcela da OS. Comissões já pagas nunca são recalculadas. */
    @Transactional
    public void sincronizar(OrdemServico os) {
        RegraComissaoService.PercentualOs pct = regras.percentualDaOs(os);
        LocalDateTime agora = LocalDateTime.now(clock);
        for (Parcela p : os.getParcelas()) {
            if (p.getId() == null) {
                continue;
            }
            Comissao c = comissoes.findByParcelaId(p.getId()).orElseGet(Comissao::new);
            if (c.getStatus() == StatusComissao.PAGA) {
                continue;
            }
            StatusComissao status;
            if (os.isCancelada() || p.getStatus() == StatusParcela.CANCELADA) {
                status = StatusComissao.CANCELADA;
            } else if (p.getStatus() == StatusParcela.RECEBIDA) {
                status = pct.travado() ? StatusComissao.AUTORIZADA : StatusComissao.PROVISORIA;
            } else {
                status = StatusComissao.PENDENTE_RECEBIMENTO;
            }
            c.setOs(os);
            c.setParcela(p);
            c.setVendedor(os.getVendedor());
            c.setMesRef(YearMonth.from(os.getDataEmissao()).toString());
            c.setBase(p.getValor());
            c.setPercentual(pct.percentual());
            c.setValor(aplicarPercentual(p.getValor(), pct.percentual()));
            if (status == StatusComissao.AUTORIZADA && c.getAutorizadaEm() == null) {
                c.setAutorizadaEm(agora);
            } else if (status != StatusComissao.AUTORIZADA) {
                c.setAutorizadaEm(null);
            }
            c.setStatus(status);
            comissoes.save(c);
        }
    }

    /** A faixa provisória muda a cada nova venda: recalcula todas as OS do mês. */
    @Transactional
    public void sincronizarMes(YearMonth mes) {
        ordens.doPeriodo(mes.atDay(1), mes.plusMonths(1).atDay(1)).forEach(this::sincronizar);
    }

    /** Trava a faixa de cada vendedor no mês e autoriza as comissões das parcelas já recebidas. */
    @Transactional
    public List<FechamentoMensal> fecharMes(YearMonth mes, Usuario responsavel) {
        if (!mes.isBefore(YearMonth.now(clock))) {
            throw new NegocioException("Só é possível fechar meses já encerrados.");
        }
        List<FechamentoMensal> novos = new ArrayList<>();
        for (Usuario v : usuarios.findByPerfilOrderByNome(Perfil.VENDEDOR)) {
            if (fechamentos.findByMesRefAndVendedorId(mes.toString(), v.getId()).isPresent()) {
                continue;
            }
            FaixaCalculada f = regras.calcularFaixaAberta(v.getId(), mes);
            FechamentoMensal fm = new FechamentoMensal();
            fm.setMesRef(mes.toString());
            fm.setVendedor(v);
            fm.setTotalVendido(f.totalVendido());
            fm.setFaturamentoEmpresa(f.faturamentoEmpresa());
            fm.setPercentual(f.percentual());
            fm.setRegra(f.regra());
            fm.setSalarioFixo(parametros.valor(ChaveParametro.SALARIO_FIXO, mes));
            fm.setFechadoEm(LocalDateTime.now(clock));
            novos.add(fechamentos.save(fm));
        }
        fechamentos.flush();
        sincronizarMes(mes);
        auditoria.registrar(null, responsavel, "FECHAMENTO_MES", "Mês " + mes + " fechado (" + novos.size() + " vendedores)");
        return novos;
    }

    @Transactional(readOnly = true)
    public List<YearMonth> mesesPendentesDeFechamento() {
        return ordens.mesesSemFechamento(YearMonth.now(clock).atDay(1)).stream().map(YearMonth::parse).toList();
    }

    /** Fecha automaticamente os meses anteriores (virada do mês), se o parâmetro estiver ligado. */
    @Transactional
    public void fechamentoAutomatico() {
        if (!parametros.ligado(ChaveParametro.FECHAMENTO_AUTOMATICO)) {
            return;
        }
        for (YearMonth m : mesesPendentesDeFechamento()) {
            log.info("Fechamento automático do mês {}", m);
            fecharMes(m, null);
        }
    }

    @Transactional
    public int pagar(List<Long> ids, Usuario gestor) {
        int pagas = 0;
        for (Comissao c : comissoes.findAllById(ids)) {
            if (c.getStatus() == StatusComissao.AUTORIZADA) {
                c.setStatus(StatusComissao.PAGA);
                c.setPagaEm(LocalDateTime.now(clock));
                auditoria.registrar(c.getOs().getId(), gestor, "COMISSAO_PAGA", "Comissão de " + formatarMoeda(c.getValor()) + " paga");
                pagas++;
            }
        }
        if (pagas == 0) {
            throw new NegocioException("Nenhuma comissão autorizada selecionada. Comissões provisórias só podem ser pagas após o fechamento do mês.");
        }
        return pagas;
    }
}
