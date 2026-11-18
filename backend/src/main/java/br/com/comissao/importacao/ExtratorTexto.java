package br.com.comissao.importacao;

import br.com.comissao.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Extrai texto de PDF (camada de texto ou OCR) e de imagens (OCR via Tesseract). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtratorTexto {

    private final AppProperties props;

    public String textoDoPdf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /** PDF digitalizado (sem camada de texto): rasteriza cada página e aplica OCR. */
    public String ocrDoPdf(byte[] pdf) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 250, ImageType.GRAY);
                Path tmp = Files.createTempFile("os-pagina", ".png");
                try {
                    ImageIO.write(img, "png", tmp.toFile());
                    sb.append(ocr(tmp)).append('\n');
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }
        return sb.toString();
    }

    public String ocrDaImagem(byte[] imagem, String extensao) throws IOException {
        Path tmp = Files.createTempFile("os-foto", extensao);
        try {
            Files.write(tmp, imagem);
            return ocr(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public boolean ocrDisponivel() {
        try {
            Process p = new ProcessBuilder(props.ocr().tesseract(), "--version").redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String ocr(Path imagem) throws IOException {
        if (!ocrDisponivel()) {
            throw new IOException("OCR indisponível: instale o Tesseract (tesseract-ocr-por) ou configure a chave da IA.");
        }
        Process p = new ProcessBuilder(props.ocr().tesseract(), imagem.toString(), "stdout", "-l", "por", "--psm", "6")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("OCR excedeu o tempo limite");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return saida;
    }
}
