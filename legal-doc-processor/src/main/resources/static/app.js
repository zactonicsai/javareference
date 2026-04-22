// ------------------------------------------------------------------
// OpenLayers USA map
// ------------------------------------------------------------------
const vectorSource = new ol.source.Vector();
const vectorLayer = new ol.layer.Vector({
    source: vectorSource,
    style: new ol.style.Style({
        image: new ol.style.Circle({
            radius: 8,
            fill: new ol.style.Fill({ color: '#dc2626' }),
            stroke: new ol.style.Stroke({ color: 'white', width: 2 })
        })
    })
});

const map = new ol.Map({
    target: 'map',
    layers: [
        new ol.layer.Tile({ source: new ol.source.OSM() }),
        vectorLayer
    ],
    view: new ol.View({
        center: ol.proj.fromLonLat([-96.0, 39.5]),
        zoom: 4
    })
});

map.on('click', (evt) => {
    const lonLat = ol.proj.toLonLat(evt.coordinate);
    document.getElementById('latitude').value = lonLat[1].toFixed(6);
    document.getElementById('longitude').value = lonLat[0].toFixed(6);

    vectorSource.clear();
    vectorSource.addFeature(new ol.Feature({
        geometry: new ol.geom.Point(evt.coordinate)
    }));
});

// ------------------------------------------------------------------
// Upload form
// ------------------------------------------------------------------
document.getElementById('uploadForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const submitBtn = document.getElementById('submitBtn');
    const statusEl = document.getElementById('uploadStatus');
    const resultPanel = document.getElementById('resultPanel');
    const resultContent = document.getElementById('resultContent');

    const fileInput = document.getElementById('file');
    if (!fileInput.files.length) {
        statusEl.textContent = 'Please choose a file.';
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('subject', document.getElementById('subject').value);

    const lat = document.getElementById('latitude').value;
    const lon = document.getElementById('longitude').value;
    if (lat) formData.append('latitude', lat);
    if (lon) formData.append('longitude', lon);
    const desc = document.getElementById('locationDescription').value;
    if (desc) formData.append('locationDescription', desc);

    submitBtn.disabled = true;
    statusEl.textContent = 'Uploading...';
    statusEl.className = 'text-sm text-blue-600';

    try {
        const resp = await fetch('/api/upload', { method: 'POST', body: formData });
        const body = await resp.json();

        if (resp.ok) {
            statusEl.textContent = 'Queued for processing.';
            statusEl.className = 'text-sm text-green-600';
            resultPanel.classList.remove('hidden');
            resultContent.textContent = JSON.stringify(body, null, 2);
            // The workflow runs async; give it a beat, then refresh the table.
            // Multiple attempts handle the indexing-latency tail even with refresh=True
            // (the workflow itself has its own latency: S3 + Temporal + per-page + ES).
            scheduleTableRefresh();
        } else {
            statusEl.textContent = 'Error: ' + (body.error || resp.statusText);
            statusEl.className = 'text-sm text-red-600';
        }
    } catch (err) {
        statusEl.textContent = 'Network error: ' + err.message;
        statusEl.className = 'text-sm text-red-600';
    } finally {
        submitBtn.disabled = false;
    }
});

// After an upload, nudge the archive list a few times so the user sees their
// doc appear once the workflow finishes indexing it.
function scheduleTableRefresh() {
    const delays = [1500, 4000, 9000];
    delays.forEach(ms => setTimeout(loadAllDocuments, ms));
}

// ------------------------------------------------------------------
// Archive table: list, search, download, view-text
// ------------------------------------------------------------------
function fmtBytes(n) {
    if (n == null) return '-';
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
    if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB';
    return (n / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

function fmtDate(iso) {
    if (!iso) return '-';
    try {
        const d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleString();
    } catch (_) { return iso; }
}

// Avoid XSS — every field that comes from user uploads is rendered via textContent
// or the escape helper below, never directly into innerHTML.
function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function renderTable(docs) {
    const tbody = document.getElementById('docsTableBody');
    const summary = document.getElementById('docsSummary');

    if (!docs || docs.length === 0) {
        tbody.innerHTML = `
            <tr><td colspan="7" class="text-center text-slate-400 py-6">
                No documents found.
            </td></tr>`;
        summary.textContent = '0 documents';
        return;
    }

    summary.textContent = `${docs.length} document${docs.length === 1 ? '' : 's'}`;

    tbody.innerHTML = docs.map(d => {
        const id = escapeHtml(d.documentId || '');
        return `
            <tr class="border-b border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2 font-medium text-slate-800">${escapeHtml(d.fileName || '(unnamed)')}</td>
                <td class="px-3 py-2 text-slate-600">${escapeHtml(d.subject || '-')}</td>
                <td class="px-3 py-2 text-xs text-slate-500">${escapeHtml(d.fileType || '-')}</td>
                <td class="px-3 py-2 text-right text-slate-600">${escapeHtml(fmtBytes(d.fileSize))}</td>
                <td class="px-3 py-2 text-right text-slate-600">${d.pageCount != null ? d.pageCount : '-'}</td>
                <td class="px-3 py-2 text-xs text-slate-500">${escapeHtml(fmtDate(d.uploadDateTime))}</td>
                <td class="px-3 py-2">
                    <a href="/api/documents/${id}/download"
                       class="inline-block bg-slate-100 hover:bg-slate-200 text-slate-700
                              px-2 py-1 rounded text-xs mr-1">
                        Download
                    </a>
                    <button type="button"
                            data-action="view-text"
                            data-doc-id="${id}"
                            data-file-name="${escapeHtml(d.fileName || '')}"
                            class="bg-blue-50 hover:bg-blue-100 text-blue-700
                                   px-2 py-1 rounded text-xs">
                        View text
                    </button>
                </td>
            </tr>`;
    }).join('');
}

// One delegated click handler for every "View text" button — simpler than
// re-wiring listeners every time the table re-renders.
document.getElementById('docsTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action="view-text"]');
    if (!btn) return;
    openTextModal(btn.dataset.docId, btn.dataset.fileName);
});

async function loadAllDocuments() {
    const summary = document.getElementById('docsSummary');
    summary.textContent = 'Loading…';
    try {
        const resp = await fetch('/api/search/all?size=200');
        if (!resp.ok) {
            summary.textContent = 'Could not load documents (HTTP ' + resp.status + ')';
            return;
        }
        renderTable(await resp.json());
    } catch (err) {
        summary.textContent = 'Could not load documents: ' + err.message;
    }
}

async function runSearch(query, mode) {
    const summary = document.getElementById('docsSummary');
    summary.textContent = 'Searching…';
    try {
        const url = mode === 'keyword'
            ? '/api/search/keyword?q=' + encodeURIComponent(query)
            : '/api/search?q=' + encodeURIComponent(query);
        const resp = await fetch(url);
        if (!resp.ok) {
            summary.textContent = 'Search failed (HTTP ' + resp.status + ')';
            return;
        }
        const docs = await resp.json();
        renderTable(docs);
        summary.textContent = `${docs.length} result${docs.length === 1 ? '' : 's'} for "${query}" (${mode})`;
    } catch (err) {
        summary.textContent = 'Search error: ' + err.message;
    }
}

document.getElementById('searchForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const q = document.getElementById('searchQuery').value.trim();
    const mode = document.getElementById('searchMode').value;
    if (!q) { loadAllDocuments(); return; }
    runSearch(q, mode);
});

document.getElementById('clearSearchBtn').addEventListener('click', () => {
    document.getElementById('searchQuery').value = '';
    loadAllDocuments();
});

document.getElementById('refreshBtn').addEventListener('click', loadAllDocuments);

// ------------------------------------------------------------------
// Extracted-text modal
// ------------------------------------------------------------------
function openModal() { document.getElementById('textModal').classList.remove('hidden'); }
function closeModal() { document.getElementById('textModal').classList.add('hidden'); }

document.getElementById('closeModalBtn').addEventListener('click', closeModal);
document.getElementById('closeModalBtn2').addEventListener('click', closeModal);
document.getElementById('textModal').addEventListener('click', (e) => {
    // Clicking the dimmed backdrop (but not the inner card) closes the modal.
    if (e.target.id === 'textModal') closeModal();
});

document.getElementById('copyTextBtn').addEventListener('click', async () => {
    const txt = document.getElementById('modalText').textContent;
    try {
        await navigator.clipboard.writeText(txt);
        const btn = document.getElementById('copyTextBtn');
        const original = btn.textContent;
        btn.textContent = 'Copied ✓';
        setTimeout(() => { btn.textContent = original; }, 1500);
    } catch (_) { /* some browsers block clipboard in insecure contexts */ }
});

async function openTextModal(documentId, fileName) {
    document.getElementById('modalTitle').textContent = 'Extracted Text';
    document.getElementById('modalSubtitle').textContent = fileName || documentId;
    const pre = document.getElementById('modalText');
    pre.textContent = 'Loading…';
    openModal();

    try {
        const resp = await fetch('/api/documents/' + encodeURIComponent(documentId) + '/text');
        if (!resp.ok) {
            pre.textContent = 'Could not fetch text (HTTP ' + resp.status + ').';
            return;
        }
        const body = await resp.text();
        pre.textContent = body.length === 0 ? '(No extracted text for this document.)' : body;
    } catch (err) {
        pre.textContent = 'Error: ' + err.message;
    }
}

// ------------------------------------------------------------------
// Service health: WebSocket + fallback polling
// ------------------------------------------------------------------
const serviceLabels = {
    s3Available: 's3',
    sqsAvailable: 'sqs',
    temporalAvailable: 'temporal',
    elasticsearchAvailable: 'elasticsearch'
};

function applyStatus(status) {
    let downServices = [];

    Object.entries(serviceLabels).forEach(([key, id]) => {
        const dot = document.getElementById('dot-' + id);
        if (!dot) return;
        dot.classList.remove('status-unknown', 'status-ok', 'status-bad');
        if (status[key]) {
            dot.classList.add('status-ok');
        } else {
            dot.classList.add('status-bad');
            downServices.push(id);
        }
    });

    const banner = document.getElementById('serviceBanner');
    const bannerDetail = document.getElementById('bannerDetail');
    const submitBtn = document.getElementById('submitBtn');

    if (downServices.length > 0) {
        banner.classList.remove('hidden');
        bannerDetail.textContent = 'Unavailable: ' + downServices.join(', ');
        submitBtn.disabled = true;
    } else {
        banner.classList.add('hidden');
        submitBtn.disabled = false;
    }

    document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString();
}

let ws;
let pollTimer;
function connectWebSocket() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    ws = new WebSocket(`${proto}//${location.host}/ws/health`);

    ws.onopen = () => {
        document.getElementById('wsStatus').textContent = 'websocket \u2713';
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    };
    ws.onmessage = (e) => {
        try { applyStatus(JSON.parse(e.data)); } catch (err) { console.error(err); }
    };
    ws.onclose = () => {
        document.getElementById('wsStatus').textContent = 'reconnecting...';
        startPollingFallback();
        setTimeout(connectWebSocket, 5000);
    };
    ws.onerror = () => {
        document.getElementById('wsStatus').textContent = 'error';
    };
}

function startPollingFallback() {
    if (pollTimer) return;
    pollTimer = setInterval(async () => {
        try {
            const resp = await fetch('/api/health/services');
            if (resp.ok) applyStatus(await resp.json());
        } catch (_) { /* ignore */ }
    }, 5000);
}

connectWebSocket();

// Initial load of the archive table.
loadAllDocuments();
