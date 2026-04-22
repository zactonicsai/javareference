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
        // Center of the continental USA (approx)
        center: ol.proj.fromLonLat([-96.0, 39.5]),
        zoom: 4
    })
});

map.on('click', (evt) => {
    const lonLat = ol.proj.toLonLat(evt.coordinate);
    const lon = lonLat[0].toFixed(6);
    const lat = lonLat[1].toFixed(6);

    document.getElementById('latitude').value = lat;
    document.getElementById('longitude').value = lon;

    vectorSource.clear();
    vectorSource.addFeature(new ol.Feature({
        geometry: new ol.geom.Point(evt.coordinate)
    }));
});

// ------------------------------------------------------------------
// Upload form handling
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
        document.getElementById('wsStatus').textContent = 'websocket ✓';
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
