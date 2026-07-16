const $ = (selector) => document.querySelector(selector);
const sequenceKey = "frctl.local.sequence.v1";
const telemetryKey = "frctl.local.telemetry.v1";
const marketCacheKey = "frctl.marketplace.cache.v1";
const marketTtlMs = 5 * 60 * 1000;
const categories = [
  ["all", "Все"], ["android", "Android"], ["ai", "ИИ и модели"],
  ["security", "Безопасность"], ["remote-access", "Удалённый доступ"],
  ["tools", "Инструменты"], ["media", "Медиа"]
];
const connectors = [
  {id:"ssh",name:"SSH / PuTTY",risk:"medium",description:"Plan an SSH session and hand it to an installed OSS client."},
  {id:"rustdesk",name:"RustDesk",risk:"high",description:"Plan a consent-based remote desktop session without embedding credentials."},
  {id:"browser",name:"Browser workspace",risk:"low",description:"Open an isolated web workspace in Chromium or Gecko."},
  {id:"sandbox",name:"Ephemeral sandbox",risk:"medium",description:"Describe an isolated disposable workspace with deny-by-default policy."}
];
const marketState = {items: [], category: "all", query: "", updatedAt: null, cached: false};

function read(key, fallback=[]) { try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); } catch { return fallback; } }
function learn(action) {
  const items = [...read(sequenceKey), action].slice(-100);
  localStorage.setItem(sequenceKey, JSON.stringify(items));
  const counts = {}; const current = items.at(-1);
  for (let i=0;i<items.length-1;i+=1) if(items[i]===current) counts[items[i+1]]=(counts[items[i+1]]||0)+1;
  const next = Object.entries(counts).sort((a,b)=>b[1]-a[1])[0]?.[0];
  if (next) $("#suggestion").textContent = next;
}
function emit(name, properties={}) {
  learn(name); if (!$("#telemetry-consent").checked) return;
  const allowed = {name,properties:{connector:properties.connector,scope:properties.scope,enabled:properties.enabled},at:new Date().toISOString()};
  localStorage.setItem(telemetryKey, JSON.stringify([...read(telemetryKey),allowed].slice(-100)));
}
function targetInfo(value) {
  const clean=value.trim();
  if(!clean||clean.length>255||/[\s@\\]/.test(clean)) throw new Error("Цель должна быть hostname/IP без credentials");
  const parsed=new URL(clean.includes("://")?clean:`ssh://${clean}`);
  if(parsed.username||parsed.password||parsed.search||parsed.hash) throw new Error("Credentials и query‑параметры запрещены");
  if(!["ssh:","http:","https:","rustdesk:"].includes(parsed.protocol)) throw new Error("Схема не поддерживается");
  const host=parsed.hostname;
  const privateScope=/^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)?"private":host.endsWith(".example")||host.endsWith(".test")?"documentation":"hostname";
  return {display:parsed.port?`${host}:${parsed.port}`:host,scope:privateScope};
}
function plan(payload) {
  const connector=connectors.find(item=>item.id===payload.connector); if(!connector) throw new Error("Неизвестный коннектор");
  const target=targetInfo(payload.target);
  return {connector,target,controls:["Confirm the operator owns or is authorized to access the target","Create an ephemeral workspace with no host filesystem mounts","Keep credentials in the local OS credential vault","Require explicit confirmation in the installed local node","Write a redacted local receipt and destroy the workspace on exit"]};
}
function classify(text, kind) {
  const value = text.toLowerCase();
  if(kind === "ai-model" || /\b(ai|llm|machine learning|model|agent)\b/.test(value)) return "ai";
  if(/security|privacy|vpn|firewall|auth/.test(value)) return "security";
  if(/remote|ssh|rdp|rustdesk|server/.test(value)) return "remote-access";
  if(/camera|audio|video|music|media/.test(value)) return "media";
  if(/android|apk/.test(value)) return "android";
  return "tools";
}
function githubItem(repo) {
  const text = `${repo.name || ""} ${repo.description || ""} ${(repo.topics || []).join(" ")}`;
  return {id:`github:${repo.full_name}`,source:"GitHub",kind:"android-app",category:classify(text,"android-app"),name:repo.full_name,description:repo.description||"Open-source Android project",url:repo.html_url,updatedAt:repo.updated_at,stars:Number(repo.stargazers_count)||0,downloads:0,tag:repo.language||"Android"};
}
function hfItem(model) {
  const id=model.id||model.modelId; const tags=Array.isArray(model.tags)?model.tags:[];
  return {id:`hf:${id}`,source:"Hugging Face",kind:"ai-model",category:"ai",name:id,description:`${model.pipeline_tag||"AI model"}${tags.length?` · ${tags.slice(0,3).join(", ")}`:""}`,url:`https://huggingface.co/${encodeURI(id)}`,updatedAt:model.lastModified,stars:Number(model.likes)||0,downloads:Number(model.downloads)||0,tag:model.pipeline_tag||"model"};
}
async function fetchJson(url) {
  const response=await fetch(url,{headers:{Accept:"application/json"}}); if(!response.ok) throw new Error(`${response.status} ${response.statusText}`); return response.json();
}
async function refreshMarketplace(force=false) {
  const cached=read(marketCacheKey,null);
  if(!force && cached?.items?.length && Date.now()-Date.parse(cached.updatedAt)<marketTtlMs) {
    Object.assign(marketState,{items:cached.items,updatedAt:cached.updatedAt,cached:true}); renderMarketplace(); return;
  }
  setMarketStatus("CATALOG LOADING","pending");
  try {
    const [repos,models]=await Promise.all([
      fetchJson("https://api.github.com/search/repositories?q=topic%3Aandroid+stars%3A%3E500&sort=updated&order=desc&per_page=24"),
      fetchJson("https://huggingface.co/api/models?sort=lastModified&direction=-1&limit=24&full=false")
    ]);
    const items=[...(repos.items||[]).map(githubItem),...(models||[]).map(hfItem)]; const updatedAt=new Date().toISOString();
    Object.assign(marketState,{items,updatedAt,cached:false}); localStorage.setItem(marketCacheKey,JSON.stringify({items,updatedAt})); renderMarketplace();
  } catch(error) {
    if(cached?.items?.length) { Object.assign(marketState,{items:cached.items,updatedAt:cached.updatedAt,cached:true}); renderMarketplace(); $("#market-message").textContent=`Сеть недоступна: показан последний локальный снимок. ${error.message}`; }
    else { setMarketStatus("CATALOG OFFLINE","pending"); $("#market-message").textContent=`Каталог временно недоступен: ${error.message}`; $("#marketplace").replaceChildren(); }
  }
}
function setMarketStatus(text, className) { const node=$("#market-status"); node.textContent=text; node.className=`badge ${className}`; }
function appendMarketCard(item) {
  const card=document.createElement("article"); card.className="market-card";
  const source=document.createElement("span"); source.className="source"; source.textContent=`${item.source} · ${item.category}`;
  const title=document.createElement("h3"); title.textContent=item.name;
  const description=document.createElement("p"); description.textContent=item.description;
  const meta=document.createElement("div"); meta.className="meta"; meta.textContent=`★ ${item.stars.toLocaleString("ru-RU")} · ↓ ${item.downloads.toLocaleString("ru-RU")} · ${item.tag}`;
  const link=document.createElement("a"); link.href=item.url; link.target="_blank"; link.rel="noopener noreferrer"; link.textContent="Открыть источник ↗";
  card.append(source,title,description,meta,link); $("#marketplace").append(card);
}
function renderMarketplace() {
  const q=marketState.query.trim().toLowerCase();
  const items=marketState.items.filter(item=>(marketState.category==="all"||item.category===marketState.category)&&(!q||`${item.name} ${item.description} ${item.tag}`.toLowerCase().includes(q)));
  $("#marketplace").replaceChildren(); items.forEach(appendMarketCard); $("#market-count").textContent=marketState.items.length;
  $("#market-updated").textContent=marketState.updatedAt?`Обновлено ${new Date(marketState.updatedAt).toLocaleTimeString("ru-RU",{hour:"2-digit",minute:"2-digit"})}`:"Нет данных";
  $("#market-message").textContent=items.length?`${items.length} элементов · обновление каждые 5 минут`:"По выбранному фильтру ничего не найдено";
  setMarketStatus(marketState.cached?"CACHED CATALOG":"LIVE CATALOG",marketState.cached?"pending":"live");
  document.querySelectorAll(".category").forEach(node=>node.classList.toggle("active",node.dataset.category===marketState.category));
}
function selectConnector(id) { $("#connector").value=id; document.querySelectorAll(".card").forEach(node=>node.classList.toggle("active",node.dataset.id===id)); emit("connector_select",{connector:id}); }
function renderConnectors() {
  $("#connector-count").textContent=connectors.length; const catalog=$("#connector-catalog"); catalog.replaceChildren();
  connectors.forEach((item,index)=>{const card=document.createElement("article"); card.className=`card ${index===0?"active":""}`; card.dataset.id=item.id; card.tabIndex=0; const icon=document.createElement("span"); icon.className="icon"; icon.textContent=["⌁","↗","◎","⬡"][index]; const title=document.createElement("h3"); title.textContent=item.name; const desc=document.createElement("p"); desc.textContent=item.description; const risk=document.createElement("span"); risk.className="risk"; risk.textContent=`risk · ${item.risk}`; card.append(icon,title,desc,risk); card.addEventListener("click",()=>selectConnector(item.id)); card.addEventListener("keydown",event=>{if(event.key==="Enter")selectConnector(item.id);}); catalog.append(card);});
  $("#connector").replaceChildren(...connectors.map(item=>{const option=document.createElement("option"); option.value=item.id; option.textContent=item.name; return option;})); learn("catalog_open");
}
$("#market-categories").replaceChildren(...categories.map(([id,label])=>{const button=document.createElement("button"); button.type="button"; button.className=`category ${id==="all"?"active":""}`; button.dataset.category=id; button.textContent=label; button.addEventListener("click",()=>{marketState.category=id;renderMarketplace();}); return button;}));
$("#market-search").addEventListener("input",event=>{marketState.query=event.target.value;renderMarketplace();});
$("#planner").addEventListener("submit",event=>{event.preventDefault();const form=new FormData(event.currentTarget);try{const result=plan({connector:form.get("connector"),target:form.get("target")});$("#plan").textContent=[`DRY-RUN · ${result.connector.name} · ${result.target.display}`,...result.controls.map((line,i)=>`${i+1}. ${line}`),"\nExecution requires a separately installed, authenticated local node."].join("\n");emit("plan_created",{connector:result.connector.id,scope:result.target.scope});}catch(error){$("#plan").textContent=`Ошибка: ${error.message}`;}});
$("#reset-learning").addEventListener("click",()=>{localStorage.removeItem(sequenceKey);$("#suggestion").textContent="Локальная история очищена";});
$("#telemetry-consent").addEventListener("change",event=>{if(event.target.checked)emit("consent_change",{enabled:true});});
renderConnectors(); refreshMarketplace(); setInterval(()=>refreshMarketplace(true),marketTtlMs);
