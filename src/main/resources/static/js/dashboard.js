/*
 * Dashboard administrativo (RF06): todo o conteúdo vem da API /api/v1/dashboard.
 * A página usa a sessão de quem entrou no sistema; o cabeçalho X-Requested-With faz a API
 * responder 401 sem abrir a janela de login do navegador quando a sessão expira.
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
        serie: cor('--dash-serie'),
        suave: cor('--dash-serie-suave'),
        grade: cor('--dash-grade'),
        texto2: cor('--dash-texto-2')
    };

    const BADGE = {
        RECEBIDO: 'bg-secondary', EM_PREPARACAO: 'bg-warning text-dark', PRONTO: 'bg-info text-dark',
        SAIU_PARA_ENTREGA: 'bg-primary', RETIRADO: 'bg-primary', FINALIZADO: 'bg-success', CANCELADO: 'bg-danger'
    };

    const fmtMoeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const fmtInteiro = new Intl.NumberFormat('pt-BR');
    const fmtDecimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });
    const fmtMoedaCompacta = new Intl.NumberFormat('pt-BR',
        { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 });
    const moeda = v => fmtMoeda.format(Number(v || 0));
    /** Eixos usam valores compactos (R$ 10 mil); tooltips e tabelas mostram o valor completo. */
    const moedaEixo = v => fmtMoedaCompacta.format(Number(v || 0));
    const inteiro = v => fmtInteiro.format(Number(v || 0));
    const pct = v => v == null ? '-' : fmtDecimal.format(Number(v)) + '%';

    const $ = id => document.getElementById(id);
    const campos = {
        periodo: $('fPeriodo'), inicio: $('fInicio'), fim: $('fFim'), status: $('fStatus'),
        btnStatus: $('btnStatus'), tipo: $('fTipo'), categoria: $('fCategoria'), agrupamento: $('fAgrupamento')
    };

    const estado = {
        hoje: null, pagina: 0, metrica: 'faturamento', ordenarPor: 'QUANTIDADE',
        serie: null, carregando: false, timer: null
    };
    const graficos = {};

    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.color = COR.texto2;
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

    function definir(el, campo, texto) {
        const alvo = el.querySelector(`[data-campo="${campo}"]`);
        if (alvo) alvo.textContent = texto;
    }

    function mostrar(el, seletor, visivel) {
        el.querySelectorAll(seletor).forEach(e => e.classList.toggle('d-none', !visivel));
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

    function grafico(id, config) {
        if (graficos[id]) {
            graficos[id].data = config.data;
            graficos[id].options = config.options;
            graficos[id].update();
        } else {
            graficos[id] = new Chart($(id), config);
        }
    }

    function eixos(formatoY, horizontal) {
        const valor = { beginAtZero: true, grid: { color: COR.grade }, border: { display: false },
            ticks: { callback: formatoY, maxRotation: 0, maxTicksLimit: horizontal ? 5 : 8 } };
        const categoria = { grid: { display: false }, border: { color: COR.grade } };
        return horizontal ? { x: valor, y: categoria } : { x: categoria, y: valor };
    }

    function tabela(el, cabecalho, linhas) {
        const html = '<table class="table table-sm mb-0 tabela-num"><thead><tr>'
            + cabecalho.map(c => `<th>${esc(c)}</th>`).join('') + '</tr></thead><tbody>'
            + linhas.map(l => '<tr>' + l.map(c => `<td>${esc(c)}</td>`).join('') + '</tr>').join('')
            + '</tbody></table>';
        el.querySelector('.tabela-dados').innerHTML = html;
    }

    // ------------------------------------------------------------------ filtros (RF05)

    function statusSelecionados() {
        return [...campos.status.querySelectorAll('input:checked')].map(i => i.value);
    }

    function atualizarRotuloStatus() {
        const marcados = [...campos.status.querySelectorAll('input:checked')];
        campos.btnStatus.textContent = marcados.length === 0 ? 'Todos'
            : marcados.length === 1 ? marcados[0].dataset.descricao : `${marcados.length} selecionados`;
    }

    function aplicarPeriodo(preset) {
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

    /** Mantém os filtros na URL: recarregar ou compartilhar o link reabre a mesma visão. */
    function sincronizarUrl() {
        const p = new URLSearchParams(paramsFiltro());
        if (campos.periodo.value !== '30') p.set('periodo', campos.periodo.value);
        if (campos.categoria.value) p.set('categoria', campos.categoria.value);
        if (campos.agrupamento.value !== 'DIA') p.set('agrupamento', campos.agrupamento.value);
        if (estado.ordenarPor !== 'QUANTIDADE') p.set('ordenarPor', estado.ordenarPor);
        const query = p.toString();
        history.replaceState(null, '', query ? `?${query}` : window.location.pathname);
    }

    function restaurarDaUrl() {
        const p = new URLSearchParams(window.location.search);
        const periodo = p.get('periodo') || (p.get('dataInicio') ? 'custom' : '30');
        campos.periodo.value = periodo;
        aplicarPeriodo(periodo);
        if (p.get('dataInicio')) campos.inicio.value = p.get('dataInicio');
        if (p.get('dataFim')) campos.fim.value = p.get('dataFim');
        const status = p.getAll('status');
        campos.status.querySelectorAll('input').forEach(i => { i.checked = status.includes(i.value); });
        atualizarRotuloStatus();
        if (p.get('tipoEntrega')) campos.tipo.value = p.get('tipoEntrega');
        if (p.get('categoria')) campos.categoria.value = p.get('categoria');
        if (p.get('agrupamento')) campos.agrupamento.value = p.get('agrupamento');
        if (p.get('ordenarPor') === 'FATURAMENTO') {
            estado.ordenarPor = 'FATURAMENTO';
            $('oFat').checked = true;
        }
    }

    async function carregarOpcoes() {
        const opcoes = await api('/filtros');
        estado.hoje = opcoes.dataFimPadrao;
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
        definir(el, 'pedidosCancelados', `${inteiro(d.pedidosCancelados)} de ${inteiro(d.totalPedidos)} pedidos`);
        definir(el, 'totalDescontos', moeda(d.totalDescontos));
        definir(el, 'itensVendidos', `${inteiro(d.itensVendidos)} itens vendidos`);
        definir(el, 'periodo', `${dataBr(r.filtros.dataInicio)} a ${dataBr(r.filtros.dataFim)}`);

        const anterior = `${dataBr(d.comparativo.dataInicio)} a ${dataBr(d.comparativo.dataFim)}`;
        el.querySelectorAll('[data-delta]').forEach(alvo => {
            const v = d.comparativo[alvo.dataset.delta];
            if (v == null) {
                alvo.textContent = 'Sem base de comparação no período anterior';
                return;
            }
            const subiu = Number(v) > 0;
            const bom = (alvo.dataset.bom === 'alta') === subiu;
            const seta = Number(v) === 0 ? '=' : (subiu ? '▲' : '▼');
            alvo.innerHTML = `<span class="${Number(v) === 0 ? '' : (bom ? 'bom' : 'ruim')}">${seta} ${esc(pct(Math.abs(v)))}</span>`
                + ` vs ${esc(anterior)}`;
        });
        return r;
    }

    // RF02
    async function carregarSerie(el) {
        const r = await api('/vendas/serie', [...paramsFiltro(), ['agrupamento', campos.agrupamento.value]]);
        estado.serie = r.dados;
        desenharSerie();
        tabela(el, ['Período', 'Faturamento', 'Pedidos', 'Cancelados', 'Ticket médio'],
            r.dados.pontos.map(p => [dataBr(p.periodo), moeda(p.faturamento), inteiro(p.pedidos),
                inteiro(p.cancelados), moeda(p.ticketMedio)]));
        return r;
    }

    function desenharSerie() {
        const el = painel('serie');
        const pontos = estado.serie ? estado.serie.pontos : [];
        const vazio = pontos.every(p => Number(p.pedidos) === 0 && Number(p.cancelados) === 0);
        mostrar(el, '.painel-vazio', vazio);
        mostrar(el, '.grafico', !vazio);
        if (vazio) return;

        const metrica = estado.metrica;
        const formato = metrica === 'pedidos' ? inteiro : moeda;
        const nomes = { faturamento: 'Faturamento', pedidos: 'Pedidos', ticketMedio: 'Ticket médio' };
        grafico('gSerie', {
            type: 'line',
            data: {
                labels: pontos.map(p => p.rotulo),
                datasets: [{
                    label: nomes[metrica],
                    data: pontos.map(p => Number(p[metrica])),
                    borderColor: COR.serie,
                    backgroundColor: COR.serie + '14',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.25,
                    pointRadius: pontos.length > 45 ? 0 : 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: COR.serie
                }]
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                scales: eixos(v => (metrica === 'pedidos' ? inteiro : moedaEixo)(v)),
                plugins: { tooltip: { callbacks: {
                    title: itens => 'Período ' + dataBr(pontos[itens[0].dataIndex].periodo),
                    label: c => `${nomes[metrica]}: ${formato(c.parsed.y)}`
                } } }
            }
        });
    }

    async function carregarCategoria(el) {
        const r = await api('/vendas/por-categoria', paramsFiltro());
        const lista = r.dados;
        mostrar(el, '.painel-vazio', lista.length === 0);
        mostrar(el, '.grafico', lista.length > 0);
        tabela(el, ['Categoria', 'Quantidade', 'Faturamento', 'Participação'],
            lista.map(c => [c.categoria, inteiro(c.quantidade), moeda(c.faturamento), pct(c.participacao)]));
        if (lista.length === 0) return r;
        grafico('gCategoria', {
            type: 'bar',
            data: {
                labels: lista.map(c => c.categoria),
                datasets: [{ data: lista.map(c => Number(c.faturamento)), backgroundColor: COR.serie,
                    borderRadius: 4, borderSkipped: 'start', maxBarThickness: 22 }]
            },
            options: {
                indexAxis: 'y',
                scales: eixos(v => moedaEixo(v), true),
                plugins: { tooltip: { callbacks: {
                    label: c => `${moeda(lista[c.dataIndex].faturamento)} (${pct(lista[c.dataIndex].participacao)})`
                } } }
            }
        });
        return r;
    }

    async function carregarHora(el) {
        const r = await api('/vendas/por-hora', paramsFiltro());
        const horas = r.dados;
        const maximo = Math.max(...horas.map(h => Number(h.pedidos)));
        mostrar(el, '.painel-vazio', maximo === 0);
        mostrar(el, '.grafico', maximo > 0);
        const pico = horas.find(h => Number(h.pedidos) === maximo);
        definir(el, 'pico', maximo > 0 ? `Pico: ${pico.hora}h (${inteiro(pico.pedidos)} pedidos)` : '');
        tabela(el, ['Hora', 'Pedidos', 'Faturamento'],
            horas.map(h => [`${h.hora}h`, inteiro(h.pedidos), moeda(h.faturamento)]));
        if (maximo === 0) return r;
        grafico('gHora', {
            type: 'bar',
            data: {
                labels: horas.map(h => `${h.hora}h`),
                datasets: [{
                    data: horas.map(h => Number(h.pedidos)),
                    // destaque só no horário de pico; os demais em um tom mais claro do mesmo azul
                    backgroundColor: horas.map(h => Number(h.pedidos) === maximo ? COR.serie : COR.suave),
                    borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24
                }]
            },
            options: {
                scales: eixos(v => inteiro(v)),
                plugins: { tooltip: { callbacks: {
                    title: itens => `${horas[itens[0].dataIndex].hora}h às ${horas[itens[0].dataIndex].hora}h59`,
                    label: c => `${inteiro(horas[c.dataIndex].pedidos)} pedidos, ${moeda(horas[c.dataIndex].faturamento)}`
                } } }
            }
        });
        return r;
    }

    // RF03
    async function carregarSituacao(el) {
        const r = await api('/pedidos/situacao', paramsFiltro());
        const s = r.dados;
        definir(el, 'emAndamento', inteiro(s.emAndamento));
        definir(el, 'finalizados', inteiro(s.finalizados));
        definir(el, 'tempoMedio', s.tempoMedioAteSaidaMinutos == null ? '-'
            : `${fmtDecimal.format(Number(s.tempoMedioAteSaidaMinutos))} min`);
        mostrar(el, '.painel-vazio', s.total === 0);
        mostrar(el, '.grafico', s.total > 0);
        tabela(el, ['Status', 'Pedidos', '% do total', 'Valor'],
            s.porStatus.map(c => [c.descricao, inteiro(c.quantidade), pct(c.percentual), moeda(c.valor)]));
        if (s.total === 0) return r;
        grafico('gSituacao', {
            type: 'bar',
            data: {
                labels: s.porStatus.map(c => c.descricao),
                datasets: [{ data: s.porStatus.map(c => Number(c.quantidade)), backgroundColor: COR.serie,
                    borderRadius: 4, borderSkipped: 'start', maxBarThickness: 18 }]
            },
            options: {
                indexAxis: 'y',
                scales: eixos(v => inteiro(v), true),
                plugins: { tooltip: { callbacks: {
                    label: c => `${inteiro(s.porStatus[c.dataIndex].quantidade)} pedidos (${pct(s.porStatus[c.dataIndex].percentual)})`
                } } }
            }
        });
        return r;
    }

    async function carregarPedidos(el) {
        const r = await api('/pedidos', [...paramsFiltro(), ['pagina', estado.pagina], ['tamanho', TAMANHO_PAGINA]]);
        const p = r.dados;
        const corpo = el.querySelector('[data-corpo="pedidos"]');
        corpo.innerHTML = p.conteudo.map(x => `
            <tr>
                <td class="ps-3 text-nowrap">${esc(dataHoraCurta(x.dataHoraEntrada))}</td>
                <td>${esc(x.nomeCliente || '-')}</td>
                <td>${x.tipoEntrega === 'RETIRADA' ? 'Retirada' : 'Entrega'}</td>
                <td><span class="badge ${BADGE[x.status] || 'bg-secondary'}">${esc(x.statusDescricao || x.status)}</span></td>
                <td class="text-end">${esc(moeda(x.valorTotal))}</td>
                <td class="text-end pe-3"><a href="/pedidos/${encodeURIComponent(x.id)}" class="btn btn-sm btn-link py-0">Abrir</a></td>
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
        const maior = Math.max(0, ...itens.map(i => Number(i.participacao)));
        el.querySelector('[data-corpo="ranking"]').innerHTML = itens.map(i => `
            <tr>
                <td class="ps-3 text-muted">${i.posicao}</td>
                <td><div class="fw-medium">${esc(i.nome)}</div><div class="small text-muted">${esc(i.categoria)}</div></td>
                <td class="text-end">${esc(inteiro(i.quantidade))}</td>
                <td class="text-end">${esc(moeda(i.faturamento))}</td>
                <td><div class="d-flex align-items-center gap-2">
                    <div class="barra-participacao flex-grow-1" role="presentation">
                        <span style="width:${maior > 0 ? (Number(i.participacao) / maior * 100).toFixed(1) : 0}%"></span>
                    </div>
                    <span class="small text-muted">${esc(pct(i.participacao))}</span></div></td>
            </tr>`).join('');
        mostrar(el, '.painel-vazio', itens.length === 0);
        return r;
    }

    const PAINEIS = {
        resumo: carregarResumo, serie: carregarSerie, categoria: carregarCategoria, hora: carregarHora,
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
        const quando = resumo ? new Date(resumo.geradoEm) : new Date();
        const hora = quando.toLocaleTimeString('pt-BR');
        $('statusAtualizacao').textContent = falhas.length
            ? `Atualizado às ${hora}, ${falhas.length} painel(is) com erro`
            : `Atualizado às ${hora}`;
    }

    function iniciarAtualizacao() {
        pararAtualizacao();
        if ($('autoAtualizar').checked) {
            estado.timer = setInterval(() => { if (!document.hidden) carregarTudo(); }, INTERVALO_ATUALIZACAO_MS);
        }
    }

    function pararAtualizacao() {
        if (estado.timer) clearInterval(estado.timer);
        estado.timer = null;
    }

    // ------------------------------------------------------------------ eventos

    function aoMudarFiltro() {
        estado.pagina = 0;
        carregarTudo();
    }

    campos.periodo.addEventListener('change', () => { aplicarPeriodo(campos.periodo.value); aoMudarFiltro(); });
    [campos.inicio, campos.fim].forEach(c => c.addEventListener('change', () => {
        campos.periodo.value = 'custom';
        aoMudarFiltro();
    }));
    campos.status.addEventListener('change', () => { atualizarRotuloStatus(); aoMudarFiltro(); });
    campos.tipo.addEventListener('change', aoMudarFiltro);
    campos.categoria.addEventListener('change', () => { sincronizarUrl(); recarregar('ranking'); });
    campos.agrupamento.addEventListener('change', () => { sincronizarUrl(); recarregar('serie'); });
    document.querySelectorAll('input[name="metrica"]').forEach(r => r.addEventListener('change', () => {
        estado.metrica = r.value;
        desenharSerie();
    }));
    document.querySelectorAll('input[name="ordenarPor"]').forEach(r => r.addEventListener('change', () => {
        estado.ordenarPor = r.value;
        sincronizarUrl();
        recarregar('ranking');
    }));
    $('pagAnterior').addEventListener('click', () => { estado.pagina--; recarregar('pedidos'); });
    $('pagProxima').addEventListener('click', () => { estado.pagina++; recarregar('pedidos'); });
    $('btnAtualizar').addEventListener('click', carregarTudo);
    $('autoAtualizar').addEventListener('change', iniciarAtualizacao);
    $('filtros').addEventListener('submit', e => e.preventDefault());

    // ------------------------------------------------------------------ início

    (async function iniciar() {
        try {
            await carregarOpcoes();
        } catch (e) {
            tratarFalha(e);
            $('statusAtualizacao').textContent = 'Não foi possível carregar as opções de filtro.';
        }
        restaurarDaUrl();
        await carregarTudo();
        iniciarAtualizacao();
    })();
})();
