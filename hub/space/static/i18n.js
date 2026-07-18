(() => {
  const messages = {
    ru: {
      hero_title: "Одно рабочее место.<br><span>Нулевое неявное доверие.</span>",
      hero_text: "Планировщик OSS‑подключений, песочниц и агентских workflow. Публичная демо‑версия ничего не выполняет на удалённых узлах.",
      connectors: "Коннекторы", safe_plan: "Безопасный план", connector: "Коннектор", target: "Цель", action: "Действие",
      make_plan: "Сформировать dry-run", plan_empty: "План появится здесь. Команды не выполняются.",
      adaptive: "Адаптивная подсказка", adaptive_text: "Последовательности действий обучаются только в этом браузере через локальную частотную модель.",
      next: "Следующее действие", no_history: "Пока недостаточно локальной истории", reset: "Сбросить локальное обучение",
      catalog: "Приложения и ИИ-модели", search: "Поиск", execution_off: "ВЫПОЛНЕНИЕ ОТКЛЮЧЕНО"
    },
    en: {
      hero_title: "One workspace.<br><span>Zero implicit trust.</span>",
      hero_text: "An OSS connection, sandbox and agent workflow planner. The public demo executes nothing on remote hosts.",
      connectors: "Connectors", safe_plan: "Safe plan", connector: "Connector", target: "Target", action: "Action",
      make_plan: "Create dry-run", plan_empty: "The plan will appear here. No commands are executed.",
      adaptive: "Adaptive suggestion", adaptive_text: "Action sequences are learned only in this browser using a local frequency model.",
      next: "Next action", no_history: "Not enough local history yet", reset: "Reset local learning",
      catalog: "Apps and AI models", search: "Search", execution_off: "EXECUTION OFF"
    },
    zh: {
      hero_title: "一个工作空间。<br><span>零隐式信任。</span>", hero_text: "OSS 连接、沙箱和代理工作流规划器。公开演示不会在远程主机上执行任何操作。",
      connectors: "连接器", safe_plan: "安全计划", connector: "连接器", target: "目标", action: "操作", make_plan: "创建 dry-run", plan_empty: "计划将在此显示，不会执行命令。",
      adaptive: "自适应建议", adaptive_text: "操作序列仅通过本地频率模型在此浏览器中学习。", next: "下一步", no_history: "本地历史记录不足", reset: "重置本地学习",
      catalog: "应用与 AI 模型", search: "搜索", execution_off: "执行已关闭"
    },
    de: {
      hero_title: "Ein Arbeitsplatz.<br><span>Kein implizites Vertrauen.</span>", hero_text: "Planer für OSS-Verbindungen, Sandboxes und Agenten-Workflows. Die öffentliche Demo führt nichts auf entfernten Hosts aus.",
      connectors: "Konnektoren", safe_plan: "Sicherer Plan", connector: "Konnektor", target: "Ziel", action: "Aktion", make_plan: "Dry-run erstellen", plan_empty: "Der Plan erscheint hier. Es werden keine Befehle ausgeführt.",
      adaptive: "Adaptive Empfehlung", adaptive_text: "Aktionsfolgen werden nur in diesem Browser mit einem lokalen Häufigkeitsmodell gelernt.", next: "Nächste Aktion", no_history: "Noch nicht genug lokale Historie", reset: "Lokales Lernen zurücksetzen",
      catalog: "Apps und KI-Modelle", search: "Suche", execution_off: "AUSFÜHRUNG AUS"
    },
    es: {
      hero_title: "Un espacio de trabajo.<br><span>Cero confianza implícita.</span>", hero_text: "Planificador de conexiones OSS, sandboxes y flujos de agentes. La demo pública no ejecuta nada en hosts remotos.",
      connectors: "Conectores", safe_plan: "Plan seguro", connector: "Conector", target: "Objetivo", action: "Acción", make_plan: "Crear dry-run", plan_empty: "El plan aparecerá aquí. No se ejecutan comandos.",
      adaptive: "Sugerencia adaptativa", adaptive_text: "Las secuencias se aprenden solo en este navegador mediante un modelo local de frecuencia.", next: "Siguiente acción", no_history: "Aún no hay suficiente historial local", reset: "Restablecer aprendizaje local",
      catalog: "Apps y modelos de IA", search: "Buscar", execution_off: "EJECUCIÓN DESACTIVADA"
    }
  };
  const supported = Object.keys(messages);
  const saved = localStorage.getItem("frctl.language");
  let language = supported.includes(saved) ? saved : (supported.includes(navigator.language.slice(0,2)) ? navigator.language.slice(0,2) : "en");
  function t(key) { return messages[language]?.[key] || messages.en[key] || key; }
  function apply() {
    document.documentElement.lang = language;
    document.querySelectorAll("[data-i18n]").forEach(node => { node.textContent = t(node.dataset.i18n); });
    document.querySelectorAll("[data-i18n-html]").forEach(node => { node.innerHTML = t(node.dataset.i18nHtml); });
    const picker = document.querySelector("#language-select"); if (picker) picker.value = language;
  }
  window.frctlT = t;
  window.frctlSetLanguage = value => { if (supported.includes(value)) { localStorage.setItem("frctl.language", value); location.reload(); } };
  document.addEventListener("DOMContentLoaded", () => {
    const picker = document.querySelector("#language-select");
    if (picker) picker.addEventListener("change", event => window.frctlSetLanguage(event.target.value));
    apply();
  });
})();
