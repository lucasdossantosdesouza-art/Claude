package br.com.comissao.importacao;

import br.com.comissao.importacao.ImportacaoService.ResultadoImportacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Tag(name = "Importação de OS")
@RestController
@RequestMapping("/api/os/importacao")
@RequiredArgsConstructor
public class ImportacaoController {

    private final ImportacaoService service;
    private final LeitorIa leitorIa;
    private final ExtratorTexto extrator;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('VENDEDOR','GESTOR')")
    @Operation(summary = "Lê o documento da OS (PDF/foto) e devolve os dados para pré-preencher o formulário")
    public ResultadoImportacao importar(@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
        return service.importar(arquivo.getBytes(), arquivo.getOriginalFilename());
    }

    @GetMapping("/capacidades")
    public Map<String, Boolean> capacidades() {
        return Map.of("ia", leitorIa.disponivel(), "ocr", extrator.ocrDisponivel());
    }
}
