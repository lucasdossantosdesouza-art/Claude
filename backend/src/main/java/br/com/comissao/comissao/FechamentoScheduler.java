package br.com.comissao.comissao;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executa o fechamento automático na virada do mês (e ao iniciar, para recuperar meses perdidos). */
@Component
@RequiredArgsConstructor
public class FechamentoScheduler {

    private final ComissaoService comissaoService;

    @Scheduled(cron = "0 5 0 1 * *", zone = "${app.zona:America/Sao_Paulo}")
    public void viradaDoMes() {
        comissaoService.fechamentoAutomatico();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        comissaoService.fechamentoAutomatico();
    }
}
