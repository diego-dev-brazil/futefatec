const playerCountInput = document.getElementById('playerCount');
const soccerField = document.getElementById('soccerField');
const reservesList = document.getElementById('reservesList');
const addReserveBtn = document.getElementById('addReserveBtn');

const MAX_RESERVES = 3;
const TITULARES = 5;

const formacaoTitular = [
    { label: "Goleiro", top: "90%", left: "50%", name: "Jogador_Goleiro" },
    { label: "Fixo", top: "70%", left: "50%", name: "Jogador_Fixo" },
    { label: "Ala Esquerda", top: "45%", left: "20%", name: "Jogador_Ala_Esq" },
    { label: "Ala Direita", top: "45%", left: "80%", name: "Jogador_Ala_Dir" },
    { label: "Pivô (Ataque)", top: "15%", left: "50%", name: "Jogador_Pivo" }
];

let reservesCount = 0;

function renderTitulares() {
    soccerField.innerHTML = '';

    formacaoTitular.forEach(pos => {
        const playerDiv = document.createElement('div');
        playerDiv.className = 'player-position';
        playerDiv.style.top = pos.top;
        playerDiv.style.left = pos.left;

        playerDiv.innerHTML = `
            <span>${pos.label}</span>
            <input type="text" name="${pos.name}" placeholder="Nome..." required>
        `;

        soccerField.appendChild(playerDiv);
    });
}

function updatePlayerCount() {
    playerCountInput.value = TITULARES + reservesCount;
}

function updateAddButtonState() {
    if (reservesCount >= MAX_RESERVES) {
        addReserveBtn.disabled = true;
        addReserveBtn.textContent = 'Limite de reservas atingido';
    } else {
        addReserveBtn.disabled = false;
        addReserveBtn.textContent = '+ Adicionar Reserva';
    }
}

function addReserve() {
    if (reservesCount >= MAX_RESERVES) return;

    reservesCount++;
    const reserveIndex = reservesCount;

    const reserveItem = document.createElement('div');
    reserveItem.className = 'reserve-item';
    reserveItem.dataset.index = reserveIndex;

    reserveItem.innerHTML = `
        <div class="form-group reserve-input">
            <label for="reserva${reserveIndex}">Reserva ${reserveIndex}:</label>
            <input type="text" id="reserva${reserveIndex}" name="Jogador_Reserva_${reserveIndex}" placeholder="Nome do reserva..." required>
        </div>
        <button type="button" class="btn-remove-reserve" aria-label="Remover reserva ${reserveIndex}">&times;</button>
    `;

    reserveItem.querySelector('.btn-remove-reserve').addEventListener('click', () => {
        reserveItem.remove();
        reservesCount--;
        renumberReserves();
        updatePlayerCount();
        updateAddButtonState();
    });

    reservesList.appendChild(reserveItem);
    updatePlayerCount();
    updateAddButtonState();
}

function renumberReserves() {
    const items = reservesList.querySelectorAll('.reserve-item');
    items.forEach((item, idx) => {
        const num = idx + 1;
        item.dataset.index = num;

        const label = item.querySelector('label');
        const input = item.querySelector('input');
        const removeBtn = item.querySelector('.btn-remove-reserve');

        label.setAttribute('for', `reserva${num}`);
        label.textContent = `Reserva ${num}:`;
        input.id = `reserva${num}`;
        input.name = `Jogador_Reserva_${num}`;
        removeBtn.setAttribute('aria-label', `Remover reserva ${num}`);
    });
}

addReserveBtn.addEventListener('click', addReserve);

renderTitulares();
updatePlayerCount();
updateAddButtonState();

/* ---------------------------------------------------------------------
   INTEGRAÇÃO COM O BACKEND JAVA (Jakarta Servlets)
   --------------------------------------------------------------------- */

let detectedBackend = 'http://localhost:8085';

/**
 * Detecta dinamicamente onde o backend Java está rodando.
 * Se a página foi aberta diretamente pelo Tomcat (porta 8085), usa caminhos relativos ('').
 * Se foi aberta pelo Live Server (porta 5500) ou por arquivo direto (file://), aponta para http://localhost:8085.
 */
async function getBackendUrl() {
    if (window.location.origin.includes(':8085')) {
        return '';
    }
    return 'http://localhost:8085';
}

const teamForm = document.getElementById('teamForm');
const statusMessage = document.getElementById('statusMessage');
const submitBtn = teamForm.querySelector('.btn-submit');

/**
 * Exibe mensagens visuais de status para o usuário (loading, success, error)
 */
function showStatus(texto, tipo = 'loading') {
    if (!statusMessage) return;
    statusMessage.textContent = texto;
    statusMessage.className = `status-message ${tipo}`;
    statusMessage.style.display = 'block';
    statusMessage.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideStatus() {
    if (!statusMessage) return;
    statusMessage.style.display = 'none';
}

/**
 * Intercepta a submissão do formulário para envio via API fetch()
 */
teamForm.addEventListener('submit', async function(event) {
    event.preventDefault(); // Impede o recarregamento clássico da página

    // Coleta todos os campos e arquivos do formulário automaticamente como multipart/form-data
    const formData = new FormData(teamForm);

    // Estado visual de envio
    const textoOriginalBtn = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = 'Cadastrando time...';
    showStatus('⏳ Enviando inscrição para o servidor Java...', 'loading');

    try {
        const base = await getBackendUrl();
        // Envia a requisição POST para o Servlet em /inscrever
        const response = await fetch(base + '/inscrever', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (response.ok && data.status === 'sucesso') {
            showStatus(`✅ ${data.mensagem} (Time: "${data.time.nome}" | Capitão: "${data.time.capitao}")`, 'success');
            
            // Limpa os campos do formulário
            teamForm.reset();
            
            // Reseta a lista de reservas dinâmica
            reservesList.innerHTML = '';
            reservesCount = 0;
            renderTitulares();
            updatePlayerCount();
            updateAddButtonState();
            carregarListaTimesCadastrados();
        } else {
            showStatus(`❌ Falha no cadastro: ${data.mensagem || 'Dados inválidos.'}`, 'error');
        }

    } catch (error) {
        console.error('Erro na requisição para o servidor Java:', error);
        showStatus('❌ Não foi possível se comunicar com o backend Java (http://localhost:8080/inscrever). Verifique se o servidor está rodando!', 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = textoOriginalBtn;
    }
});

/* ---------------------------------------------------------------------
   GERENCIADOR DE ABAS (INSCRIÇÃO X CHAVEAMENTO)
   --------------------------------------------------------------------- */

const tabBtns = document.querySelectorAll('.tab-btn');
const tabContents = document.querySelectorAll('.tab-content');

tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        const targetId = btn.getAttribute('data-tab');

        tabBtns.forEach(b => b.classList.remove('active'));
        tabContents.forEach(c => c.classList.remove('active'));

        btn.classList.add('active');
        const targetContent = document.getElementById(targetId);
        if (targetContent) {
            targetContent.classList.add('active');
        }

        if (targetId === 'tab-chaveamento') {
            carregarChaveamento();
        }
    });
});

/* ---------------------------------------------------------------------
   SISTEMA DE CHAVEAMENTO COM REPESCAGEM (DOUBLE ELIMINATION)
   --------------------------------------------------------------------- */

let isAdminState = false;
let isBracketReleasedState = false;

const teamsCountBadge = document.getElementById('teamsCountBadge');
const bracketContainer = document.getElementById('bracketContainer');
const bracketStatus = document.getElementById('bracketStatus');
const btnGerarChave = document.getElementById('btnGerarChave');
const btnResetChave = document.getElementById('btnResetChave');

// Elementos de Controle de Liberação e Acesso
const bracketLockedMessage = document.getElementById('bracketLockedMessage');
const bracketActiveWrapper = document.getElementById('bracketActiveWrapper');
const adminBracketActions = document.getElementById('adminBracketActions');
const btnToggleChaveamento = document.getElementById('btnToggleChaveamento');
const adminRegisteredTeamsSection = document.getElementById('adminRegisteredTeamsSection');

// Elementos de Autenticação Admin
const btnOpenAdminModal = document.getElementById('btnOpenAdminModal');
const adminLoggedBadge = document.getElementById('adminLoggedBadge');
const btnLogoutAdmin = document.getElementById('btnLogoutAdmin');
const adminLoginModal = document.getElementById('adminLoginModal');
const closeAdminLoginBtn = document.getElementById('closeAdminLoginBtn');
const adminLoginForm = document.getElementById('adminLoginForm');
const adminLoginStatus = document.getElementById('adminLoginStatus');

// Modal Elements (Síntese)
const teamModal = document.getElementById('teamModal');
const modalContent = document.getElementById('modalContent');
const modalCloseBtn = document.getElementById('modalCloseBtn');

function showBracketStatus(msg, tipo = 'loading') {
    if (!bracketStatus) return;
    bracketStatus.textContent = msg;
    bracketStatus.className = `status-message ${tipo}`;
    bracketStatus.style.display = 'block';
}

function hideBracketStatus() {
    if (!bracketStatus) return;
    bracketStatus.style.display = 'none';
}

/**
 * Carrega e renderiza o chaveamento a partir do backend
 */
async function carregarChaveamento() {
    try {
        const base = await getBackendUrl();
        const res = await fetch(base + '/api/chaveamento');
        const data = await res.json();

        // Se bloqueado para o público geral:
        if (data.status === 'bloqueado' || (data.chaveamentoLiberado === false && !isAdminState)) {
            if (bracketLockedMessage) bracketLockedMessage.style.display = 'block';
            if (bracketActiveWrapper) bracketActiveWrapper.style.display = 'none';
            return;
        }

        // Se liberado ou se for administrador:
        if (bracketLockedMessage) bracketLockedMessage.style.display = 'none';
        if (bracketActiveWrapper) bracketActiveWrapper.style.display = 'block';

        if (teamsCountBadge) {
            teamsCountBadge.textContent = `${data.totalTimesCadastrados} equipes inscritas`;
        }

        if (!data.chaveamentoGerado || data.partidas.length === 0) {
            renderizarChaveVazia(data.totalTimesCadastrados);
        } else {
            renderizarChaves(data.partidas);
        }
    } catch (err) {
        console.error('Erro ao carregar chaveamento:', err);
        if (bracketContainer) {
            bracketContainer.innerHTML = `
                <div class="empty-bracket-state">
                    <p>⚠️ Não foi possível conectar ao servidor Java (localhost:8085).</p>
                </div>
            `;
        }
    }
}

function renderizarChaveVazia(totalTimes) {
    bracketContainer.innerHTML = `
        <div class="empty-bracket-state">
            <h3>Nenhum Chaveamento Ativo</h3>
            <p>Atualmente existem <strong>${totalTimes} equipes</strong> inscritas no torneio.</p>
            <p style="margin-top: 8px;">Clique no botão verde <strong>"🎲 Sortear / Gerar Chaveamento"</strong> acima para criar as chaves!</p>
        </div>
    `;
}

/**
 * Renderiza as seções: Chave Principal, Repescagem e Grande Final
 */
function renderizarChaves(partidas) {
    const partidasPrincipal = partidas.filter(p => p.chave === 'PRINCIPAL');
    const partidasRepescagem = partidas.filter(p => p.chave === 'REPESCAGEM');
    const partidasGrandeFinal = partidas.filter(p => p.chave === 'GRANDE_FINAL');

    let html = '';

    // 1. Chave Principal (Mata-Mata dos Vencedores)
    if (partidasPrincipal.length > 0) {
        html += `
            <div class="bracket-section">
                <div class="bracket-section-header">
                    <h2>🏆 Chave Principal (Mata-Mata)</h2>
                </div>
                <div class="bracket-grid">
                    ${partidasPrincipal.map(p => renderCardPartida(p)).join('')}
                </div>
            </div>
        `;
    }

    // 2. Chave de Repescagem (Segunda Chance dos Perdedores)
    if (partidasRepescagem.length > 0) {
        html += `
            <div class="bracket-section">
                <div class="bracket-section-header">
                    <h2>🔄 Chave de Repescagem (Segunda Chance)</h2>
                </div>
                <div class="bracket-grid">
                    ${partidasRepescagem.map(p => renderCardPartida(p)).join('')}
                </div>
            </div>
        `;
    }

    // 3. Grande Final
    if (partidasGrandeFinal.length > 0) {
        html += `
            <div class="bracket-section" style="border: 2px solid #eab308; background: #fffdf5;">
                <div class="bracket-section-header">
                    <h2>⭐ Grande Final (Campeão Principal vs Campeão da Repescagem)</h2>
                </div>
                <div class="bracket-grid">
                    ${partidasGrandeFinal.map(p => renderCardPartida(p)).join('')}
                </div>
            </div>
        `;
    }

    bracketContainer.innerHTML = html;
    anexarEventosChaveamento();
}

/**
 * Renderiza o Card de uma partida individual
 */
function renderCardPartida(p) {
    const isFinalizada = p.golsA !== null && p.golsB !== null;
    const venceuA = isFinalizada && p.vencedorId === p.timeAId;
    const venceuB = isFinalizada && p.vencedorId === p.timeBId;

    const timeANome = p.timeA ? p.timeA.nome : 'Aguardando adversário...';
    const timeBNome = p.timeB ? p.timeB.nome : 'Aguardando adversário...';

    const timeALogo = p.timeA && p.timeA.nomeArquivoLogo && p.timeA.nomeArquivoLogo !== 'sem_logo.png'
        ? `${detectedBackend || ''}/uploads/${p.timeA.nomeArquivoLogo}`
        : 'https://placehold.co/60x60/b11226/white?text=FT';

    const timeBLogo = p.timeB && p.timeB.nomeArquivoLogo && p.timeB.nomeArquivoLogo !== 'sem_logo.png'
        ? `${detectedBackend || ''}/uploads/${p.timeB.nomeArquivoLogo}`
        : 'https://placehold.co/60x60/0b1f4b/white?text=FT';

    let scoreDisplayA;
    if (isFinalizada) {
        scoreDisplayA = `<span class="score-badge">${p.golsA}</span>`;
    } else if (isAdminState) {
        scoreDisplayA = `<input type="number" min="0" class="score-input input-score-a" placeholder="0" ${!p.timeAId || !p.timeBId ? 'disabled' : ''}>`;
    } else {
        scoreDisplayA = `<span class="score-badge" style="background: #f1f5f9; color: #94a3b8; font-size: 0.85rem;">-</span>`;
    }

    let scoreDisplayB;
    if (isFinalizada) {
        scoreDisplayB = `<span class="score-badge">${p.golsB}</span>`;
    } else if (isAdminState) {
        scoreDisplayB = `<input type="number" min="0" class="score-input input-score-b" placeholder="0" ${!p.timeAId || !p.timeBId ? 'disabled' : ''}>`;
    } else {
        scoreDisplayB = `<span class="score-badge" style="background: #f1f5f9; color: #94a3b8; font-size: 0.85rem;">-</span>`;
    }

    return `
        <div class="match-card" data-match-id="${p.id}">
            <div class="match-header">
                <span>Jogo #${p.numeroPartida}</span>
                <span>${formatarFase(p.fase)}</span>
            </div>
            <div class="match-teams">
                <!-- Time A -->
                <div class="team-slot ${venceuA ? 'winner' : ''} ${isFinalizada && !venceuA ? 'loser' : ''}">
                    <div class="team-slot-info" ${p.timeAId ? `onclick="abrirSinteseTime(${p.timeAId})"` : ''}>
                        <img src="${timeALogo}" alt="Logo" class="team-logo-thumb" onerror="this.src='https://placehold.co/60x60/e2e8f0/64748b?text=FT'">
                        <span class="team-name-text">${timeANome}</span>
                    </div>
                    ${scoreDisplayA}
                </div>

                <!-- Time B -->
                <div class="team-slot ${venceuB ? 'winner' : ''} ${isFinalizada && !venceuB ? 'loser' : ''}">
                    <div class="team-slot-info" ${p.timeBId ? `onclick="abrirSinteseTime(${p.timeBId})"` : ''}>
                        <img src="${timeBLogo}" alt="Logo" class="team-logo-thumb" onerror="this.src='https://placehold.co/60x60/e2e8f0/64748b?text=FT'">
                        <span class="team-name-text">${timeBNome}</span>
                    </div>
                    ${scoreDisplayB}
                </div>
            </div>

            ${isAdminState && !isFinalizada && p.timeAId && p.timeBId ? `
                <div class="match-footer">
                    <button type="button" class="btn-confirm-score" onclick="confirmarPlacar(${p.id})">
                        Salvar Placar
                    </button>
                </div>
            ` : ''}
        </div>
    `;
}

function formatarFase(fase) {
    return fase.replace('_', ' ');
}

/**
 * Atualiza placar de um jogo via POST /api/chaveamento/placar
 */
async function confirmarPlacar(partidaId) {
    const card = document.querySelector(`.match-card[data-match-id="${partidaId}"]`);
    if (!card) return;

    const inputA = card.querySelector('.input-score-a');
    const inputB = card.querySelector('.input-score-b');

    if (!inputA || !inputB || inputA.value === '' || inputB.value === '') {
        alert('Por favor, preencha os gols de ambos os times!');
        return;
    }

    const golsA = parseInt(inputA.value, 10);
    const golsB = parseInt(inputB.value, 10);

    if (golsA === golsB) {
        alert('No futsal mata-mata não pode haver empate! Em caso de igualdade no tempo normal, coloque o resultado final com a decisão por pênaltis.');
        return;
    }

    showBracketStatus('Salvando placar e avançando equipes...', 'loading');

    try {
        const base = await getBackendUrl();
        const formData = new URLSearchParams();
        formData.append('partidaId', partidaId);
        formData.append('golsA', golsA);
        formData.append('golsB', golsB);

        const res = await fetch(base + '/api/chaveamento/placar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData.toString()
        });

        const data = await res.json();
        if (res.ok && data.status === 'sucesso') {
            showBracketStatus(`✅ Placar registrado! As equipes avançaram nas chaves.`, 'success');
            renderizarChaves(data.partidas);
            setTimeout(hideBracketStatus, 4000);
        } else {
            showBracketStatus(`❌ Erro: ${data.mensagem}`, 'error');
        }
    } catch (err) {
        console.error(err);
        showBracketStatus('❌ Erro de conexão com o servidor.', 'error');
    }
}

/**
 * Botão Gerar Chaveamento
 */
btnGerarChave.addEventListener('click', async () => {
    if (!confirm('Deseja sortear e gerar o chaveamento para todas as equipes inscritas?')) return;

    showBracketStatus('🎲 Sorteando equipes e gerando chaveamento adaptativo...', 'loading');

    try {
        const base = await getBackendUrl();
        const res = await fetch(base + '/api/chaveamento/gerar', { method: 'POST' });
        const data = await res.json();

        if (res.ok && data.status === 'sucesso') {
            showBracketStatus(`✅ ${data.mensagem}`, 'success');
            renderizarChaves(data.partidas);
            setTimeout(hideBracketStatus, 4000);
        } else {
            showBracketStatus(`❌ ${data.mensagem}`, 'error');
        }
    } catch (err) {
        console.error(err);
        showBracketStatus('❌ Erro ao solicitar sorteio no servidor.', 'error');
    }
});

/**
 * Botão Resetar Chaveamento
 */
btnResetChave.addEventListener('click', async () => {
    if (!confirm('Atenção: deseja realmente zerar todo o chaveamento e placares atuais?')) return;

    try {
        const base = await getBackendUrl();
        const res = await fetch(base + '/api/chaveamento/reset', { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            showBracketStatus('Chaveamento resetado.', 'success');
            carregarChaveamento();
            setTimeout(hideBracketStatus, 3000);
        }
    } catch (err) {
        console.error(err);
    }
});

/* ---------------------------------------------------------------------
   MODAL DE SÍNTESE DO TIME (DETALHES, GOLS E JOGADORES)
   --------------------------------------------------------------------- */

async function abrirSinteseTime(timeId) {
    if (!timeId) return;

    teamModal.style.display = 'flex';
    modalContent.innerHTML = '<div style="text-align:center; padding: 40px;">⏳ Carregando síntese do time...</div>';

    try {
        const base = await getBackendUrl();
        const res = await fetch(base + `/api/times/sintese?id=${timeId}`);
        const data = await res.json();

        if (res.ok && data.status === 'sucesso') {
            const time = data.time;
            const logoSrc = time.nomeArquivoLogo && time.nomeArquivoLogo !== 'sem_logo.png'
                ? `${detectedBackend || ''}/uploads/${time.nomeArquivoLogo}`
                : 'https://placehold.co/100x100/b11226/white?text=LOGO';

            const titulares = time.jogadores ? time.jogadores.filter(j => j.titular) : [];
            const reservas = time.jogadores ? time.jogadores.filter(j => !j.titular) : [];

            modalContent.innerHTML = `
                <div class="modal-header-team">
                    <img src="${logoSrc}" alt="Logo" class="modal-logo-lg" onerror="this.src='https://placehold.co/100x100/e2e8f0/64748b?text=LOGO'">
                    <div class="modal-header-info">
                        <h2>${time.nome}</h2>
                        <p><strong>Capitão:</strong> ${time.capitao}</p>
                        <p><small>Inscrito em: ${time.dataHoraInscricao || 'Recentemente'}</small></p>
                    </div>
                </div>

                <!-- Estatísticas de Desempenho no Torneio -->
                <div class="modal-stats-grid">
                    <div class="stat-box">
                        <div class="stat-number">${data.golsFeitos}</div>
                        <div class="stat-label">Gols Feitos</div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-number">${data.vitorias}</div>
                        <div class="stat-label">Vitórias</div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-number">${data.jogosDisputados}</div>
                        <div class="stat-label">Partidas</div>
                    </div>
                </div>

                <!-- Lista de Jogadores -->
                <div class="modal-roster-title">
                    👥 Escalação Completa (${time.jogadores ? time.jogadores.length : 0} atletas)
                </div>

                <div class="modal-roster-list">
                    ${titulares.map(j => `
                        <div class="roster-item titular">
                            <span><strong>${j.nome}</strong></span>
                            <span class="roster-badge" style="background: #dcfce7; color: #166534;">${j.posicao} (Titular)</span>
                        </div>
                    `).join('')}

                    ${reservas.map(j => `
                        <div class="roster-item reserva">
                            <span>${j.nome}</span>
                            <span class="roster-badge" style="background: #fef9c3; color: #854d0e;">${j.posicao}</span>
                        </div>
                    `).join('')}

                    ${time.jogadores && time.jogadores.length === 0 ? `
                        <p style="color: #64748b; font-size: 0.9rem;">Nenhum jogador registrado para este time.</p>
                    ` : ''}
                </div>

                ${isAdminState ? `
                <!-- Ações de Gerenciamento do Time (Exclusivo Admin) -->
                <div class="modal-team-actions">
                    <button type="button" class="btn-team-action btn-team-edit" onclick="editarTime(${time.id}, '${time.nome.replace(/'/g, "\\'")}', '${time.capitao.replace(/'/g, "\\'")}')">
                        ✏️ Alterar Nome / Capitão
                    </button>
                    <button type="button" class="btn-team-action btn-team-delete" onclick="deletarTime(${time.id}, '${time.nome.replace(/'/g, "\\'")}')">
                        🗑️ Excluir Time do Torneio
                    </button>
                </div>
                ` : ''}
            `;
        } else {
            modalContent.innerHTML = `<p style="color: #dc2626;">Falha ao carregar dados do time: ${data.mensagem}</p>`;
        }
    } catch (err) {
        console.error(err);
        modalContent.innerHTML = `<p style="color: #dc2626;">Erro de conexão com o servidor.</p>`;
    }
}

function fecharModal() {
    teamModal.style.display = 'none';
    modalContent.innerHTML = '';
}

modalCloseBtn.addEventListener('click', fecharModal);
teamModal.addEventListener('click', (e) => {
    if (e.target === teamModal) fecharModal();
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && teamModal.style.display === 'flex') fecharModal();
});

/* ---------------------------------------------------------------------
   OPERAÇÕES DE GERENCIAMENTO (ALTERAR / EXCLUIR TIMES)
   --------------------------------------------------------------------- */

const registeredTeamsList = document.getElementById('registeredTeamsList');
const registeredTeamsCount = document.getElementById('registeredTeamsCount');
const btnRecarregarTimes = document.getElementById('btnRecarregarTimes');

if (btnRecarregarTimes) {
    btnRecarregarTimes.addEventListener('click', carregarListaTimesCadastrados);
}

/**
 * Carrega e lista todas as equipes cadastradas no banco de dados relacional
 */
async function carregarListaTimesCadastrados() {
    if (!registeredTeamsList) return;

    try {
        const base = await getBackendUrl();
        const res = await fetch(base + '/api/times');
        const times = await res.json();

        if (registeredTeamsCount) {
            registeredTeamsCount.textContent = times.length;
        }
        if (teamsCountBadge) {
            teamsCountBadge.textContent = `${times.length} equipes inscritas`;
        }

        if (times.length === 0) {
            registeredTeamsList.innerHTML = `
                <p style="text-align:center; color: #64748b; padding: 20px;">
                    Nenhum time cadastrado ainda. Preencha o formulário acima para inscrever o primeiro!
                </p>
            `;
            return;
        }

        registeredTeamsList.innerHTML = times.map(time => {
            const logoSrc = time.nomeArquivoLogo && time.nomeArquivoLogo !== 'sem_logo.png'
                ? `${detectedBackend || ''}/uploads/${time.nomeArquivoLogo}`
                : 'https://placehold.co/50x50/b11226/white?text=FT';

            return `
                <div class="registered-team-card">
                    <div class="registered-team-info" onclick="abrirSinteseTime(${time.id})">
                        <img src="${logoSrc}" alt="Logo" class="team-logo-thumb" onerror="this.src='https://placehold.co/50x50/e2e8f0/64748b?text=FT'">
                        <div class="registered-team-details">
                            <h4>${time.nome}</h4>
                            <p>Capitão: <strong>${time.capitao}</strong> | ${time.jogadores ? time.jogadores.length : 0} jogadores</p>
                        </div>
                    </div>
                    <div class="registered-team-actions">
                        <button type="button" class="btn-team-action btn-team-edit" onclick="editarTime(${time.id}, '${time.nome.replace(/'/g, "\\'")}', '${time.capitao.replace(/'/g, "\\'")}')" title="Alterar">
                            ✏️ Editar
                        </button>
                        <button type="button" class="btn-team-action btn-team-delete" onclick="deletarTime(${time.id}, '${time.nome.replace(/'/g, "\\'")}')" title="Excluir">
                            🗑️ Excluir
                        </button>
                    </div>
                </div>
            `;
        }).join('');

    } catch (err) {
        console.error('Erro ao listar times:', err);
        registeredTeamsList.innerHTML = `<p style="color: #dc2626; text-align: center;">Erro ao conectar com o banco de dados.</p>`;
    }
}

/**
 * Exclui um time do banco de dados (DELETE FROM times WHERE id = ?)
 */
async function deletarTime(id, nome) {
    if (!confirm(`⚠️ ATENÇÃO: Deseja realmente excluir o time "${nome}"?\nTodos os seus jogadores e dados associados serão apagados definitivamente do banco de dados.`)) {
        return;
    }

    try {
        const base = await getBackendUrl();
        const res = await fetch(base + `/api/times/deletar?id=${id}`, {
            method: 'POST'
        });
        const data = await res.json();

        if (res.ok && data.status === 'sucesso') {
            alert(`✅ ${data.mensagem}`);
            fecharModal();
            carregarListaTimesCadastrados();
            carregarChaveamento();
        } else {
            alert(`❌ Erro ao excluir: ${data.mensagem}`);
        }
    } catch (err) {
        console.error(err);
        alert('❌ Falha de comunicação com o servidor ao tentar excluir o time.');
    }
}

/**
 * Altera o nome e o capitão de um time no banco (UPDATE times SET ... WHERE id = ?)
 */
async function editarTime(id, nomeAtual, capitaoAtual) {
    const novoNome = prompt('Digite o novo nome do time:', nomeAtual);
    if (novoNome === null) return; // Cancelou

    const novoCapitao = prompt('Digite o novo nome do capitão:', capitaoAtual);
    if (novoCapitao === null) return; // Cancelou

    if (!novoNome.trim() || !novoCapitao.trim()) {
        alert('Nome do time e nome do capitão não podem ficar vazios!');
        return;
    }

    try {
        const base = await getBackendUrl();
        const params = new URLSearchParams();
        params.append('id', id);
        params.append('nome', novoNome.trim());
        params.append('capitao', novoCapitao.trim());

        const res = await fetch(base + '/api/times/atualizar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        });

        const data = await res.json();

        if (res.ok && data.status === 'sucesso') {
            alert(`✅ ${data.mensagem}`);
            fecharModal();
            carregarListaTimesCadastrados();
            carregarChaveamento();
        } else {
            alert(`❌ Erro ao atualizar: ${data.mensagem}`);
        }
    } catch (err) {
        console.error(err);
        alert('❌ Falha de comunicação com o servidor ao tentar atualizar o time.');
    }
}

// ---------------------------------------------------------------------
// FUNÇÕES DE ADMINISTRAÇÃO E AUTENTICAÇÃO
// ---------------------------------------------------------------------

/**
 * Verifica o status de autenticação do administrador e liberação do chaveamento
 */
async function verificarStatusAdmin() {
    try {
        const base = await getBackendUrl();
        const res = await fetch(base + '/api/admin/status');
        const data = await res.json();

        isAdminState = Boolean(data.adminLogado);
        isBracketReleasedState = Boolean(data.chaveamentoLiberado);

        atualizarInterfaceAdmin();
        carregarChaveamento();
    } catch (err) {
        console.warn('Não foi possível verificar status de admin:', err);
    }
}

function atualizarInterfaceAdmin() {
    if (btnOpenAdminModal) {
        btnOpenAdminModal.style.display = isAdminState ? 'none' : 'block';
    }
    if (adminLoggedBadge) {
        adminLoggedBadge.style.display = isAdminState ? 'flex' : 'none';
    }
    if (adminBracketActions) {
        adminBracketActions.style.display = isAdminState ? 'flex' : 'none';
    }
    if (adminRegisteredTeamsSection) {
        adminRegisteredTeamsSection.style.display = isAdminState ? 'block' : 'none';
        if (isAdminState) carregarListaTimesCadastrados();
    }
    if (btnToggleChaveamento) {
        if (isBracketReleasedState) {
            btnToggleChaveamento.textContent = '🔒 Bloquear para o Público';
            btnToggleChaveamento.className = 'btn-bracket-action btn-toggle-release is-released';
        } else {
            btnToggleChaveamento.textContent = '🔓 Liberar para o Público';
            btnToggleChaveamento.className = 'btn-bracket-action btn-toggle-release';
        }
    }
}

if (btnOpenAdminModal) {
    btnOpenAdminModal.addEventListener('click', () => {
        adminLoginModal.style.display = 'flex';
        adminLoginStatus.style.display = 'none';
        adminLoginForm.reset();
    });
}

if (closeAdminLoginBtn) {
    closeAdminLoginBtn.addEventListener('click', () => {
        adminLoginModal.style.display = 'none';
    });
}

if (adminLoginModal) {
    adminLoginModal.addEventListener('click', (e) => {
        if (e.target === adminLoginModal) adminLoginModal.style.display = 'none';
    });
}

// Submissão do Formulário de Login de Admin
if (adminLoginForm) {
    adminLoginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const user = document.getElementById('adminUser').value;
        const pass = document.getElementById('adminPassword').value;

        adminLoginStatus.textContent = 'Verificando credenciais...';
        adminLoginStatus.className = 'status-message loading';
        adminLoginStatus.style.display = 'block';

        try {
            const base = await getBackendUrl();
            const params = new URLSearchParams();
            params.append('usuario', user);
            params.append('senha', pass);

            const res = await fetch(base + '/api/admin/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            });
            const data = await res.json();

            if (res.ok && data.status === 'sucesso') {
                adminLoginModal.style.display = 'none';
                isAdminState = true;
                isBracketReleasedState = Boolean(data.chaveamentoLiberado);
                atualizarInterfaceAdmin();
                carregarChaveamento();
                alert('✅ Administrador autenticado com sucesso!');
            } else {
                adminLoginStatus.textContent = `❌ ${data.mensagem || 'Credenciais inválidas!'}`;
                adminLoginStatus.className = 'status-message error';
            }
        } catch (err) {
            console.error(err);
            adminLoginStatus.textContent = '❌ Erro de conexão com o servidor.';
            adminLoginStatus.className = 'status-message error';
        }
    });
}

// Logout do Administrador
if (btnLogoutAdmin) {
    btnLogoutAdmin.addEventListener('click', async () => {
        try {
            const base = await getBackendUrl();
            await fetch(base + '/api/admin/logout', { method: 'POST' });
        } catch (err) {
            console.error(err);
        }
        isAdminState = false;
        atualizarInterfaceAdmin();
        carregarChaveamento();
        alert('👋 Sessão de administrador encerrada.');
    });
}

// Botão de Liberar / Bloquear Chaveamento para o Público
if (btnToggleChaveamento) {
    btnToggleChaveamento.addEventListener('click', async () => {
        try {
            const base = await getBackendUrl();
            const res = await fetch(base + '/api/admin/toggle-chaveamento', { method: 'POST' });
            const data = await res.json();

            if (res.ok && data.status === 'sucesso') {
                isBracketReleasedState = Boolean(data.chaveamentoLiberado);
                atualizarInterfaceAdmin();
                carregarChaveamento();
                alert(`📢 ${data.mensagem}`);
            } else {
                alert(`❌ ${data.mensagem}`);
            }
        } catch (err) {
            console.error(err);
            alert('❌ Erro ao comunicar alteração de liberação com o servidor.');
        }
    });
}

// Inicializa verificação de status e chaveamento
verificarStatusAdmin();


