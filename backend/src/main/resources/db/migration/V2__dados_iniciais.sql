-- Valores de referência do documento de requisitos (todos editáveis na tela de Parametrização)

INSERT INTO parametro (chave, valor, descricao, tipo, ordem) VALUES
 ('SALARIO_FIXO', 1500.00, 'Salário fixo mensal do vendedor', 'MOEDA', 1),
 ('TETO_FATURAMENTO_EMPRESA', 230000.00, 'Teto de faturamento mensal da EMPRESA (soma de todos os vendedores)', 'MOEDA', 2),
 ('PERCENTUAL_POS_TETO', 1.0, '% pago a quem NÃO atingiu a meta mínima quando a empresa ultrapassa o teto', 'PERCENTUAL', 3),
 ('PERCENTUAL_ABAIXO_META', 0.0, '% pago a quem NÃO atingiu a meta mínima quando a empresa NÃO ultrapassou o teto', 'PERCENTUAL', 4),
 ('FATOR_MEIA_MARGEM', 0.5, 'Fator aplicado ao % da faixa nas OS "Meia Margem" (0,5 = metade)', 'NUMERO', 5),
 ('DIAS_TOLERANCIA_INADIMPLENCIA', 0, 'Dias após o vencimento sem comprovante para gerar alerta de inadimplência', 'DIAS', 6),
 ('DIAS_ALERTA_VENCIMENTO', 3, 'Dias de antecedência para avisar parcelas a vencer', 'DIAS', 7),
 ('PRAZO_ENTREGA_PADRAO_DIAS', 10, 'Prazo de entrega sugerido ao criar OS', 'DIAS', 8),
 ('DIA_INICIO_SEMANA', 1, 'Dia de início da semana da meta (1=segunda ... 7=domingo)', 'DIAS', 9),
 ('FECHAMENTO_AUTOMATICO', 1, 'Fechar automaticamente os meses anteriores na virada do mês', 'BOOLEANO', 10),
 ('LEITURA_IA_HABILITADA', 1, 'Usar IA (Claude) para ler documentos de OS quando houver chave de API configurada', 'BOOLEANO', 11);

INSERT INTO faixa_comissao (valor_minimo, percentual) VALUES (58000, 1), (70000, 2), (80000, 3);
INSERT INTO faixa_meta_semanal (valor_minimo, bonus) VALUES (20133, 100), (23810, 150), (26690, 200);

INSERT INTO forma_pagamento (nome) VALUES ('Pix'), ('Boleto'), ('Cartão de crédito'), ('Cartão de débito'),
                                          ('Dinheiro'), ('Transferência');
INSERT INTO motivo_estorno (nome) VALUES ('Desistência do cliente'), ('Atraso na entrega'), ('Defeito no produto'),
                                         ('Erro no pedido'), ('Preço/negociação'), ('Outros');
INSERT INTO produto_producao (nome) VALUES ('Bordado'), ('Serigrafia'), ('Camiseta AirFit'), ('Camiseta de pesca'),
                                           ('Wind banner');
