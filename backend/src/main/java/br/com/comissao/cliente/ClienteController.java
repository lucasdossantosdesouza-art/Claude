package br.com.comissao.cliente;

import br.com.comissao.comissao.ComissaoConsultaService.ComissaoDto;
import br.com.comissao.comissao.ComissaoRepository;
import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.os.OrdemServicoService;
import br.com.comissao.os.OsDtos.ClienteDto;
import br.com.comissao.os.OsDtos.OcorrenciaDto;
import br.com.comissao.os.OsDtos.OsResumo;
import br.com.comissao.os.ParcelaRepository;
import br.com.comissao.os.SituacaoOs;
import br.com.comissao.posvenda.OcorrenciaRepository;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Clientes e busca")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteController {

    private final ClienteRepository clientes;
    private final OrdemServicoRepository ordens;
    private final ParcelaRepository parcelas;
    private final ComissaoRepository comissoes;
    private final OcorrenciaRepository ocorrencias;
    private final OrdemServicoService osService;
    private final UsuarioAtualService usuarioAtual;

    @GetMapping("/clientes")
    public List<ClienteDto> buscar(@RequestParam(defaultValue = "") String q) {
        return clientes.buscar(q.trim(), PageRequest.of(0, 20)).stream().map(OrdemServicoService::clienteDto).toList();
    }

    public record ParcelaCliente(Long id, Long osId, String osCodigo, String cliente, int numero, String forma,
                                 BigDecimal valor, LocalDate vencimento, String status, LocalDate dataRecebimento) {
    }

    public record HistoricoCliente(ClienteDto cliente, BigDecimal totalComprado, BigDecimal emAberto, List<OsResumo> compras,
                                   List<ParcelaCliente> parcelas, List<OcorrenciaDto> ocorrencias,
                                   List<ComissaoDto> comissoesPagas) {
    }

    /** Histórico completo do cliente: compras, datas de pagamento, pós-venda e comissões pagas. */
    @GetMapping("/clientes/{id}/historico")
    public HistoricoCliente historico(@PathVariable Long id) {
        Usuario u = usuarioAtual.atual();
        Cliente c = clientes.findById(id).orElseThrow(() -> new NaoEncontradoException("Cliente não encontrado"));
        Long vid = u.isVendedor() ? u.getId() : null;
        var compras = (vid == null ? ordens.findByClienteIdOrderByDataEmissaoDesc(id)
                : ordens.findByClienteIdAndVendedorIdOrderByDataEmissaoDesc(id, vid)).stream().map(osService::resumo).toList();
        var parc = parcelas.doClienteId(id, vid).stream().map(p -> new ParcelaCliente(p.getId(), p.getOs().getId(),
                p.getOs().getCodigo(), c.getNome(), p.getNumero(), p.getForma(), p.getValor(), p.getVencimento(),
                p.getStatus().name(), p.getDataRecebimento())).toList();
        List<ComissaoDto> pagas = u.getPerfil() == Perfil.PRODUCAO ? List.of()
                : comissoes.pagasDoCliente(c.getNome(), vid).stream().filter(cm -> cm.getOs().getCliente().getId().equals(id))
                .map(cm -> new ComissaoDto(cm.getId(), cm.getOs().getId(), cm.getOs().getCodigo(), c.getNome(),
                        cm.getVendedor().getId(), cm.getVendedor().getNome(), 0, null, null, null, null,
                        cm.getOs().isMeiaMargem(), cm.getBase(), cm.getPercentual(), cm.getValor(), cm.getStatus(), cm.getPagaEm()))
                .toList();
        BigDecimal total = compras.stream().filter(o -> o.situacao() != SituacaoOs.CANCELADA).map(OsResumo::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aberto = parc.stream().filter(p -> "PENDENTE".equals(p.status())).map(ParcelaCliente::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HistoricoCliente(OrdemServicoService.clienteDto(c), total, aberto, compras, parc,
                ocorrencias.doCliente(id, vid).stream().map(OrdemServicoService::ocorrenciaDto).toList(), pagas);
    }

    public record ResultadoBusca(List<ClienteDto> clientes, List<OsResumo> vendas, List<ParcelaCliente> datasPagamento,
                                 List<ComissaoDto> comissoesPagas) {
    }

    /** Busca por nome de cliente disponível em todos os perfis. */
    @GetMapping("/busca")
    public ResultadoBusca busca(@RequestParam String q) {
        Usuario u = usuarioAtual.atual();
        Long vid = u.isVendedor() ? u.getId() : null;
        String termo = q.trim();
        var vendas = osService.listar(new OrdemServicoService.FiltroOs(termo, null, null, null), u).stream().limit(100).toList();
        var parc = parcelas.doCliente(termo, vid).stream().limit(100).map(p -> new ParcelaCliente(p.getId(), p.getOs().getId(),
                p.getOs().getCodigo(), p.getOs().getCliente().getNome(), p.getNumero(), p.getForma(), p.getValor(),
                p.getVencimento(), p.getStatus().name(), p.getDataRecebimento())).toList();
        List<ComissaoDto> pagas = u.getPerfil() == Perfil.PRODUCAO ? List.of()
                : comissoes.pagasDoCliente(termo, vid).stream().limit(100)
                .map(cm -> new ComissaoDto(cm.getId(), cm.getOs().getId(), cm.getOs().getCodigo(), cm.getOs().getCliente().getNome(),
                        cm.getVendedor().getId(), cm.getVendedor().getNome(), 0, null, null, null, null,
                        cm.getOs().isMeiaMargem(), cm.getBase(), cm.getPercentual(), cm.getValor(), cm.getStatus(), cm.getPagaEm()))
                .toList();
        return new ResultadoBusca(buscar(termo), vendas, parc, pagas);
    }
}
