package br.com.comissao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Storage storage, Ia ia, Ocr ocr, String zona) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String diretorio) {
    }

    public record Ia(String apiKey, String modelo) {
        public boolean habilitada() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Ocr(String tesseract) {
    }
}
