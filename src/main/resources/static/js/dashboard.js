/*
 * Dashboard administrativo (RF06): todo o conteúdo vem da API /api/v1/dashboard.
 * A página usa a sessão de quem entrou no sistema; o cabeçalho X-Requested-With faz a API
 * responder 401 sem abrir a janela de login do navegador quando a sessão expira.
 *
 * Interações: os indicadores trocam a métrica do gráfico de evolução; a categoria clicada filtra o ranking;
 * o status clicado filtra a lista de pedidos; o gráfico e o mapa de calor mostram detalhes ao passar o mouse.
 */
(function () {
    'use strict';

    const API = '/api/v1/dashboard';
    const INTERVALO_ATUALIZACAO_MS = 5 * 60 * 1000; // 5 minutos
    const TAMANHO_PAGINA = 10;

    const raiz = document.getElementById('dashboard');
    const estilo = getComputedStyle(raiz);
    const cor = nome => estilo.getPropertyValue(nome).trim();
    const COR = {
        roxo: cor('--dash-roxo'),
        laranja: cor('--dash-laranja'),
        anterior: cor('--dash-anterior'),
        grade: cor('--dash-grade'),
        mudo: cor('--dash-mudo')
    };
    // Rampa sequencial (um só tom, do claro ao escuro) para o mapa de calor
    const RAMPA = ['#F2F0FA', '#DCD6F7', '#B9AEF0', '#8F80E6', '#6655D9', '#3E2FB0'];
    // Cores de status: cada etapa com claridade diferente, sempre acompanhadas do nome
    const COR_STATUS = {
        RECEBIDO: '#B9B4C9', EM_PREPARACAO: '#F2B755', PRONTO: '#E07B1A', SAIU_PARA_ENTREGA: '#9C90EE',
        RETIRADO: '#6655D9', FINALIZADO: '#3E2FB0', CANCELADO: '#C2413A'
    };
    const ETAPAS = [
        ['RECEBIDO', 'Recebido', '#F1EFF7', '#3B3748'], ['EM_PREPARACAO', 'Em preparação', '#FCEBD2', '#8A4A00'],
        ['PRONTO', 'Pronto', '#E9E5FC', '#3424A3'], ['SAIU_PARA_ENTREGA', 'Saiu para entrega', '#DCD6F7', '#2B1E8F']
    ];
    const METRICAS = {
        faturamento: { nome: 'faturamento', anterior: 'faturamentoAnterior' },
        pedidos: { nome: 'pedidos', anterior: 'pedidosAnterior' },
        ticketMedio: { nome: 'ticket médio', anterior: 'ticketMedioAnterior' }
    };

    const fmtMoeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const fmtCompacto = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 });
    const fmtInteiro = new Intl.NumberFormat('pt-BR');
    const fmtDecimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });
    const moeda = v => fmtMoeda.format(Number(v || 0));
    const moedaEixo = v => fmtCompacto.format(Number(v || 0));
    const inteiro = v => fmtInteiro.format(Number(v || 0));
    const pct = v => v == null ? '-' : fmtDecimal.format(Number(v)) + '%';
    const formatoMetrica = m => (m === 'pedidos' ? inteiro : moeda);

    const $ = id => document.getElementById(id);
    const campos = {
        periodo: $('fPeriodo'), inicio: $('fInicio'), fim: $('fFim'), status: $('fStatus'),
        btnStatus: $('btnStatus'), tipo: $('fTipo'), categoria: $('fCategoria'), agrupamento: $('fAgrupamento')
    };

    const estado = {
        hoje: null, periodo: '30', pagina: 0, metrica: 'faturamento', ordenarPor: 'QUANTIDADE',
        statusLista: null, nomesStatus: {}, serie: null, carregando: false, timer: null
    };
    let grafico = null;

    Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
    Chart.defaults.color = COR.mudo;
    Chart.defaults.plugins.legend.display = false;
    Chart.defaults.maintainAspectRatio = false;

    // ------------------------------------------------------------------ utilidades

    function esc(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : String(texto);
        return div.innerHTML;
    }

    function dataBr(iso) {
        if (!iso) return '-';
        const [a, m, d] = iso.substring(0, 10).split('-');
        return `${d}/${m}/${a}`;
    }

    function diaSemana(iso) {
        return new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'long' });
    }

    function dataHoraCurta(iso) {
        if (!iso) return '-';
        const d = new Date(iso);
        return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' '
            + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }

    function somarDias(iso, dias) {
        const d = new Date(iso + 'T12:00:00');
        d.setDate(d.getDate() + dias);
        return d.toISOString().substring(0, 10);
    }

    function painel(nome) {
        return raiz.querySelector(`[data-painel="${nome}"]`);
    }

    function corpo(nome) {
        return raiz.querySelector(`[data-corpo="${nome}"]`);
    }

    function definir(el, campo, texto) {
        el.querySelectorAll(`[data-campo="${campo}"]`).forEach(alvo => { alvo.textContent = texto; });
    }

    function mostrar(el, seletor, visivel) {
        el.querySelectorAll(seletor).forEach(e => e.classList.toggle('d-none', !visivel));
    }

    function tabela(el, cabecalho, linhas) {
        el.querySelector('.tabela-dados').innerHTML = '<table><thead><tr>'
            + cabecalho.map(c => `<th>${esc(c)}</th>`).join('') + '</tr></thead><tbody>'
            + linhas.map(l => '<tr>' + l.map(c => `<td>${esc(c)}</td>`).join('') + '</tr>').join('')
            + '</tbody></table>';
    }

    /** Mini gráfico de tendência para os indicadores. */
    function sparkline(svg, valores, ativo) {
        if (!svg) return;
        if (!valores.length) { svg.innerHTML = ''; return; }
        const max = Math.max(...valores), min = Math.min(...valores);
        const n = Math.max(valores.length - 1, 1);
        const pontos = valores.map((v, i) =>
            `${(i / n * 100).toFixed(1)},${(30 - (v - min) / ((max - min) || 1) * 28).toFixed(1)}`).join(' ');
        svg.innerHTML = `<polyline points="${pontos}" fill="none" stroke="${ativo ? COR.roxo : '#A7A1BA'}"
            stroke-width="2" vector-effect="non-scaling-stroke" stroke-linejoin="round"></polyline>`;
    }

    class ErroApi extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    async function api(caminho, params) {
        const url = new URL(API + caminho, window.location.origin);
        (params || []).forEach(([k, v]) => url.searchParams.append(k, v));
        let resposta;
        try {
            resposta = await fetch(url, {
                headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                credentials: 'same-origin'
            });
        } catch (e) {
            throw new ErroApi(0, 'Sem conexão com o servidor. Verifique a rede e tente novamente.');
        }
        if (!resposta.ok) {
            let mensagem = `Não foi possível carregar os dados (HTTP ${resposta.status}).`;
            try {
                const problema = await resposta.json();
                if (problema.detail) mensagem = problema.detail;
            } catch (e) { /* resposta sem corpo JSON */ }
            if (resposta.status === 401) mensagem = 'Sessão expirada.';
            if (resposta.status === 403) mensagem = 'Seu perfil não tem acesso ao dashboard.';
            throw new ErroApi(resposta.status, mensagem);
        }
        return resposta.json();
    }

    // ------------------------------------------------------------------ filtros (RF05)

    function statusSelecionados() {
        return [...campos.status.querySelectorAll('input:checked')].map(i => i.value);
    }

    function atualizarRotuloStatus() {
        const marcados = [...campos.status.querySelectorAll('input:checked')];
        campos.btnStatus.textContent = marcados.length === 0 ? 'Status: todos'
            : marcados.length === 1 ? 'Status: ' + marcados[0].dataset.descricao : `Status: ${marcados.length} selecionados`;
    }

    function marcarPeriodo() {
        campos.periodo.querySelectorAll('[data-periodo]').forEach(b =>
            b.setAttribute('aria-pressed', String(b.dataset.periodo === estado.periodo)));
    }

    function aplicarPeriodo(preset) {
        estado.periodo = preset;
        marcarPeriodo();
        const hoje = estado.hoje;
        if (preset === 'custom' || !hoje) return;
        if (preset === 'mes') {
            campos.inicio.value = hoje.substring(0, 8) + '01';
        } else {
            const dias = Number(preset);
            campos.inicio.value = somarDias(hoje, dias === 0 ? 0 : -(dias - 1));
        }
        campos.fim.value = hoje;
    }

    function paramsFiltro() {
        const p = [];
        if (campos.inicio.value) p.push(['dataInicio', campos.inicio.value]);
        if (campos.fim.value) p.push(['dataFim', campos.fim.value]);
        statusSelecionados().forEach(s => p.push(['status', s]));
        if (campos.tipo.value) p.push(['tipoEntrega', campos.tipo.value]);
        return p;
    }

    /** Validação no navegador para resposta imediata; a API valida de novo (regra no backend). */
    function validarFiltros() {
        const erro = $('erroFiltros');
        let mensagem = '';
        if (campos.inicio.value && campos.fim.value) {
            if (campos.inicio.value > campos.fim.value) {
                mensagem = 'A data inicial deve ser anterior ou igual à data final.';
            } else {
                const dias = (new Date(campos.fim.value) - new Date(campos.inicio.value)) / 86400000 + 1;
                if (dias > 366) mensagem = 'O período pode ter no máximo 366 dias.';
            }
        }
        erro.textContent = mensagem;
        erro.classList.toggle('d-none', !mensagem);
        return !mensagem;
    }

    /** Etiquetas removíveis dos filtros cruzados (categoria do ranking e status da lista). */
    function desenharChips() {
        const chips = [];
        if (campos.categoria.value) chips.push(['categoria', 'Categoria: ' + campos.categoria.value]);
        if (estado.statusLista) chips.push(['status', 'Lista: ' + (estado.nomesStatus[estado.statusLista] || estado.statusLista)]);
        $('chipsAtivos').innerHTML = chips.map(([tipo, texto]) =>
            `<button type="button" class="dash-chip-ativo" data-limpar="${tipo}" aria-label="Remover filtro ${esc(texto)}">
                ${esc(texto)} <i class="bi bi-x-lg" aria-hidden="true"></i></button>`).join('');
        $('rankTitulo').textContent = campos.categoria.value ? ': ' + campos.categoria.value : '';
        $('pedTitulo').textContent = estado.statusLista ? ': ' + (estado.nomesStatus[estado.statusLista] || '') : '';
    }

    /** Mantém os filtros na URL: recarregar ou compartilhar o link reabre a mesma visão. */
    function sincronizarUrl() {
        const p = new URLSearchParams(paramsFiltro());
        if (estado.periodo !== '30') p.set('periodo', estado.periodo);
        if (campos.categoria.value) p.set('categoria', campos.categoria.value);
        if (campos.agrupamento.value !== 'DIA') p.set('agrupamento', campos.agrupamento.value);
        if (estado.ordenarPor !== 'QUANTIDADE') p.set('ordenarPor', estado.ordenarPor);
        if (estado.metrica !== 'faturamento') p.set('metrica', estado.metrica);
        if (estado.statusLista) p.set('statusLista', estado.statusLista);
        const query = p.toString();
        history.replaceState(null, '', query ? `?${query}` : window.location.pathname);
    }

    function restaurarDaUrl() {
        const p = new URLSearchParams(window.location.search);
        aplicarPeriodo(p.get('periodo') || (p.get('dataInicio') ? 'custom' : '30'));
        if (p.get('dataInicio')) campos.inicio.value = p.get('dataInicio');
        if (p.get('dataFim')) campos.fim.value = p.get('dataFim');
        const status = p.getAll('status');
        campos.status.querySelectorAll('input').forEach(i => { i.checked = status.includes(i.value); });
        atualizarRotuloStatus();
        if (p.get('tipoEntrega')) campos.tipo.value = p.get('tipoEntrega');
        if (p.get('categoria')) campos.categoria.value = p.get('categoria');
        if (p.get('agrupamento')) campos.agrupamento.value = p.get('agrupamento');
        if (p.get('ordenarPor') === 'FATURAMENTO') estado.ordenarPor = 'FATURAMENTO';
        if (METRICAS[p.get('metrica')]) estado.metrica = p.get('metrica');
        if (p.get('statusLista')) estado.statusLista = p.get('statusLista');
        marcarMetrica();
        marcarOrdem();
        desenharChips();
    }

    async function carregarOpcoes() {
        const opcoes = await api('/filtros');
        estado.hoje = opcoes.dataFimPadrao;
        opcoes.status.forEach(s => { estado.nomesStatus[s.valor] = s.descricao; });
        campos.status.innerHTML = opcoes.status.map(s => `
            <div class="form-check">
                <input class="form-check-input" type="checkbox" id="st-${esc(s.valor)}" value="${esc(s.valor)}"
                       data-descricao="${esc(s.descricao)}">
                <label class="form-check-label" for="st-${esc(s.valor)}">${esc(s.descricao)}</label>
            </div>`).join('');
        opcoes.tiposEntrega.forEach(t => campos.tipo.add(new Option(t.descricao, t.valor)));
        opcoes.categorias.forEach(c => campos.categoria.add(new Option(c, c)));
    }

    // ------------------------------------------------------------------ painéis

    async function carregarPainel(nome, carregar) {
        const el = painel(nome);
        el.classList.add('atualizando');
        mostrar(el, '.painel-erro', false);
        try {
            return await carregar(el);
        } catch (e) {
            const alvo = el.querySelector('.painel-erro');
            alvo.innerHTML = `<div class="alert alert-danger py-2 mb-0 small d-flex justify-content-between align-items-center">
                <span><i class="bi bi-exclamation-triangle me-1"></i>${esc(e.message)}</span>
                <button type="button" class="btn btn-sm btn-outline-danger">Tentar novamente</button></div>`;
            alvo.querySelector('button').addEventListener('click', () => recarregar(nome));
            alvo.classList.remove('d-none');
            throw e;
        } finally {
            el.classList.remove('atualizando');
        }
    }

    // RF01
    async function carregarResumo(el) {
        const r = await api('/resumo', paramsFiltro());
        const d = r.dados;
        definir(el, 'faturamento', moeda(d.faturamento));
        definir(el, 'pedidosValidos', inteiro(d.pedidosValidos));
        definir(el, 'ticketMedio', moeda(d.ticketMedio));
        definir(el, 'taxaCancelamento', pct(d.taxaCancelamento));
        definir(el, 'pedidosCancelados', `${inteiro(d.pedidosCancelados)} de ${inteiro(d.totalPedidos)}`);
        definir(el, 'totalDescontos', moeda(d.totalDescontos));
        definir(el, 'itensVendidos', `${inteiro(d.itensVendidos)} itens vendidos`);
        definir(el, 'periodo', `${inteiro(r.dados.totalPedidos)} pedidos`);
        $('periodoTexto').textContent = `${dataBr(r.filtros.dataInicio)} a ${dataBr(r.filtros.dataFim)}, `
            + `comparado a ${dataBr(d.comparativo.dataInicio)} a ${dataBr(d.comparativo.dataFim)}`;

        el.querySelectorAll('[data-delta]').forEach(alvo => {
            const v = d.comparativo[alvo.dataset.delta];
            if (v == null) {
                alvo.textContent = 'sem base de comparação';
                return;
            }
            const n = Number(v);
            const bom = (alvo.dataset.bom === 'alta') === (n > 0);
            const seta = n === 0 ? '=' : (n > 0 ? '▲' : '▼');
            alvo.innerHTML = `<span class="${n === 0 ? '' : (bom ? 'bom' : 'ruim')}">${seta} ${esc(pct(Math.abs(n)))}</span> vs anterior`;
        });
        return r;
    }

    // RF02: evolução com o período anterior
    async function carregarSerie(el) {
        const r = await api('/vendas/serie', [...paramsFiltro(), ['agrupamento', campos.agrupamento.value]]);
        estado.serie = r.dados;
        desenharSerie();
        desenharSparklines();
        tabela(el, ['Período', 'Faturamento', 'Anterior', 'Pedidos', 'Anterior', 'Cancelados', 'Ticket médio'],
            r.dados.pontos.map(p => [dataBr(p.periodo), moeda(p.faturamento), moeda(p.faturamentoAnterior),
                inteiro(p.pedidos), inteiro(p.pedidosAnterior), inteiro(p.cancelados), moeda(p.ticketMedio)]));
        return r;
    }

    function desenharSparklines() {
        const pontos = estado.serie ? estado.serie.pontos : [];
        ['faturamento', 'pedidos', 'ticketMedio', 'cancelados', 'descontos'].forEach(campo => {
            sparkline(raiz.querySelector(`[data-spark="${campo}"]`), pontos.map(p => Number(p[campo] || 0)),
                campo === estado.metrica);
        });
    }

    /** Linha vertical que acompanha o mouse no gráfico de evolução. */
    const linhaGuia = {
        id: 'linhaGuia',
        afterDatasetsDraw(chart) {
            const ativos = chart.tooltip && chart.tooltip.getActiveElements();
            if (!ativos || !ativos.length) return;
            const x = ativos[0].element.x;
            const ctx = chart.ctx;
            ctx.save();
            ctx.beginPath();
            ctx.moveTo(x, chart.chartArea.top);
            ctx.lineTo(x, chart.chartArea.bottom);
            ctx.lineWidth = 1;
            ctx.strokeStyle = '#CFCADF';
            ctx.stroke();
            ctx.restore();
        }
    };

    /** Tooltip escuro em HTML: valor do intervalo, valor do período anterior e variação. */
    function tooltipSerie(contexto) {
        const tip = $('tipSerie');
        const t = contexto.tooltip;
        if (!t || t.opacity === 0 || !t.dataPoints || !t.dataPoints.length) {
            tip.classList.add('d-none');
            return;
        }
        const i = t.dataPoints[0].dataIndex;
        const p = estado.serie.pontos[i];
        const m = METRICAS[estado.metrica];
        const formato = formatoMetrica(estado.metrica);
        const atual = Number(p[estado.metrica]);
        const anterior = Number(p[m.anterior]);
        const variacao = anterior ? (atual - anterior) / anterior * 100 : null;
        const rotulo = campos.agrupamento.value === 'DIA'
            ? `${dataBr(p.periodo).substring(0, 5)} · ${diaSemana(p.periodo)}`
            : campos.agrupamento.value === 'SEMANA' ? `Semana de ${dataBr(p.periodo).substring(0, 5)}` : p.rotulo;
        tip.innerHTML = `<span class="tip-dia">${esc(rotulo)}</span>
            <span class="tip-valor">${esc(formato(atual))}</span>
            <span class="tip-anterior">Antes: ${esc(formato(anterior))}${p.periodoAnterior ? ' (' + esc(dataBr(p.periodoAnterior).substring(0, 5)) + ')' : ''}</span>
            ${variacao == null ? '' : `<span class="${variacao >= 0 ? 'tip-sobe' : 'tip-desce'}">${variacao >= 0 ? '▲' : '▼'} ${esc(pct(Math.abs(variacao)))}</span>`}`;
        tip.classList.remove('d-none');
        const largura = contexto.chart.width;
        const x = t.caretX;
        tip.style.left = x > largura * 0.65 ? '' : `${x + 14}px`;
        tip.style.right = x > largura * 0.65 ? `${largura - x + 14}px` : '';
    }

    function desenharSerie() {
        const el = painel('serie');
        const pontos = estado.serie ? estado.serie.pontos : [];
        $('nomeMetrica').textContent = METRICAS[estado.metrica].nome;
        const vazio = pontos.every(p => Number(p.pedidos) === 0 && Number(p.cancelados) === 0
            && Number(p.pedidosAnterior) === 0);
        mostrar(el, '.painel-vazio', vazio);
        mostrar(el, '.grafico', !vazio);
        if (vazio) return;

        const metrica = estado.metrica;
        const campoAnterior = METRICAS[metrica].anterior;
        const formatoEixo = metrica === 'pedidos' ? inteiro : moedaEixo;
        const config = {
            type: 'line',
            data: {
                labels: pontos.map(p => p.rotulo),
                datasets: [{
                    label: 'Período atual',
                    data: pontos.map(p => Number(p[metrica])),
                    borderColor: COR.roxo,
                    borderWidth: 2.5,
                    fill: true,
                    backgroundColor: ctx => {
                        const area = ctx.chart.chartArea;
                        if (!area) return 'rgba(76, 60, 201, 0.12)';
                        const g = ctx.chart.ctx.createLinearGradient(0, area.top, 0, area.bottom);
                        g.addColorStop(0, 'rgba(76, 60, 201, 0.24)');
                        g.addColorStop(1, 'rgba(76, 60, 201, 0)');
                        return g;
                    },
                    tension: 0.3,
                    pointRadius: 0,
                    pointHoverRadius: 7,
                    pointHoverBorderWidth: 3,
                    pointHoverBorderColor: '#FFFFFF',
                    pointHoverBackgroundColor: COR.roxo,
                    order: 1
                }, {
                    label: 'Período anterior',
                    data: pontos.map(p => Number(p[campoAnterior])),
                    borderColor: COR.anterior,
                    borderWidth: 2,
                    fill: false,
                    tension: 0.3,
                    pointRadius: 0,
                    pointHoverRadius: 5,
                    pointHoverBorderWidth: 2,
                    pointHoverBorderColor: '#FFFFFF',
                    pointHoverBackgroundColor: COR.anterior,
                    order: 2
                }]
            },
            options: {
                animation: { duration: 400 },
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: { grid: { display: false }, border: { color: COR.grade }, ticks: { maxRotation: 0, autoSkipPadding: 16 } },
                    y: { beginAtZero: true, grid: { color: COR.grade }, border: { display: false },
                        ticks: { callback: v => formatoEixo(v), maxTicksLimit: 6 } }
                },
                plugins: { tooltip: { enabled: false, external: tooltipSerie } }
            },
            plugins: [linhaGuia]
        };
        if (grafico) {
            grafico.data = config.data;
            grafico.options = config.options;
            grafico.update();
        } else {
            grafico = new Chart($('gSerie'), config);
        }
    }

    async function carregarCategoria(el) {
        const r = await api('/vendas/por-categoria', paramsFiltro());
        desenharCategorias(r.dados);
        return r;
    }

    function desenharCategorias(lista) {
        const el = painel('categoria');
        el._lista = lista;
        mostrar(el, '.painel-vazio', lista.length === 0);
        const selecionada = campos.categoria.value;
        const alvo = corpo('categorias');
        alvo.classList.toggle('com-selecao', !!selecionada);
        alvo.innerHTML = lista.map(c => `
            <button type="button" class="dash-categoria" data-categoria="${esc(c.categoria)}"
                    aria-pressed="${c.categoria === selecionada}">
                <span class="dash-categoria-linha">
                    <span class="dash-categoria-nome">${esc(c.categoria)}</span>
                    <span class="dash-categoria-valor">${esc(moeda(c.faturamento))} · ${esc(pct(c.participacao))}</span>
                </span>
                <span class="dash-trilho"><span style="width:${Math.max(Number(c.participacao), 1.5)}%"></span></span>
            </button>`).join('');
    }

    // RF02: mapa de calor dia x hora
    async function carregarCalor(el) {
        const r = await api('/vendas/por-dia-hora', paramsFiltro());
        const celulas = r.dados;
        const max = Math.max(0, ...celulas.map(c => Number(c.pedidos)));
        mostrar(el, '.painel-vazio', max === 0);
        mostrar(el, '.dash-calor-rolagem', max > 0);
        if (max === 0) {
            $('infoCalor').textContent = 'Sem vendas no período.';
            return r;
        }
        // mostra só a faixa de horas com movimento
        const horasComVenda = celulas.filter(c => c.pedidos > 0).map(c => c.hora);
        const deHora = Math.min(...horasComVenda), ateHora = Math.max(...horasComVenda);
        const horas = [];
        for (let h = deHora; h <= ateHora; h++) horas.push(h);
        const dias = [...new Map(celulas.map(c => [c.diaSemana, c.dia])).entries()];
        const pico = celulas.reduce((a, c) => (c.pedidos > a.pedidos ? c : a));
        const porChave = new Map(celulas.map(c => [c.diaSemana * 100 + c.hora, c]));

        const grade = corpo('calor');
        grade.style.gridTemplateColumns = `44px repeat(${horas.length}, minmax(0, 1fr))`;
        let html = '<span></span>' + horas.map(h => `<span class="hora">${h}h</span>`).join('');
        dias.forEach(([numero, nome]) => {
            html += `<span class="dia">${esc(nome)}</span>`;
            horas.forEach(h => {
                const c = porChave.get(numero * 100 + h);
                const nivel = c.pedidos === 0 ? 0 : Math.min(5, 1 + Math.floor(c.pedidos / max * 4.999));
                const texto = `${nome}, ${h}h: ${inteiro(c.pedidos)} pedidos (${moeda(c.faturamento)})`;
                html += `<span class="cel${c === pico ? ' pico' : ''}" tabindex="0" title="${esc(texto)}" aria-label="${esc(texto)}"
                    data-info="${esc(texto)}" style="background:${RAMPA[nivel]};color:${nivel >= 3 ? '#FFFFFF' : '#3B3748'}">${nivel >= 3 ? c.pedidos : ''}</span>`;
            });
        });
        grade.innerHTML = html;
        el._textoPico = `Pico: ${pico.dia} às ${pico.hora}h com ${inteiro(pico.pedidos)} pedidos (contorno laranja). Passe o mouse em uma célula.`;
        $('infoCalor').textContent = el._textoPico;
        tabela(el, ['Dia', 'Hora', 'Pedidos', 'Faturamento'],
            celulas.filter(c => c.pedidos > 0).map(c => [c.dia, `${c.hora}h`, inteiro(c.pedidos), moeda(c.faturamento)]));
        return r;
    }

    // RF03: operação agora e status
    async function carregarSituacao(el) {
        const r = await api('/pedidos/situacao', paramsFiltro());
        const s = r.dados;
        const porStatus = Object.fromEntries(s.porStatus.map(c => [c.status, c]));
        corpo('fluxo').innerHTML = ETAPAS.map(([codigo, nome, fundo, tinta]) => `
            <div class="dash-etapa" style="background:${fundo};color:${tinta}">
                <strong>${inteiro(porStatus[codigo] ? porStatus[codigo].quantidade : 0)}</strong><span>${esc(nome)}</span>
            </div>`).join('');
        definir(el, 'tempoMedio', s.tempoMedioAteSaidaMinutos == null ? '-'
            : `${fmtDecimal.format(Number(s.tempoMedioAteSaidaMinutos))} min`);
        definir(el, 'totalStatus', `Todos os pedidos do período (${inteiro(s.total)})`);
        mostrar(el, '.painel-vazio', s.total === 0);
        corpo('segmentos').innerHTML = s.porStatus.filter(c => c.quantidade > 0).map(c =>
            `<span style="background:${COR_STATUS[c.status]};flex:${Math.max(Number(c.percentual), 1.2)} 0 0"
                   title="${esc(c.descricao)}: ${inteiro(c.quantidade)}"></span>`).join('');
        corpo('statusLista').innerHTML = s.porStatus.map(c => `
            <button type="button" class="dash-status-item" data-status="${esc(c.status)}"
                    aria-pressed="${c.status === estado.statusLista}">
                <span class="nome"><span class="dash-swatch" style="background:${COR_STATUS[c.status]}"></span>${esc(c.descricao)}</span>
                <span class="qtd">${inteiro(c.quantidade)} <span class="dash-mudo">(${esc(pct(c.percentual))})</span></span>
            </button>`).join('');
        return r;
    }

    async function carregarPedidos(el) {
        const filtros = estado.statusLista
            ? [...paramsFiltro().filter(([k]) => k !== 'status'), ['status', estado.statusLista]]
            : paramsFiltro();
        const r = await api('/pedidos', [...filtros, ['pagina', estado.pagina], ['tamanho', TAMANHO_PAGINA]]);
        const p = r.dados;
        corpo('pedidos').innerHTML = p.conteudo.map(x => `
            <tr>
                <td class="text-nowrap">${esc(dataHoraCurta(x.dataHoraEntrada))}</td>
                <td>${esc(x.nomeCliente || '-')}</td>
                <td>${x.tipoEntrega === 'RETIRADA' ? 'Retirada' : 'Entrega'}</td>
                <td><span class="dash-badge"><span class="ponto" style="background:${COR_STATUS[x.status] || '#B9B4C9'}"></span>${esc(x.statusDescricao || x.status)}</span></td>
                <td class="text-end fw-semibold">${esc(moeda(x.valorTotal))}</td>
                <td class="text-end"><a href="/pedidos/${encodeURIComponent(x.id)}">Abrir</a></td>
            </tr>`).join('');
        mostrar(el, '.painel-vazio', p.totalElementos === 0);
        definir(el, 'totalPedidos', `${inteiro(p.totalElementos)} pedido(s)`);
        $('pagInfo').textContent = p.totalPaginas > 0 ? `Página ${p.pagina + 1} de ${p.totalPaginas}` : '';
        $('pagAnterior').disabled = p.pagina <= 0;
        $('pagProxima').disabled = p.pagina + 1 >= p.totalPaginas;
        return r;
    }

    // RF04
    async function carregarRanking(el) {
        const params = [...paramsFiltro(), ['ordenarPor', estado.ordenarPor], ['limite', 10]];
        if (campos.categoria.value) params.push(['categoria', campos.categoria.value]);
        const r = await api('/produtos/ranking', params);
        const itens = r.dados.produtos;
        const chave = estado.ordenarPor === 'FATURAMENTO' ? 'faturamento' : 'quantidade';
        const maior = Math.max(0, ...itens.map(i => Number(i[chave])));
        corpo('ranking').innerHTML = itens.map(i => `
            <div class="dash-rank-item" title="${esc(i.nome)}: ${esc(pct(i.participacao))} do faturamento, em ${inteiro(i.pedidos)} pedidos">
                <span class="dash-rank-pos">${i.posicao}</span>
                <span class="dash-rank-nome">
                    <span>${esc(i.nome)} <small>· ${esc(i.categoria)}</small></span>
                    <span class="dash-trilho"><span style="width:${maior > 0 ? (Number(i[chave]) / maior * 100).toFixed(1) : 0}%"></span></span>
                </span>
                <span class="dash-rank-num">${esc(inteiro(i.quantidade))} un.</span>
                <span class="dash-rank-num forte">${esc(moeda(i.faturamento))}</span>
            </div>`).join('');
        mostrar(el, '.painel-vazio', itens.length === 0);
        return r;
    }

    const PAINEIS = {
        resumo: carregarResumo, serie: carregarSerie, categoria: carregarCategoria, calor: carregarCalor,
        situacao: carregarSituacao, ranking: carregarRanking, pedidos: carregarPedidos
    };

    function recarregar(nome) {
        return carregarPainel(nome, PAINEIS[nome]).catch(tratarFalha);
    }

    function tratarFalha(e) {
        if (e && e.status === 401) {
            $('avisoSessao').classList.remove('d-none');
            pararAtualizacao();
        }
    }

    // ------------------------------------------------------------------ atualização (RF07)

    async function carregarTudo() {
        if (estado.carregando) return;
        if (!validarFiltros()) {
            $('statusAtualizacao').textContent = 'Corrija os filtros para atualizar os dados.';
            return;
        }
        estado.carregando = true;
        sincronizarUrl();
        const nomes = Object.keys(PAINEIS);
        const resultados = await Promise.allSettled(nomes.map(n => carregarPainel(n, PAINEIS[n])));
        estado.carregando = false;

        const falhas = resultados.filter(r => r.status === 'rejected');
        falhas.forEach(f => tratarFalha(f.reason));
        const resumo = resultados[0].status === 'fulfilled' ? resultados[0].value : null;
        const hora = (resumo ? new Date(resumo.geradoEm) : new Date())
            .toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
        const proxima = estado.timer ? ' · próxima em 5 min' : '';
        $('statusAtualizacao').textContent = falhas.length
            ? `Atualizado às ${hora}, ${falhas.length} painel(is) com erro`
            : `Atualizado às ${hora}${proxima}`;
    }

    function iniciarAtualizacao() {
        pararAtualizacao();
        if ($('autoAtualizar').getAttribute('aria-pressed') === 'true') {
            estado.timer = setInterval(() => { if (!document.hidden) carregarTudo(); }, INTERVALO_ATUALIZACAO_MS);
        }
    }

    function pararAtualizacao() {
        if (estado.timer) clearInterval(estado.timer);
        estado.timer = null;
    }

    // ------------------------------------------------------------------ eventos

    function marcarMetrica() {
        raiz.querySelectorAll('[data-metrica]').forEach(b =>
            b.setAttribute('aria-pressed', String(b.dataset.metrica === estado.metrica)));
    }

    function marcarOrdem() {
        raiz.querySelectorAll('[data-ordem]').forEach(b =>
            b.setAttribute('aria-pressed', String(b.dataset.ordem === estado.ordenarPor)));
    }

    function aoMudarFiltro() {
        estado.pagina = 0;
        carregarTudo();
    }

    campos.periodo.addEventListener('click', e => {
        const botao = e.target.closest('[data-periodo]');
        if (!botao) return;
        aplicarPeriodo(botao.dataset.periodo);
        aoMudarFiltro();
    });
    [campos.inicio, campos.fim].forEach(c => c.addEventListener('change', () => {
        aplicarPeriodo('custom');
        aoMudarFiltro();
    }));
    campos.status.addEventListener('change', () => { atualizarRotuloStatus(); aoMudarFiltro(); });
    campos.tipo.addEventListener('change', aoMudarFiltro);
    campos.categoria.addEventListener('change', () => {
        desenharChips();
        sincronizarUrl();
        const lista = painel('categoria')._lista;
        if (lista) desenharCategorias(lista);
        recarregar('ranking');
    });
    campos.agrupamento.addEventListener('change', () => { sincronizarUrl(); recarregar('serie'); });

    // Indicadores trocam a métrica do gráfico (sem nova consulta)
    raiz.querySelectorAll('[data-metrica]').forEach(b => b.addEventListener('click', () => {
        estado.metrica = b.dataset.metrica;
        marcarMetrica();
        sincronizarUrl();
        desenharSerie();
        desenharSparklines();
    }));

    // Categoria clicada filtra o ranking
    corpo('categorias').addEventListener('click', e => {
        const botao = e.target.closest('[data-categoria]');
        if (!botao) return;
        const categoria = botao.dataset.categoria;
        campos.categoria.value = campos.categoria.value === categoria ? '' : categoria;
        campos.categoria.dispatchEvent(new Event('change'));
    });

    // Status clicado filtra a lista de pedidos
    corpo('statusLista').addEventListener('click', e => {
        const botao = e.target.closest('[data-status]');
        if (!botao) return;
        estado.statusLista = estado.statusLista === botao.dataset.status ? null : botao.dataset.status;
        estado.pagina = 0;
        corpo('statusLista').querySelectorAll('[data-status]').forEach(b =>
            b.setAttribute('aria-pressed', String(b.dataset.status === estado.statusLista)));
        desenharChips();
        sincronizarUrl();
        recarregar('pedidos');
    });

    // Etiquetas removíveis
    $('chipsAtivos').addEventListener('click', e => {
        const botao = e.target.closest('[data-limpar]');
        if (!botao) return;
        if (botao.dataset.limpar === 'categoria') {
            campos.categoria.value = '';
            campos.categoria.dispatchEvent(new Event('change'));
        } else {
            estado.statusLista = null;
            corpo('statusLista').querySelectorAll('[data-status]').forEach(b => b.setAttribute('aria-pressed', 'false'));
            desenharChips();
            sincronizarUrl();
            recarregar('pedidos');
        }
    });

    // Mapa de calor: detalhe da célula ao passar o mouse ou focar pelo teclado
    const grade = corpo('calor');
    const mostrarCelula = e => {
        const cel = e.target.closest('.cel');
        if (cel) $('infoCalor').textContent = cel.dataset.info;
    };
    grade.addEventListener('mouseover', mostrarCelula);
    grade.addEventListener('focusin', mostrarCelula);
    grade.addEventListener('mouseleave', () => { $('infoCalor').textContent = painel('calor')._textoPico || ''; });

    raiz.querySelectorAll('[data-ordem]').forEach(b => b.addEventListener('click', () => {
        estado.ordenarPor = b.dataset.ordem;
        marcarOrdem();
        sincronizarUrl();
        recarregar('ranking');
    }));
    $('pagAnterior').addEventListener('click', () => { estado.pagina--; recarregar('pedidos'); });
    $('pagProxima').addEventListener('click', () => { estado.pagina++; recarregar('pedidos'); });
    $('btnAtualizar').addEventListener('click', carregarTudo);
    $('autoAtualizar').addEventListener('click', () => {
        const ligado = $('autoAtualizar').getAttribute('aria-pressed') !== 'true';
        $('autoAtualizar').setAttribute('aria-pressed', String(ligado));
        iniciarAtualizacao();
        const texto = $('statusAtualizacao').textContent.replace(' · próxima em 5 min', '');
        $('statusAtualizacao').textContent = ligado ? texto + ' · próxima em 5 min' : texto;
    });
    $('filtros').addEventListener('submit', e => e.preventDefault());

    // Rampa da legenda do mapa de calor
    $('rampa').innerHTML = RAMPA.map(c => `<span style="background:${c}"></span>`).join('');

    // ------------------------------------------------------------------ início

    (async function iniciar() {
        try {
            await carregarOpcoes();
        } catch (e) {
            tratarFalha(e);
            $('statusAtualizacao').textContent = 'Não foi possível carregar as opções de filtro.';
        }
        restaurarDaUrl();
        iniciarAtualizacao();
        await carregarTudo();
    })();
})();
