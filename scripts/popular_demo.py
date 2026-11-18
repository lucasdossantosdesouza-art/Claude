"""Popula o sistema com dados de demonstração usando apenas a API REST (tokens reais do Keycloak).

Requer um client do Keycloak com "Direct access grants" habilitado — use SOMENTE em desenvolvimento:
    python scripts/popular_demo.py --client teste-local
"""
import argparse
import datetime as dt
import json
import random
import urllib.parse
import urllib.request
import uuid

p = argparse.ArgumentParser()
p.add_argument("--api", default="http://localhost:8080/api")
p.add_argument("--keycloak", default="http://localhost:8180/realms/comissao")
p.add_argument("--client", required=True)
a = p.parse_args()
random.seed(7)


def token(user, senha):
    data = urllib.parse.urlencode({"client_id": a.client, "username": user, "password": senha, "grant_type": "password"}).encode()
    with urllib.request.urlopen(a.keycloak + "/protocol/openid-connect/token", data) as r:
        return json.load(r)["access_token"]


def chamar(tok, metodo, caminho, corpo=None, multipart=None):
    headers = {"Authorization": "Bearer " + tok}
    data = None
    if multipart:
        limite = uuid.uuid4().hex
        partes = []
        for nome, (arquivo, conteudo, tipo) in multipart.items():
            cab = f'--{limite}\r\nContent-Disposition: form-data; name="{nome}"'
            cab += f'; filename="{arquivo}"' if arquivo else ""
            partes.append((cab + f"\r\nContent-Type: {tipo}\r\n\r\n").encode() + conteudo + b"\r\n")
        data = b"".join(partes) + f"--{limite}--\r\n".encode()
        headers["Content-Type"] = "multipart/form-data; boundary=" + limite
    elif corpo is not None:
        data = json.dumps(corpo).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(a.api + caminho, data=data, method=metodo, headers=headers)
    with urllib.request.urlopen(req) as r:
        txt = r.read()
        return json.loads(txt) if txt else None


senhas = {"gestor": "gestor123", "producao": "producao123", "vendedor": "vendedor123", "carla": "vendedor123", "diego": "vendedor123"}
tokens = {u: token(u, s) for u, s in senhas.items()}
for u, t in tokens.items():
    chamar(t, "GET", "/me")  # sincroniza o usuário na API

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
clientes = ["Pesca & Cia Esportes", "Academia Corpo Livre", "Escola Pequeno Saber", "Restaurante Sabor da Serra",
            "Clube Náutico Maré Alta", "Construtora Base Forte", "Igreja Vida Nova", "Auto Peças Turbo"]
produtos = [("Camiseta AirFit personalizada", 39.9), ("Camiseta de pesca UV", 45.0), ("Bordado logo", 8.5),
            ("Wind banner 3m", 380.0), ("Serigrafia 1 cor", 6.0)]
hoje = dt.date.today()
codigo = 48000
criadas = []
for meses_atras in (3, 2, 1, 0):
    ano, mes = hoje.year, hoje.month - meses_atras
    while mes <= 0:
        mes += 12
        ano -= 1
    for vend in ("carla", "diego", "vendedor"):
        for _ in range(random.randint(4, 7)):
            dia = random.randint(1, 28 if meses_atras else max(1, hoje.day - 1))
            emissao = dt.date(ano, mes, dia)
            prod, preco = random.choice(produtos)
            qtd = random.choice([50, 100, 150, 200, 300, 400])
            total = round(qtd * preco, 2)
            n = random.choice([1, 2, 3])
            parcelas = []
            for k in range(n):
                valor = round(total / n, 2) if k < n - 1 else round(total - round(total / n, 2) * (n - 1), 2)
                parcelas.append({"forma": "Pix" if k == 0 else "Boleto", "valor": valor,
                                 "vencimento": (emissao + dt.timedelta(days=30 * k)).isoformat()})
            codigo += 1
            os_ = chamar(tokens[vend], "POST", "/os", {
                "codigo": str(codigo), "dataEmissao": emissao.isoformat(), "hora": "10:00",
                "previsaoEntrega": (emissao + dt.timedelta(days=10)).isoformat(), "servicos": f"{prod} — {qtd} unidades",
                "desconto": 0, "total": total, "prazoPagamentoDias": 30, "meiaMargem": random.random() < .12,
                "cliente": {"nome": random.choice(clientes), "cidade": "Santos", "uf": "SP"},
                "itens": [{"tipo": "PRODUTO", "descricao": prod, "unidade": "UN", "quantidade": qtd, "valorUnitario": preco,
                           "desconto": 0, "valorTotal": total}],
                "parcelas": parcelas})
            criadas.append((vend, os_))
            for p_ in os_["parcelas"]:
                if dt.date.fromisoformat(p_["vencimento"]) <= hoje and random.random() < .85:
                    chamar(tokens[vend], "POST", f"/os/{os_['id']}/parcelas/{p_['id']}/recebimento?dataRecebimento={p_['vencimento']}",
                           multipart={"comprovante": ("comprovante.png", PNG, "image/png")})
            if emissao + dt.timedelta(days=10) < hoje and random.random() < .8:
                entrega = emissao + dt.timedelta(days=random.randint(8, 13))
                chamar(tokens[vend], "POST", f"/os/{os_['id']}/entrega", {"dataEntregaReal": entrega.isoformat()})

# ocorrência de pós-venda e cancelamento com estorno
vend, os_ = criadas[3]
chamar(tokens[vend], "POST", f"/os/{os_['id']}/ocorrencias", {"tipo": "Retrabalho", "descricao": "Bordado com cor errada em 10 peças"})
vend, os_ = criadas[-2]
chamar(tokens[vend], "POST", f"/os/{os_['id']}/cancelamento", multipart={
    "dados": (None, json.dumps({"motivo": "Cliente desistiu do evento", "estorno": True, "motivoEstornoId": 1,
                                "valorEstorno": 150}).encode(), "application/json"),
    "comprovante": ("estorno.png", PNG, "image/png")})

# gestor fecha os meses anteriores (trava a faixa)
for m in chamar(tokens["gestor"], "GET", "/fechamentos")["pendentes"]:
    chamar(tokens["gestor"], "POST", f"/fechamentos/{m}")

# produção: bloqueios e avisos
for d in (3, 7, 11):
    dia = (hoje + dt.timedelta(days=d)).isoformat()
    chamar(tokens["producao"], "PUT", f"/calendario/{dia}", {"produtoIds": [1, 2], "observacao": "Máquina em manutenção"})
chamar(tokens["producao"], "POST", "/avisos", {"tipo": "GERAL", "urgente": True,
       "texto": "Bordadeira em manutenção até sexta — não prometer bordado nesta semana."})
chamar(tokens["producao"], "POST", "/avisos", {"tipo": "VINCULADO", "texto": "Produção de serigrafia lotada",
       "data": (hoje + dt.timedelta(days=5)).isoformat(), "produtoId": 2})
print(f"{len(criadas)} OS criadas. Dados de demonstração prontos.")
