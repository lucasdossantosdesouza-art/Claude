package br.com.comissao.os;

import br.com.comissao.os.OrdemServicoService.FiltroOs;
import br.com.comissao.os.OsDtos.*;
import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "Ordens de Serviço")
@RestController
@RequestMapping("/api/os")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService service;
    private final UsuarioAtualService usuarioAtual;

    @GetMapping
    @Operation(summary = "Lista OS (vendedor vê apenas as próprias)")
    public List<OsResumo> listar(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) SituacaoOs situacao,
                                 @RequestParam(required = false) YearMonth mes,
                                 @RequestParam(required = false) Long vendedorId) {
        return service.listar(new FiltroOs(q, situacao, mes, vendedorId), usuarioAtual.atual());
    }

    @GetMapping("/{id}")
    public OsDetalhe detalhe(@PathVariable Long id) {
        return service.detalhe(id, usuarioAtual.atual());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
    public OsDetalhe criar(@Valid @RequestBody OsRequest req) {
        var u = usuarioAtual.atual();
        return service.detalhe(service.salvar(null, req, u).getId(), u);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
    public OsDetalhe atualizar(@PathVariable Long id, @Valid @RequestBody OsRequest req) {
        var u = usuarioAtual.atual();
        return service.detalhe(service.salvar(id, req, u).getId(), u);
    }

    @PostMapping(value = "/{id}/parcelas/{parcelaId}/recebimento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
    @Operation(summary = "Registra o recebimento da parcela com o comprovante anexado")
    public OsDetalhe receber(@PathVariable Long id, @PathVariable Long parcelaId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataRecebimento,
                             @RequestPart("comprovante") MultipartFile comprovante) {
        var u = usuarioAtual.atual();
        service.receberParcela(id, parcelaId, dataRecebimento, comprovante, u);
        return service.detalhe(id, u);
    }

    @DeleteMapping("/{id}/parcelas/{parcelaId}/recebimento")
    @PreAuthorize("hasRole('GESTOR')")
    public OsDetalhe desfazerRecebimento(@PathVariable Long id, @PathVariable Long parcelaId) {
        var u = usuarioAtual.atual();
        service.desfazerRecebimento(id, parcelaId, u);
        return service.detalhe(id, u);
    }

    @PostMapping(value = "/{id}/cancelamento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
    @Operation(summary = "Cancela a venda (justificativa obrigatória) com estorno opcional")
    public OsDetalhe cancelar(@PathVariable Long id, @Valid @RequestPart("dados") CancelamentoRequest dados,
                              @RequestPart(value = "comprovante", required = false) MultipartFile comprovante) {
        var u = usuarioAtual.atual();
        service.cancelar(id, dados, comprovante, u);
        return service.detalhe(id, u);
    }

    @PostMapping("/{id}/entrega")
    public OsDetalhe entrega(@PathVariable Long id, @RequestBody EntregaRequest req) {
        var u = usuarioAtual.atual();
        service.confirmarEntrega(id, req.dataEntregaReal(), u);
        return service.detalhe(id, u);
    }

    @PostMapping("/{id}/ocorrencias")
    public OsDetalhe ocorrencia(@PathVariable Long id, @Valid @RequestBody OcorrenciaRequest req) {
        var u = usuarioAtual.atual();
        service.registrarOcorrencia(id, req, u);
        return service.detalhe(id, u);
    }
}
