package br.com.comissao.relatorio;

import br.com.comissao.usuario.UsuarioAtualService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "Relatórios")
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class RelatorioController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RelatorioService service;
    private final UsuarioAtualService usuarioAtual;
    private final Clock clock;

    public enum Formato { XLSX, PDF }

    @GetMapping("/comissoes")
    public ResponseEntity<byte[]> comissoes(@RequestParam(required = false) YearMonth mes,
                                            @RequestParam(defaultValue = "XLSX") Formato formato) {
        YearMonth m = mes == null ? YearMonth.now(clock) : mes;
        var u = usuarioAtual.atual();
        byte[] dados = formato == Formato.PDF ? service.comissoesPdf(m, u) : service.comissoesExcel(m, u);
        return arquivo(dados, "comissoes_" + m, formato);
    }

    @GetMapping("/contas-receber")
    public ResponseEntity<byte[]> contasReceber(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                                @RequestParam(defaultValue = "XLSX") Formato formato) {
        byte[] dados = formato == Formato.PDF ? service.contasReceberPdf(inicio, fim) : service.contasReceberExcel(inicio, fim);
        return arquivo(dados, "contas_a_receber_" + LocalDate.now(clock), formato);
    }

    private static ResponseEntity<byte[]> arquivo(byte[] dados, String nome, Formato f) {
        String ext = f == Formato.PDF ? ".pdf" : ".xlsx";
        return ResponseEntity.ok()
                .contentType(f == Formato.PDF ? MediaType.APPLICATION_PDF : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome + ext).build().toString())
                .body(dados);
    }
}
