const $ = (selector) => document.querySelector(selector);
let statusData = null;
let plannedPayload = null;
let plannedWorkflow = null;
let marketplaceData = null;
let marketplaceFilter = "all";

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char]));
}

async function api(path, options = {}) {
  const headers = {"X-FRCTL-Request": "1", ...(options.headers || {})};
  const response = await fetch(path, {...options, headers, credentials: "same-origin"});
  const body = await response.json();
  if (!response.ok) {
    const error = new Error(body.message || body.error || `HTTP ${response.status}`);
    error.output = body.output || "";
    throw error;
  }
  return body;
}

function connectorDescription(id) {
  return {
    ssh: "Интерактивная SSH-сессия без пароля в API",
    rustdesk: "Удалённый рабочий стол с авторизацией в RustDesk",
    docker: "Одноразовая песочница без сети и host mounts",
    browser: "Разрешённый web-workspace в системном браузере"
  }[id];
}

function connectorState(item) {
  if (item.installed) return "ГОТОВ";
  if (item.id === "docker" && item.providers?.docker_cli) return "DOCKER НЕ ЗАПУЩЕН";
  return "КЛИЕНТ НЕ НАЙДЕН";
}

const marketCategoryNames = {all:"Все",android:"Android",ai:"ИИ и модели",security:"Безопасность","remote-access":"Удалённый доступ",tools:"Инструменты",media:"Медиа"};

function compactNumber(value) {
  const number = Number(value || 0);
  if (number >= 1000000) return `${(number / 1000000).toFixed(1)}M`;
  if (number >= 1000) return `${(number / 1000).toFixed(1)}K`;
  return String(number);
}

function renderMarketplace() {
  if (!marketplaceData) return;
  const query = $("#market-search").value.trim().toLowerCase();
  const entries = marketplaceData.entries.filter(item => {
    const categoryMatch = marketplaceFilter === "all" || (marketplaceFilter === "android" ? item.kind === "android" : item.category === marketplaceFilter);
    const text = `${item.name} ${item.owner} ${item.description} ${item.source}`.toLowerCase();
    return categoryMatch && (!query || text.includes(query));
  });
  $("#market-categories").innerHTML = marketplaceData.categories.map(category => `<button class="market-chip ${marketplaceFilter === category ? "active" : ""}" data-category="${escapeHtml(category)}">${escapeHtml(marketCategoryNames[category] || category)}</button>`).join("");
  $("#market-summary").textContent = `${entries.length} позиций · ${marketplaceData.sources.join(" + ")} · ${marketplaceData.cached ? "кэш" : "live"} · ${new Date(marketplaceData.generated_at).toLocaleString("ru-RU")}`;
  $("#market-state").textContent = marketplaceData.degraded ? "OFFLINE CACHE" : "LIVE · 5 МИН";
  $("#market-state").className = `pill ${marketplaceData.degraded ? "wait" : "ok"}`;
  $("#market-grid").innerHTML = entries.length ? entries.map(item => `<a class="market-card ${item.kind === "ai-model" ? "model" : ""}" href="${escapeHtml(item.url)}" target="_blank" rel="noopener noreferrer">
    <span class="market-icon">${item.kind === "ai-model" ? "AI" : escapeHtml(item.name.slice(0,1).toUpperCase())}</span>
    <span class="market-kind">${item.kind === "ai-model" ? "ИИ-МОДЕЛЬ" : "ANDROID OSS"} · ${escapeHtml(marketCategoryNames[item.category] || item.category)}</span>
    <h3>${escapeHtml(item.name)}</h3><p>${escapeHtml(item.description)}</p>
    <span class="market-meta">${escapeHtml(item.owner)} · ${item.kind === "ai-model" ? `↓ ${compactNumber(item.downloads)} · ♥ ${compactNumber(item.score)}` : `★ ${compactNumber(item.score)}`}</span>
  </a>`).join("") : `<div class="market-empty">По этому фильтру ничего не найдено.</div>`;
  document.querySelectorAll(".market-chip").forEach(button => button.addEventListener("click", () => { marketplaceFilter = button.dataset.category; renderMarketplace(); }));
}

async function loadMarketplace() {
  $("#market-state").textContent = "ОБНОВЛЕНИЕ";
  $("#market-state").className = "pill wait";
  try { marketplaceData = await api("/api/marketplace"); renderMarketplace(); }
  catch (error) { $("#market-state").textContent = "НЕДОСТУПНО"; $("#market-summary").textContent = error.message; }
}

function renderStatus(data) {
  statusData = data;
  $("#node-state").textContent = "NODE READY";
  $("#node-state").className = "pill ok";
  $("#version").textContent = `v${data.version}`;
  $("#audit-state").textContent = data.audit_verified ? "VERIFIED" : "BROKEN";
  $("#allowlist").innerHTML = data.allowed_targets.map(item => `<span class="tag">${escapeHtml(item)}</span>`).join("");
  $("#image").innerHTML = data.allowed_docker_images.map(item => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`).join("");
  $("#identity-file").innerHTML = `<option value="">Без ключа</option>` + (data.identity_files || []).map(item => `<option value="${escapeHtml(item.path)}">${escapeHtml(item.name)}</option>`).join("");
  $("#workflow-identity").innerHTML = `<option value="">Выберите ключ</option>` + (data.identity_files || []).map(item => `<option value="${escapeHtml(item.path)}">${escapeHtml(item.name)}</option>`).join("");
  $("#workflow-profile").innerHTML = (data.workflows || []).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}${item.mutating ? " · изменяет /tmp" : " · read-only"}</option>`).join("");
  $("#plink-state").textContent = data.plink_ready ? "PLINK ГОТОВ" : "PLINK НЕ НАЙДЕН";
  $("#plink-state").className = `pill ${data.plink_ready ? "ok" : "wait"}`;
  if ((data.pinned_targets || []).length === 1) {
    $("#workflow-target").value = data.pinned_targets[0];
    $("#workflow-user").value = (data.ssh_users || {})[data.pinned_targets[0]] || "";
  }
  if ((data.identity_files || []).length === 1) $("#workflow-identity").value = data.identity_files[0].path;
  $("#catalog").innerHTML = data.connectors.map((item, index) => `
    <article class="card ${index === 0 ? "active" : ""}" data-id="${escapeHtml(item.id)}" tabindex="0">
      <span>${["⌁","↗","⬡","◎"][index]}</span><h3>${escapeHtml(item.name)}</h3><p>${escapeHtml(connectorDescription(item.id))}</p>
      <span class="status ${item.installed ? "" : "missing"}">${connectorState(item)}</span>
    </article>`).join("");
  $("#connector").innerHTML = data.connectors.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join("");
  document.querySelectorAll(".card").forEach(card => {
    const select = () => { $("#connector").value = card.dataset.id; connectorChanged(); };
    card.addEventListener("click", select);
    card.addEventListener("keydown", event => { if (event.key === "Enter") select(); });
  });
  connectorChanged();
}

function connectorChanged() {
  const value = $("#connector").value;
  document.querySelectorAll(".card").forEach(card => card.classList.toggle("active", card.dataset.id === value));
  $("#ssh-fields").hidden = value !== "ssh";
  $("#image-row").hidden = value !== "docker";
  $("#target-row").hidden = value === "docker";
  const defaults = {ssh:"server.example",rustdesk:"123456789",browser:"portal.example"};
  if (defaults[value]) $("#target").value = defaults[value];
  plannedPayload = null;
  $("#launch").disabled = true;
  $("#plan").textContent = "План ещё не сформирован.";
  sshClientChanged();
}

function sshClientChanged() {
  const enabled = $("#connector").value === "ssh" && $("#ssh-client").value === "putty";
  $("#identity-file").disabled = !enabled;
  if (!enabled) $("#identity-file").value = "";
}

function payload() {
  const form = new FormData($("#launcher"));
  return {
    connector: form.get("connector"), action: form.get("connector") === "docker" ? "open-workspace" : "connect",
    target: form.get("target"), client: form.get("client"), user: form.get("user"), image: form.get("image"), identity_file: form.get("identity_file")
  };
}

function showPlan(plan) {
  $("#plan").textContent = [
    `${plan.title}\n`, `Клиент: ${plan.installed ? "установлен" : "НЕ НАЙДЕН"}`,
    `Команда: ${plan.command_preview.join(" ")}\n`, ...plan.controls.map((item, index) => `${index + 1}. ${item}`),
    "\nПеред запуском появится отдельное системное подтверждение."
  ].join("\n");
}

function message(text, kind = "") { $("#message").textContent = text; $("#message").className = `message ${kind}`; }

$("#launcher").addEventListener("submit", async event => {
  event.preventDefault();
  plannedPayload = null; $("#launch").disabled = true; message("Проверяю policy…");
  try {
    const current = payload();
    const plan = await api("/api/plan", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(current)});
    plannedPayload = current; showPlan(plan); $("#launch").disabled = !plan.installed;
    message(plan.installed ? "План разрешён. Нажмите «Запустить» и подтвердите системное окно." : "План разрешён, но локальный клиент не установлен.", plan.installed ? "success" : "error");
  } catch (error) { message(error.message, "error"); $("#plan").textContent = `POLICY DENIED\n${error.message}`; }
});

$("#launch").addEventListener("click", async () => {
  if (!plannedPayload) return;
  $("#launch").disabled = true; message("Ожидается подтверждение в системном окне…");
  try {
    const result = await api("/api/launch", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(plannedPayload)});
    message(result.launched ? `Клиент запущен. PID: ${result.pid || "system"}` : "Запуск отменён пользователем.", result.launched ? "success" : "error");
    await loadAudit();
  } catch (error) { message(error.message, "error"); }
  finally { $("#launch").disabled = false; }
});

async function loadAudit() {
  const data = await api("/api/audit");
  $("#audit-state").textContent = data.verified ? "VERIFIED" : "BROKEN";
  $("#audit").innerHTML = data.records.length ? data.records.slice().reverse().map(item => `
    <div class="audit-row"><span>${escapeHtml(item.at)}</span><b>${escapeHtml(item.connector)}</b><span>${escapeHtml(item.target_hash.slice(0,16))}…</span><span class="${["launched","executed"].includes(item.result) ? "good" : "bad"}">${escapeHtml(item.result)}</span></div>`).join("") : "Записей пока нет.";
}

function workflowPayload() {
  const form = new FormData($("#workflow-form"));
  return {
    target: form.get("target"),
    user: form.get("user"),
    identity_file: form.get("identity_file"),
    workflow: form.get("workflow")
  };
}

function showWorkflowPlan(plan) {
  $("#workflow-plan").textContent = [
    `${plan.name} → ${plan.user}@${plan.target}`,
    `PPK: ${plan.identity}`,
    `Host key: ${plan.host_key}`,
    `Изменяет сервер: ${plan.mutating ? "ДА — только описанный /tmp файл" : "НЕТ"}`,
    "",
    ...plan.commands.map((command, index) => `${index + 1}. ${command}`),
    "",
    "Произвольные команды: ЗАПРЕЩЕНЫ"
  ].join("\n");
}

function messageWorkflow(text, kind = "") {
  $("#workflow-message").textContent = text;
  $("#workflow-message").className = `message ${kind}`;
}

$("#workflow-form").addEventListener("submit", async event => {
  event.preventDefault();
  plannedWorkflow = null;
  $("#workflow-run").disabled = true;
  messageWorkflow("Проверяю target, PPK, fingerprint и workflow…");
  try {
    const current = workflowPayload();
    const plan = await api("/api/workflow/plan", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(current)});
    plannedWorkflow = current;
    showWorkflowPlan(plan);
    $("#workflow-run").disabled = !plan.installed;
    messageWorkflow(plan.installed ? "Workflow разрешён. Выполнение потребует системного подтверждения." : "Plink не установлен.", plan.installed ? "success" : "error");
  } catch (error) {
    messageWorkflow(error.message, "error");
    $("#workflow-plan").textContent = `POLICY DENIED\n${error.message}`;
  }
});

$("#workflow-run").addEventListener("click", async () => {
  if (!plannedWorkflow) return;
  $("#workflow-run").disabled = true;
  messageWorkflow("Ожидается нативное подтверждение…");
  try {
    const result = await api("/api/workflow/run", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(plannedWorkflow)});
    if (result.executed) {
      $("#workflow-output").textContent = result.output || "Workflow выполнен без текстового вывода.";
      messageWorkflow(`Выполнено за ${result.duration_ms} мс. Квитанция: ${result.receipt.slice(0,16)}…`, "success");
    } else {
      messageWorkflow("Workflow отменён пользователем.", "error");
    }
    await loadAudit();
  } catch (error) {
    $("#workflow-output").textContent = error.output || error.message;
    messageWorkflow(error.message, "error");
  } finally {
    $("#workflow-run").disabled = false;
  }
});

async function load() {
  try { renderStatus(await api("/api/status")); await Promise.all([loadAudit(), loadMarketplace()]); }
  catch (error) { $("#node-state").textContent = "NODE ERROR"; message(error.message, "error"); }
}

$("#connector").addEventListener("change", connectorChanged);
$("#ssh-client").addEventListener("change", sshClientChanged);
$("#refresh").addEventListener("click", load);
$("#audit-refresh").addEventListener("click", loadAudit);
$("#market-refresh").addEventListener("click", loadMarketplace);
$("#market-search").addEventListener("input", renderMarketplace);
setInterval(loadMarketplace, 5 * 60 * 1000);
load();
