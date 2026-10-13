const HISTORY_KEY = "flashSaleOrderHistory";
const MAX_POLL_ATTEMPTS = 30;
const POLL_INTERVAL_MS = 2000;
const STOCK_REFRESH_INTERVAL_MS = 5000;

let pollTimer = null;
let pollAttempts = 0;
let stockAutoRefreshTimer = null;

function stockLevelClass(quantity) {
  if (quantity <= 0) return "badge--cancelled";
  if (quantity <= 10) return "badge--processing";
  return "badge--confirmed";
}

function renderStock(items) {
  const body = document.getElementById("stock-body");
  if (!items || items.length === 0) {
    body.innerHTML =
      '<tr class="empty-row"><td colspan="2">Nenhum produto configurado.</td></tr>';
    return;
  }
  body.innerHTML = items
    .map(
      (item) => `
        <tr>
          <td>${item.produto_id}</td>
          <td><span class="badge ${stockLevelClass(item.quantidade_disponivel)}">${item.quantidade_disponivel} unidades</span></td>
        </tr>`,
    )
    .join("");
}

async function fetchStock() {
  try {
    const response = await fetch("/v1/stock");
    if (!response.ok) {
      throw new Error("stock unavailable");
    }
    renderStock(await response.json());
  } catch (error) {
    document.getElementById("stock-body").innerHTML =
      '<tr class="empty-row"><td colspan="2">Não foi possível carregar o estoque (backend fora do ar?).</td></tr>';
  }
}

function startStockAutoRefresh() {
  stopStockAutoRefresh();
  stockAutoRefreshTimer = setInterval(fetchStock, STOCK_REFRESH_INTERVAL_MS);
}

function stopStockAutoRefresh() {
  if (stockAutoRefreshTimer) {
    clearInterval(stockAutoRefreshTimer);
    stockAutoRefreshTimer = null;
  }
}

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [];
  } catch (error) {
    return [];
  }
}

function saveHistory(history) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
}

function upsertHistory(order) {
  const history = loadHistory();
  const index = history.findIndex((item) => item.pedido_id === order.pedido_id);
  if (index >= 0) {
    history[index] = { ...history[index], ...order };
  } else {
    history.unshift(order);
  }
  saveHistory(history);
  renderHistory();
}

function statusBadge(status) {
  const normalized = (status || "").toLowerCase();
  const label = status || "desconhecido";
  return `<span class="badge badge--${normalized || "error"}">${label}</span>`;
}

function renderHistory() {
  const history = loadHistory();
  const body = document.getElementById("history-body");
  if (history.length === 0) {
    body.innerHTML =
      '<tr class="empty-row"><td colspan="6">Nenhum pedido criado ainda.</td></tr>';
    return;
  }
  body.innerHTML = history
    .map(
      (order) => `
        <tr>
          <td><code>${order.pedido_id}</code></td>
          <td>${order.produto_id || "-"}</td>
          <td>${order.quantidade ?? "-"}</td>
          <td>${statusBadge(order.status)}</td>
          <td>${order.data_hora_requisicao || order.data_hora_atualizacao || "-"}</td>
          <td><button class="link-button" data-pedido-id="${order.pedido_id}">consultar</button></td>
        </tr>`,
    )
    .join("");

  body.querySelectorAll("button[data-pedido-id]").forEach((button) => {
    button.addEventListener("click", () => {
      document.getElementById("pedido_id").value = button.dataset.pedidoId;
      queryOrder(button.dataset.pedidoId);
    });
  });
}

async function checkHealth() {
  const healthEl = document.getElementById("health");
  try {
    const response = await fetch("/actuator/health");
    if (response.ok) {
      healthEl.textContent = "Backend online";
      healthEl.className = "health health--up";
    } else {
      throw new Error("unhealthy");
    }
  } catch (error) {
    healthEl.textContent =
      "Backend offline (suba a infraestrutura e o checkout-service)";
    healthEl.className = "health health--down";
  }
}

async function createOrder(event) {
  event.preventDefault();
  const payload = {
    produto_id: document.getElementById("produto_id").value.trim(),
    quantidade: Number(document.getElementById("quantidade").value),
    cliente_cpf: document.getElementById("cliente_cpf").value.trim(),
    cliente_nome: document.getElementById("cliente_nome").value.trim(),
  };

  const resultEl = document.getElementById("create-result");
  resultEl.innerHTML = "<em>Enviando…</em>";

  try {
    const response = await fetch("/v1/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = await response.json().catch(() => ({}));

    if (response.status === 202) {
      resultEl.innerHTML = `${statusBadge(data.status)} pedido <code>${data.pedido_id}</code> criado às ${data.data_hora_requisicao}`;
      upsertHistory({ ...payload, ...data });
      document.getElementById("pedido_id").value = data.pedido_id;
      fetchStock();
    } else {
      resultEl.innerHTML = `<span class="badge badge--error">HTTP ${response.status}</span> ${data.mensagem || "Erro desconhecido"}`;
    }
  } catch (error) {
    resultEl.innerHTML = `<span class="badge badge--error">Falha de rede</span> ${error.message}`;
  }
}

async function fetchOrder(pedidoId) {
  const response = await fetch(`/v1/orders/${encodeURIComponent(pedidoId)}`);
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, data };
}

function renderQueryResult({ ok, status, data }) {
  const resultEl = document.getElementById("query-result");
  if (ok) {
    resultEl.innerHTML = `
      ${statusBadge(data.status)}
      <pre>${JSON.stringify(data, null, 2)}</pre>`;
  } else {
    resultEl.innerHTML = `<span class="badge badge--error">HTTP ${status}</span> ${data.mensagem || "Erro desconhecido"}`;
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  pollAttempts = 0;
}

async function queryOrder(pedidoIdOverride) {
  const pedidoId =
    pedidoIdOverride || document.getElementById("pedido_id").value.trim();
  if (!pedidoId) {
    return;
  }

  const result = await fetchOrder(pedidoId);
  renderQueryResult(result);

  if (result.ok) {
    upsertHistory(result.data);
  }

  const autoRefresh = document.getElementById("auto-refresh").checked;
  const isProcessing = result.ok && result.data.status === "PROCESSING";

  stopPolling();
  if (autoRefresh && isProcessing) {
    pollTimer = setInterval(async () => {
      pollAttempts += 1;
      const polled = await fetchOrder(pedidoId);
      renderQueryResult(polled);
      if (polled.ok) {
        upsertHistory(polled.data);
      }
      if (
        !polled.ok ||
        polled.data.status !== "PROCESSING" ||
        pollAttempts >= MAX_POLL_ATTEMPTS
      ) {
        stopPolling();
      }
    }, POLL_INTERVAL_MS);
  }
}

document.getElementById("create-form").addEventListener("submit", createOrder);
document.getElementById("query-form").addEventListener("submit", (event) => {
  event.preventDefault();
  stopPolling();
  queryOrder();
});
document.getElementById("refresh-stock").addEventListener("click", fetchStock);
document
  .getElementById("auto-refresh-stock")
  .addEventListener("change", (event) => {
    if (event.target.checked) {
      startStockAutoRefresh();
    } else {
      stopStockAutoRefresh();
    }
  });

checkHealth();
setInterval(checkHealth, 15000);
renderHistory();
fetchStock();
if (document.getElementById("auto-refresh-stock").checked) {
  startStockAutoRefresh();
}
