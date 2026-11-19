-- O dia de início da semana passou a ser escolhido em lista (segunda a domingo) na tela de parametrização
UPDATE parametro SET descricao = 'Dia de início da semana da meta' WHERE chave = 'DIA_INICIO_SEMANA';
