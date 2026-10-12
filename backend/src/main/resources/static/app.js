/**
 * Verified RAG Knowledge Assistant - Frontend Client
 */

// Determine API Base URL dynamically
const API_BASE = window.location.origin.includes(':8080') ? '' : 'http://localhost:8080';

// Global state
let currentUnansweredFilter = false;

document.addEventListener('DOMContentLoaded', () => {
    setupDragAndDrop();
    checkBackendHealth();
    loadDocuments();
});

/* ==========================================================================
   Tab Navigation
   ========================================================================== */
function switchTab(tab) {
    const assistantTab = document.getElementById('viewAssistant');
    const adminTab = document.getElementById('viewAdmin');
    const tabAssistantBtn = document.getElementById('tabAssistantBtn');
    const tabAdminBtn = document.getElementById('tabAdminBtn');

    if (tab === 'assistant') {
        assistantTab.style.display = 'block';
        adminTab.style.display = 'none';
        tabAssistantBtn.classList.add('active');
        tabAdminBtn.classList.remove('active');
    } else {
        assistantTab.style.display = 'none';
        adminTab.style.display = 'block';
        tabAssistantBtn.classList.remove('active');
        tabAdminBtn.classList.add('active');
        loadHistory(currentUnansweredFilter);
    }
}

/* ==========================================================================
   Health Check & Connectivity
   ========================================================================== */
async function checkBackendHealth() {
    const statusEl = document.getElementById('connectionStatus');
    const statusText = statusEl.querySelector('.status-text');

    try {
        const response = await fetch(`${API_BASE}/api/documents`, { method: 'GET' });
        if (response.ok) {
            statusEl.classList.add('online');
            statusText.textContent = 'Backend Online (Port 8080)';
        } else {
            statusEl.classList.remove('online');
            statusText.textContent = 'Backend Offline';
        }
    } catch (e) {
        statusEl.classList.remove('online');
        statusText.textContent = 'Backend Offline';
    }
}

/* ==========================================================================
   Document Management (Upload & Ingestion)
   ========================================================================== */
function setupDragAndDrop() {
    const dropZone = document.getElementById('dropZone');

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.add('dragover');
        });
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
        });
    });

    dropZone.addEventListener('drop', (e) => {
        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            uploadFile(files[0]);
        }
    });
}

function handleFileSelect(event) {
    const file = event.target.files[0];
    if (file) {
        uploadFile(file);
    }
}

async function uploadFile(file) {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
        showToast('Invalid file format. Please select a PDF file (.pdf)', 'error');
        return;
    }

    const progressBar = document.getElementById('uploadProgress');
    const statusText = document.getElementById('uploadStatusText');
    progressBar.style.display = 'flex';
    statusText.textContent = `Ingesting & embedding '${file.name}'...`;

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch(`${API_BASE}/api/documents`, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (response.ok) {
            showToast(`Successfully ingested '${data.fileName}' (v${data.version}, ${data.pageCount} pages, ${data.chunkCount} chunks)`, 'success');
            loadDocuments();
        } else {
            showToast(data.message || 'Failed to upload document', 'error');
        }
    } catch (error) {
        showToast(`Upload failed: ${error.message}. Ensure backend is running.`, 'error');
    } finally {
        progressBar.style.display = 'none';
        document.getElementById('pdfFileInput').value = '';
    }
}

async function loadDocuments() {
    try {
        const response = await fetch(`${API_BASE}/api/documents`);
        if (!response.ok) return;

        const documents = await response.json();
        const tbody = document.getElementById('docsTableBody');
        const badge = document.getElementById('docCountBadge');

        badge.textContent = `${documents.length} uploaded`;

        if (documents.length === 0) {
            tbody.innerHTML = `
                <tr class="empty-row">
                    <td colspan="5">No documents ingested yet. Upload a policy PDF to begin.</td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = documents.map(doc => `
            <tr>
                <td><strong>${escapeHtml(doc.fileName)}</strong></td>
                <td><span class="version-pill">v${doc.version}</span></td>
                <td>${doc.pageCount}</td>
                <td>${doc.chunkCount}</td>
                <td>${formatDate(doc.uploadedAt)}</td>
            </tr>
        `).join('');

    } catch (error) {
        console.error('Error loading documents:', error);
    }
}

/* ==========================================================================
   Question Answering (Ask Controller)
   ========================================================================== */
function fillQuestion(text) {
    const input = document.getElementById('questionInput');
    input.value = text;
    input.focus();
}

async function handleAskQuestion(event) {
    event.preventDefault();
    const questionInput = document.getElementById('questionInput');
    const question = questionInput.value.trim();
    if (!question) return;

    const btn = document.getElementById('btnAsk');
    const btnText = btn.querySelector('.btn-text');
    const spinner = btn.querySelector('.btn-spinner');
    const container = document.getElementById('answerContainer');

    btn.disabled = true;
    btnText.textContent = 'Searching & Verifying...';
    spinner.style.display = 'block';

    try {
        const response = await fetch(`${API_BASE}/api/ask`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        const data = await response.json();

        if (response.ok) {
            renderAnswer(data);
        } else {
            showToast(data.message || 'Error processing question', 'error');
        }
    } catch (error) {
        showToast(`Request failed: ${error.message}. Is backend running on port 8080?`, 'error');
    } finally {
        btn.disabled = false;
        btnText.textContent = 'Ask Verified Assistant';
        spinner.style.display = 'none';
    }
}

function renderAnswer(data) {
    const container = document.getElementById('answerContainer');
    const statusPill = document.getElementById('answerStatusPill');
    const statusText = document.getElementById('answerStatusText');
    const confidenceVal = document.getElementById('confidenceValue');
    const answerBody = document.getElementById('answerBodyText');
    const citationsSec = document.getElementById('citationsSection');
    const citationsList = document.getElementById('citationsList');

    container.style.display = 'block';

    const confidencePct = Math.round(data.confidence * 100);
    confidenceVal.textContent = `${confidencePct}% (${data.confidence})`;

    if (data.answered) {
        // Answered: Sufficient supporting evidence
        statusPill.className = 'answer-status-pill verified';
        statusText.textContent = 'Verified Grounded Answer';

        answerBody.innerHTML = `<p>${escapeHtml(data.answer)}</p>`;

        if (data.citations && data.citations.length > 0) {
            citationsSec.style.display = 'block';
            citationsList.innerHTML = data.citations.map((c, i) => `
                <div class="citation-card">
                    <div class="citation-meta">
                        <span class="citation-doc">Source [${i + 1}]: ${escapeHtml(c.document)}</span>
                        <span class="citation-page">Page ${c.page}</span>
                    </div>
                    <div class="citation-snippet">"${escapeHtml(c.snippet)}"</div>
                </div>
            `).join('');
        } else {
            citationsSec.style.display = 'none';
        }

    } else {
        // Refused: Score below threshold
        statusPill.className = 'answer-status-pill refused';
        statusText.textContent = 'Refused - Evidence Below Threshold';

        answerBody.innerHTML = `
            <p><strong>${escapeHtml(data.answer)}</strong></p>
            <div class="answer-refused-callout">
                <strong>Safety Guardrail Active:</strong> The semantic relevance score (${data.confidence}) is below the configured threshold (0.65). 
                To ensure 100% verified accuracy and prevent hallucinations, the language model was not prompted to speculate.
            </div>
        `;
        citationsSec.style.display = 'none';
    }

    container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

/* ==========================================================================
   Admin View (Audit Log & Refusals)
   ========================================================================== */
function refreshAdminLogs() {
    loadHistory(currentUnansweredFilter);
}

async function loadHistory(unansweredOnly) {
    currentUnansweredFilter = unansweredOnly;

    document.getElementById('btnFilterAll').classList.toggle('active', !unansweredOnly);
    document.getElementById('btnFilterUnanswered').classList.toggle('active', unansweredOnly);

    const tbody = document.getElementById('historyTableBody');
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;">Loading logs...</td></tr>`;

    const endpoint = unansweredOnly ? `${API_BASE}/api/unanswered` : `${API_BASE}/api/history`;

    try {
        const response = await fetch(endpoint);
        if (!response.ok) return;

        const logs = await response.json();

        if (logs.length === 0) {
            tbody.innerHTML = `
                <tr class="empty-row">
                    <td colspan="7">No query logs found matching criteria.</td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = logs.map(log => `
            <tr>
                <td>#${log.id}</td>
                <td><strong>${escapeHtml(log.question)}</strong></td>
                <td><span style="font-size:0.8rem;">${escapeHtml(truncate(log.answer, 80))}</span></td>
                <td>
                    <span class="status-badge ${log.answered ? 'answered' : 'refused'}">
                        ${log.answered ? 'Answered' : 'Refused'}
                    </span>
                </td>
                <td><code>${log.topScore !== null ? log.topScore : 'N/A'}</code></td>
                <td>${log.latencyMs} ms</td>
                <td>${formatDate(log.createdAt)}</td>
            </tr>
        `).join('');

    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:var(--refusal-rose);">Failed to load history logs: ${error.message}</td></tr>`;
    }
}

/* ==========================================================================
   Utilities
   ========================================================================== */
function showToast(message, type = 'info') {
    const toast = document.getElementById('toastNotification');
    toast.textContent = message;
    toast.className = `toast ${type}`;
    toast.style.display = 'block';

    setTimeout(() => {
        toast.style.display = 'none';
    }, 4500);
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function truncate(text, max) {
    if (!text) return '';
    return text.length > max ? text.substring(0, max) + '...' : text;
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
