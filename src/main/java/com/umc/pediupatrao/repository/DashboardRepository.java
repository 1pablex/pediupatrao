package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Consultas analíticas do dashboard, executadas como pipelines de agregação no próprio MongoDB:
 * o banco filtra, agrupa e soma, e só os totais trafegam para a aplicação.
 *
 * Tratamento de dados (RF07):
 * - valores monetários são convertidos com $convert, aceitando tanto texto (padrão do Spring Data
 *   para BigDecimal) quanto Decimal128, e tratando campos ausentes ou inválidos como zero;
 * - pedidos do modelo antigo (sem data de entrada) ficam fora do período e, portanto, das análises;
 * - itens antigos sem categoria buscam o tipo no cadastro de produtos ou caem em "Sem categoria".
 */
@Repository
public class DashboardRepository {

    public static final String SEM_CATEGORIA = "Sem categoria";

    private static final String PEDIDOS = "pedidos";
    private static final String CANCELADO = StatusPedido.CANCELADO.name();
    private static final Decimal128 ZERO = new Decimal128(BigDecimal.ZERO);

    private final MongoTemplate mongoTemplate;
    private final ZoneId zona;

    public DashboardRepository(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.zona = clock.getZone();
    }

    // ------------------------------------------------------------------ resultados brutos

    /** Totais de um período. tempoMedioSaidaMs é nulo quando nenhum pedido saiu. */
    public record Totais(long pedidos, long cancelados, BigDecimal faturamento, BigDecimal descontos, long itens,
            Double tempoMedioSaidaMs) {

        public static final Totais VAZIO = new Totais(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
    }

    /** Valores agregados por uma chave (dia, hora, status, categoria...). */
    public record Agregado(long pedidos, long cancelados, BigDecimal faturamento, BigDecimal descontos) {

        public Agregado(long pedidos, long cancelados, BigDecimal faturamento) {
            this(pedidos, cancelados, faturamento, BigDecimal.ZERO);
        }
    }

    /** Agregado de uma célula do mapa de calor: dia da semana ISO (1 = segunda ... 7 = domingo) e hora. */
    public record Celula(int diaSemana, int hora, Agregado valores) {
    }

    public record ItemVendido(String produtoId, String nome, String categoria, long quantidade,
            BigDecimal faturamento, long pedidos) {
    }

    public record ResultadoRanking(List<ItemVendido> itens, BigDecimal faturamentoTotal, long quantidadeTotal) {
    }

    public record VendaCategoria(String categoria, long quantidade, BigDecimal faturamento) {
    }

    // ------------------------------------------------------------------ consultas

    public Totais totais(FiltroDashboard filtro) {
        List<Document> pipeline = List.of(
                match(filtro, false),
                new Document("$group", new Document("_id", null)
                        .append("pedidos", new Document("$sum", 1))
                        .append("cancelados", soma(seCancelado(1, 0)))
                        .append("faturamento", soma(seCancelado(ZERO, decimal("$valorTotal"))))
                        .append("descontos", soma(seCancelado(ZERO, decimal("$valorDesconto"))))
                        .append("itens", soma(seCancelado(0, new Document("$sum", "$itens.quantidade"))))
                        // $subtract com data ausente resulta em null, e $avg ignora nulos
                        .append("tempoMedioSaidaMs", new Document("$avg",
                                new Document("$subtract", List.of("$dataHoraSaida", "$dataHoraEntrada"))))));

        List<Document> resultado = agregar(pipeline);
        if (resultado.isEmpty()) {
            return Totais.VAZIO;
        }
        Document d = resultado.get(0);
        Number tempo = d.get("tempoMedioSaidaMs", Number.class);
        return new Totais(inteiro(d, "pedidos"), inteiro(d, "cancelados"), decimal(d, "faturamento"),
                decimal(d, "descontos"), inteiro(d, "itens"), tempo == null ? null : tempo.doubleValue());
    }

    /** Chave: data de início do intervalo no formato yyyy-MM-dd, no fuso da aplicação. */
    public Map<String, Agregado> seriePorPeriodo(FiltroDashboard filtro, Agrupamento agrupamento) {
        Document trunc = new Document("date", "$dataHoraEntrada")
                .append("unit", agrupamento.unidadeMongo())
                .append("timezone", zona.getId());
        if (agrupamento == Agrupamento.SEMANA) {
            trunc.append("startOfWeek", "monday");
        }
        Document chave = new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", new Document("$dateTrunc", trunc))
                .append("timezone", zona.getId()));

        Map<String, Agregado> mapa = new LinkedHashMap<>();
        for (Document d : agregar(List.of(match(filtro, false), agrupadoPor(chave)))) {
            mapa.put(d.getString("_id"), agregado(d));
        }
        return mapa;
    }

    /** Chave: hora do dia (0 a 23) da entrada do pedido. */
    public Map<Integer, Agregado> porHora(FiltroDashboard filtro) {
        Document chave = new Document("$hour", new Document("date", "$dataHoraEntrada").append("timezone", zona.getId()));
        Map<Integer, Agregado> mapa = new LinkedHashMap<>();
        for (Document d : agregar(List.of(match(filtro, true), agrupadoPor(chave)))) {
            mapa.put(d.getInteger("_id"), agregado(d));
        }
        return mapa;
    }

    /** Vendas por dia da semana e hora da entrada, no fuso da aplicação (mapa de calor). */
    public List<Celula> porDiaEHora(FiltroDashboard filtro) {
        Document data = new Document("date", "$dataHoraEntrada").append("timezone", zona.getId());
        Document chave = new Document("dia", new Document("$isoDayOfWeek", data))
                .append("hora", new Document("$hour", data));
        List<Celula> celulas = new ArrayList<>();
        for (Document d : agregar(List.of(match(filtro, true), agrupadoPor(chave)))) {
            Document id = d.get("_id", Document.class);
            celulas.add(new Celula(id.getInteger("dia"), id.getInteger("hora"), agregado(d)));
        }
        return celulas;
    }

    /** Chave: status do pedido. Aqui "faturamento" é o valor total dos pedidos naquele status. */
    public Map<StatusPedido, Agregado> porStatus(FiltroDashboard filtro) {
        Document group = new Document("$group", new Document("_id", "$status")
                .append("pedidos", new Document("$sum", 1))
                .append("faturamento", soma(decimal("$valorTotal"))));
        Map<StatusPedido, Agregado> mapa = new LinkedHashMap<>();
        for (Document d : agregar(List.of(match(filtro, false), group))) {
            StatusPedido status = statusOuNulo(d.getString("_id"));
            if (status != null) {
                mapa.put(status, new Agregado(inteiro(d, "pedidos"), 0, decimal(d, "faturamento")));
            }
        }
        return mapa;
    }

    public ResultadoRanking rankingProdutos(FiltroDashboard filtro, String categoria, CriterioRanking criterio,
            int limite) {
        String campoOrdem = criterio == CriterioRanking.FATURAMENTO ? "faturamento" : "quantidade";
        List<Document> pipeline = new ArrayList<>(itensVendidos(filtro, categoria));
        pipeline.add(new Document("$facet", new Document()
                .append("ranking", List.of(
                        new Document("$group", new Document("_id", "$itens.produtoId")
                                .append("nome", new Document("$last", "$itens.nomeProduto"))
                                .append("categoria", new Document("$last", "$categoria"))
                                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                                .append("faturamento", soma(faturamentoLiquidoItem()))
                                .append("pedidos", new Document("$addToSet", "$_id"))),
                        new Document("$addFields", new Document("pedidos", new Document("$size", "$pedidos"))),
                        new Document("$sort", new Document(campoOrdem, -1).append("nome", 1)),
                        new Document("$limit", limite)))
                .append("total", List.of(
                        new Document("$group", new Document("_id", null)
                                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                                .append("faturamento", soma(faturamentoLiquidoItem())))))));

        Document resultado = agregar(pipeline).get(0);
        List<ItemVendido> itens = new ArrayList<>();
        for (Document d : resultado.getList("ranking", Document.class)) {
            itens.add(new ItemVendido(d.getString("_id"), textoOu(d.getString("nome"), "Produto sem nome"),
                    d.getString("categoria"), inteiro(d, "quantidade"), decimal(d, "faturamento"),
                    inteiro(d, "pedidos")));
        }
        List<Document> total = resultado.getList("total", Document.class);
        return total.isEmpty()
                ? new ResultadoRanking(itens, BigDecimal.ZERO, 0)
                : new ResultadoRanking(itens, decimal(total.get(0), "faturamento"), inteiro(total.get(0), "quantidade"));
    }

    public List<VendaCategoria> vendasPorCategoria(FiltroDashboard filtro) {
        List<Document> pipeline = new ArrayList<>(itensVendidos(filtro, null));
        pipeline.add(new Document("$group", new Document("_id", "$categoria")
                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                .append("faturamento", soma(faturamentoLiquidoItem()))));
        pipeline.add(new Document("$sort", new Document("faturamento", -1).append("_id", 1)));

        List<VendaCategoria> lista = new ArrayList<>();
        for (Document d : agregar(pipeline)) {
            lista.add(new VendaCategoria(d.getString("_id"), inteiro(d, "quantidade"), decimal(d, "faturamento")));
        }
        return lista;
    }

    /** Pedidos do período, mais recentes primeiro (consulta tipada pelo mapeamento do Spring Data). */
    public List<Pedido> listarPedidos(FiltroDashboard filtro, int pagina, int tamanho) {
        Query query = new Query(criteria(filtro))
                .with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"))
                .skip((long) pagina * tamanho)
                .limit(tamanho);
        return mongoTemplate.find(query, Pedido.class);
    }

    public long contarPedidos(FiltroDashboard filtro) {
        return mongoTemplate.count(new Query(criteria(filtro)), Pedido.class);
    }

    /** Categorias do cadastro de produtos e as já registradas em pedidos, sem repetição e em ordem alfabética. */
    public List<String> categorias() {
        List<String> encontradas = new ArrayList<>();
        mongoTemplate.getCollection("produtos").distinct("tipo", String.class).into(encontradas);
        mongoTemplate.getCollection(PEDIDOS).distinct("itens.categoria", String.class).into(encontradas);
        TreeSet<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        encontradas.stream().filter(c -> c != null && !c.isBlank()).forEach(categorias::add);
        return List.copyOf(categorias);
    }

    // ------------------------------------------------------------------ blocos dos pipelines

    /** $match do período e dos filtros. Com excluirCancelados, só entram vendas efetivas. */
    Document match(FiltroDashboard filtro, boolean excluirCancelados) {
        Document m = new Document("dataHoraEntrada", new Document("$gte", data(filtro.inicio()))
                .append("$lt", data(filtro.fimExclusivo())));

        List<String> status = filtro.status().stream()
                .map(Enum::name)
                .filter(s -> !excluirCancelados || !s.equals(CANCELADO))
                .toList();
        if (!filtro.status().isEmpty()) {
            m.append("status", new Document("$in", status)); // lista vazia: nenhum pedido
        } else if (excluirCancelados) {
            m.append("status", new Document("$ne", CANCELADO));
        }
        if (filtro.tipoEntrega() != null) {
            m.append("tipoEntrega", filtro.tipoEntrega().name());
        }
        return new Document("$match", m);
    }

    /** Mesmo filtro do $match, para consultas tipadas. */
    private Criteria criteria(FiltroDashboard filtro) {
        Criteria c = Criteria.where("dataHoraEntrada").gte(filtro.inicio()).lt(filtro.fimExclusivo());
        if (!filtro.status().isEmpty()) {
            c = c.and("status").in(filtro.status());
        }
        if (filtro.tipoEntrega() != null) {
            c = c.and("tipoEntrega").is(filtro.tipoEntrega());
        }
        return c;
    }

    /**
     * Um documento por item vendido (pedidos não cancelados), com a categoria resolvida
     * e o fator de desconto do pedido, para ratear o desconto entre os itens.
     */
    private List<Document> itensVendidos(FiltroDashboard filtro, String categoria) {
        List<Document> etapas = new ArrayList<>();
        etapas.add(match(filtro, true));
        etapas.add(new Document("$project", new Document("itens", 1)
                .append("fatorDesconto", new Document("$divide", List.of(
                        new Document("$subtract", List.of(100, decimal("$percentualDesconto"))), 100)))));
        etapas.add(new Document("$unwind", "$itens"));
        etapas.add(new Document("$lookup", new Document("from", "produtos")
                .append("let", new Document("pid", "$itens.produtoId"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$eq", List.of("$_id",
                                new Document("$convert", new Document("input", "$$pid").append("to", "objectId")
                                        .append("onError", null).append("onNull", null)))))),
                        new Document("$project", new Document("tipo", 1))))
                .append("as", "produto")));
        etapas.add(new Document("$addFields", new Document("categoria", new Document("$ifNull", List.of(
                "$itens.categoria", new Document("$first", "$produto.tipo"), SEM_CATEGORIA)))));
        if (categoria != null) {
            etapas.add(new Document("$match", new Document("categoria", categoria)));
        }
        return etapas;
    }

    /** subtotal do item x (100 - % de desconto do pedido) / 100 */
    private static Document faturamentoLiquidoItem() {
        return new Document("$multiply", List.of(decimal("$itens.subtotal"), "$fatorDesconto"));
    }

    private static Document agrupadoPor(Object chave) {
        return new Document("$group", new Document("_id", chave)
                .append("pedidos", soma(seCancelado(0, 1)))
                .append("cancelados", soma(seCancelado(1, 0)))
                .append("faturamento", soma(seCancelado(ZERO, decimal("$valorTotal"))))
                .append("descontos", soma(seCancelado(ZERO, decimal("$valorDesconto")))));
    }

    private static Document seCancelado(Object entao, Object senao) {
        return new Document("$cond", List.of(new Document("$eq", List.of("$status", CANCELADO)), entao, senao));
    }

    private static Document soma(Object expressao) {
        return new Document("$sum", expressao);
    }

    /** Converte para Decimal128 aceitando texto ou número; ausente ou inválido vira zero. */
    private static Document decimal(String campo) {
        return new Document("$convert", new Document("input", campo).append("to", "decimal")
                .append("onError", ZERO).append("onNull", ZERO));
    }

    // ------------------------------------------------------------------ conversões

    private List<Document> agregar(List<Document> pipeline) {
        return mongoTemplate.getCollection(PEDIDOS).aggregate(pipeline).into(new ArrayList<>());
    }

    private Date data(LocalDateTime dataHora) {
        return Date.from(dataHora.atZone(zona).toInstant());
    }

    private static Agregado agregado(Document d) {
        return new Agregado(inteiro(d, "pedidos"), inteiro(d, "cancelados"), decimal(d, "faturamento"),
                decimal(d, "descontos"));
    }

    private static long inteiro(Document d, String campo) {
        Object valor = d.get(campo);
        if (valor instanceof Decimal128 dec) {
            return dec.bigDecimalValue().longValue();
        }
        return valor instanceof Number n ? n.longValue() : 0;
    }

    static BigDecimal decimal(Document d, String campo) {
        Object valor = d.get(campo);
        if (valor instanceof Decimal128 dec) {
            return dec.bigDecimalValue();
        }
        if (valor instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    private static StatusPedido statusOuNulo(String valor) {
        try {
            return valor == null ? null : StatusPedido.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            return null; // status de dados antigos, fora do modelo atual
        }
    }

    private static String textoOu(String texto, String padrao) {
        return texto == null || texto.isBlank() ? padrao : texto;
    }
}
