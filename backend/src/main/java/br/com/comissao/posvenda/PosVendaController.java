package br.com.comissao.posvenda;

import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.os.OrdemServicoService;
import br.com.comissao.os.OsDtos.OcorrenciaDto;
import br.com.comissao.os.OsDtos.OsResumo;
import br.com.comissao.os.SituacaoOs;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pós-venda")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PosVendaController {

    private final OcorrenciaRepository ocorrencias;
    private final OrdemServicoRepository ordens;
    private final OrdemServicoService osService;
    private final UsuarioAtualService usuarioAtual;

    public record PainelPosVenda(List<OsResumo> pendentesEntrega, long entregues, long entreguesNoPrazo,
                                 List<OcorrenciaDto> ocorrencias) {
    }

    @GetMapping("/posvenda")
    @Transactional(readOnly = true)
    public PainelPosVenda painel(@RequestParam(required = false, defaultValue = "ABERTA") String status) {
        Usuario u = usuarioAtual.atual();
        Long vid = u.isVendedor() ? u.getId() : null;
        var pendentes = vid == null ? ordens.findBySituacaoNotAndEntregueFalseOrderByPrevisaoEntrega(SituacaoOs.CANCELADA)
                : ordens.findBySituacaoNotAndEntregueFalseAndVendedorIdOrderByPrevisaoEntrega(SituacaoOs.CANCELADA, vid);
        return new PainelPosVenda(pendentes.stream().map(osService::resumo).toList(), ordens.totalEntregues(vid),
                ordens.entreguesNoPrazo(vid),
                ocorrencias.listar(status.isBlank() ? null : status, vid).stream().map(OrdemServicoService::ocorrenciaDto).toList());
    }

    @PostMapping("/ocorrencias/{id}/resolucao")
    public void resolver(@PathVariable Long id) {
        osService.resolverOcorrencia(id, usuarioAtual.atual());
    }
}
