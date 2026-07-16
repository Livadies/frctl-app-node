const $ = (selector) => document.querySelector(selector);
const historyKey = "frctl.local.sequence.v1";
let connectors = [];

function sequence() {
  try { return JSON.parse(localStorage.getItem(historyKey) || "[]"); } catch { return []; }
}

function learn(action) {
  const items = [...sequence(), action].slice(-100);
  localStorage.setItem(historyKey, JSON.stringify(items));
  renderSuggestion(items);
}

function renderSuggestion(items = sequence()) {
  if (items.length < 2) return;
  const current = items.at(-1);
  const counts = {};
  for (let i = 0; i < items.length - 1; i += 1) {
    if (items[i] === current) counts[items[i + 1]] = (counts[items[i + 1]] || 0) + 1;
  }
  const next = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0];
  if (next) $("#suggestion").textContent = next;
}

async function api(path, options = {}) {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) throw new Error(body.message || body.error || `HTTP ${response.status}`);
  return body;
}

async function emit(name, properties = {}) {
  learn(name);
  if (!$("#telemetry-consent").checked) return;
  await api("/api/telemetry", {
    method: "POST", headers: {"Content-Type": "application/json"},
    body: JSON.stringify({consent: true, name, properties})
  }).catch(() => {});
}

function selectConnector(id) {
  $("#connector").value = id;
  document.querySelectorAll(".card").forEach((node) => node.classList.toggle("active", node.dataset.id === id));
  emit("connector_select", {connector: id});
}

async function load() {
  const [{connectors: values}, health] = await Promise.all([api("/api/catalog"), api("/api/health")]);
  connectors = values;
  $("#connector-count").textContent = connectors.length;
  $("#health").textContent = `control plane ${health.status} · v${health.version}`;
  $("#catalog").innerHTML = connectors.map((item, index) => `
    <article class="card ${index === 0 ? "active" : ""}" data-id="${item.id}" tabindex="0">
      <span class="icon">${["⌁","↗","◎","⬡"][index] || "◇"}</span><h3>${item.name}</h3>
      <p>${item.description}</p><span class="risk">risk · ${item.risk}</span>
    </article>`).join("");
  $("#connector").innerHTML = connectors.map((item) => `<option value="${item.id}">${item.name}</option>`).join("");
  document.querySelectorAll(".card").forEach((card) => {
    card.addEventListener("click", () => selectConnector(card.dataset.id));
    card.addEventListener("keydown", (event) => { if (event.key === "Enter") selectConnector(card.dataset.id); });
  });
  emit("catalog_open", {count: connectors.length});
}

$("#planner").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  $("#plan").textContent = "Проверяю policy…";
  try {
    const plan = await api("/api/plan", {
      method: "POST", headers: {"Content-Type": "application/json"},
      body: JSON.stringify({connector: form.get("connector"), target: form.get("target"), action: form.get("action"), dry_run: true})
    });
    $("#plan").textContent = [
      `${plan.mode.toUpperCase()} · ${plan.connector.name} · ${plan.target.display}`,
      ...plan.controls.map((line, i) => `${i + 1}. ${line}`), `\n${plan.next_boundary}`
    ].join("\n");
    emit("plan_created", {connector: plan.connector.id, scope: plan.target.scope});
  } catch (error) { $("#plan").textContent = `Ошибка: ${error.message}`; }
});

$("#reset-learning").addEventListener("click", () => {
  localStorage.removeItem(historyKey);
  $("#suggestion").textContent = "Локальная история очищена";
});
$("#telemetry-consent").addEventListener("change", (event) => {
  if (event.target.checked) emit("consent_change", {enabled: true});
});
renderSuggestion();
load().catch((error) => { $("#health").textContent = `offline: ${error.message}`; });

