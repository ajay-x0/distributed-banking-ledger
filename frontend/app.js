"use strict";

const ACCOUNTS = {
  alice: { id: "00000000-0000-0000-0000-000000000001", name: "Alice" },
  bob: { id: "00000000-0000-0000-0000-000000000002", name: "Bob" },
  charlie: { id: "00000000-0000-0000-0000-000000000003", name: "Charlie" }
};
const TERMINAL = new Set(["COMPLETED", "REJECTED", "MANUAL_REVIEW"]);
const tokens = new Map();
const state = { actor: "alice", lastRequest: null, before: null, pollAbort: null, observedStates: [] };

const $ = (id) => document.getElementById(id);
const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2 });
const number = new Intl.NumberFormat("en-IN");

function minorToMoney(value) { return money.format(Number(value || 0) / 100); }
function newKey() { return crypto.randomUUID ? crypto.randomUUID() : `demo-${Date.now()}-${Math.random().toString(16).slice(2)}`; }

function rupeesToMinor(input) {
  const value = String(input).trim().replace(/,/g, "");
  if (!/^\d+(\.\d{0,2})?$/.test(value)) throw new Error("Enter a positive amount with no more than two decimal places.");
  const [whole, fraction = ""] = value.split(".");
  const minor = BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
  if (minor < 1n || minor > 100000000000n) throw new Error("Amount must be between ₹0.01 and ₹1,000,000,000.");
  return Number(minor);
}

async function getToken(subject, force = false) {
  const saved = tokens.get(subject);
  if (!force && saved && saved.expiresAt * 1000 > Date.now() + 30_000) return saved.token;
  const response = await fetch(`/demo/token/${subject}`, { cache: "no-store" });
  if (!response.ok) throw new Error(`Demo authentication failed (${response.status}).`);
  const issued = await response.json();
  tokens.set(subject, issued);
  return issued.token;
}

async function api(path, options = {}, subject = state.actor) {
  const token = await getToken(subject);
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  if (options.body) headers.set("Content-Type", "application/json");
  const started = performance.now();
  let response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    headers.set("Authorization", `Bearer ${await getToken(subject, true)}`);
    response = await fetch(path, { ...options, headers });
  }
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { status: response.status, body, durationMs: Math.round(performance.now() - started) };
}

function accountOwner(accountId) {
  return Object.entries(ACCOUNTS).find(([, account]) => account.id === accountId)?.[0];
}

async function fetchBalance(owner) {
  const account = ACCOUNTS[owner];
  const result = await api(`/api/accounts/${account.id}/balance`, {}, owner);
  if (result.status !== 200) throw new Error(`Could not read ${account.name}'s balance (${result.status}).`);
  return { owner, ...account, ...result.body };
}

async function allBalances() {
  return Promise.all(Object.keys(ACCOUNTS).map(fetchBalance));
}

function renderBalances(balances) {
  $("balanceGrid").innerHTML = balances.map(balance => `
    <article class="balance-card">
      <div class="owner"><span>${balance.name}</span><span class="owner-tag">${balance.currency}</span></div>
      <div class="balance-value">${minorToMoney(balance.balanceMinor)}</div>
      <div class="balance-meta">
        <div>Reserved<strong>${minorToMoney(balance.reservedMinor)}</strong></div>
        <div>Available<strong>${minorToMoney(balance.availableMinor)}</strong></div>
      </div>
    </article>`).join("");
}

async function refreshBalances(showSkeleton = false) {
  if (showSkeleton) $("balanceGrid").innerHTML = '<div class="balance-card skeleton"></div>'.repeat(3);
  try {
    const balances = await allBalances();
    renderBalances(balances);
    setRuntime(true, "Gateway connected");
    return balances;
  } catch (error) {
    setRuntime(false, "Gateway unavailable");
    $("balanceGrid").innerHTML = `<article class="balance-card"><strong>Could not load accounts</strong><p>${escapeHtml(error.message)}</p></article>`;
    throw error;
  }
}

function setRuntime(online, label) {
  $("statusDot").className = `status-dot ${online ? "online" : "offline"}`;
  $("runtimeStatus").textContent = label;
}

function fillAccounts() {
  const options = Object.entries(ACCOUNTS).map(([owner, account]) => `<option value="${account.id}">${account.name} · …${account.id.slice(-4)}</option>`).join("");
  $("source").innerHTML = options;
  $("destination").innerHTML = options;
  syncSourceToActor();
}

function syncSourceToActor() {
  if (ACCOUNTS[state.actor]) $("source").value = ACCOUNTS[state.actor].id;
  const alternate = Object.values(ACCOUNTS).find(account => account.id !== $("source").value);
  if ($("destination").value === $("source").value || !$("destination").value) $("destination").value = alternate.id;
}

function setScenario(name) {
  document.querySelectorAll(".scenario").forEach(button => button.classList.toggle("active", button.dataset.scenario === name));
  $("currency").value = "INR";
  syncSourceToActor();
  if (name === "success") $("amount").value = "10000.00";
  if (name === "fraud") $("amount").value = "20000.01";
  if (name === "insufficient") $("amount").value = "1000000000.00";
  if (name === "invalid") $("destination").value = $("source").value;
  $("idempotencyKey").value = newKey();
  updateMinorPreview();
}

function updateMinorPreview() {
  try { $("minorPreview").textContent = `${number.format(rupeesToMinor($("amount").value))} paise`; }
  catch { $("minorPreview").textContent = "Enter a valid amount"; }
}

function transferPayload(amountOverride) {
  return {
    source: $("source").value,
    destination: $("destination").value,
    amountMinor: amountOverride ?? rupeesToMinor($("amount").value),
    currency: $("currency").value.trim()
  };
}

function inspectRequest(method, path, headers, body) {
  $("requestInspector").textContent = `${method} ${path}\n${Object.entries(headers).map(([key, value]) => `${key}: ${value}`).join("\n")}\n\n${body ? JSON.stringify(body, null, 2) : ""}`;
}

function inspectResponse(result) {
  $("responseInspector").textContent = `HTTP ${result.status}\nDuration: ${result.durationMs} ms\n\n${JSON.stringify(result.body, null, 2)}`;
}

function showPayment(payment, status) {
  $("emptyResult").hidden = true;
  $("resultContent").hidden = false;
  $("paymentId").textContent = payment.id || "—";
  $("httpStatus").textContent = status || "200";
  $("attempts").textContent = payment.attempts ?? 0;
  const current = payment.state || "NEW";
  if (!state.observedStates.includes(current)) state.observedStates.push(current);
  $("paymentState").textContent = current;
  $("paymentState").className = `state-badge ${current === "COMPLETED" ? "success" : current === "REJECTED" || current === "MANUAL_REVIEW" ? "failed" : "running"}`;
  renderTimeline(current);
  const reason = payment.reason || payment.lastError;
  $("reason").hidden = !reason;
  $("reason").textContent = reason ? `Reason: ${reason}` : "";
}

function renderTimeline(current) {
  $("timeline").innerHTML = state.observedStates.map((item, position) => `<li><strong>${item}</strong>${position === state.observedStates.length - 1 ? "Current durable state" : "Observed state"}</li>`).join("");
}

async function submitTransfer({ repeat = false, conflict = false } = {}) {
  if (state.actor === "admin") return setMessage("Select Alice, Bob, or Charlie to create a customer transfer.", true);
  if (state.pollAbort) state.pollAbort.abort();
  if ((repeat || conflict) && (!state.lastRequest || state.lastRequest.actor !== state.actor)) {
    return setMessage("Return to the identity that created the original request before testing its idempotency key.", true);
  }
  const payload = repeat || conflict ? { ...state.lastRequest.payload } : transferPayload();
  if (conflict) payload.amountMinor += 1;
  const key = repeat || conflict ? state.lastRequest.key : $("idempotencyKey").value.trim();
  if (!key) return setMessage("Generate an idempotency key first.", true);

  $("submitTransfer").disabled = true;
  setMessage(conflict ? "Submitting changed input with the same key…" : repeat ? "Repeating the exact request…" : "Capturing balances and submitting…");
  try {
    if (!repeat && !conflict) {
      state.before = await allBalances();
      state.observedStates = [];
    }
    inspectRequest("POST", "/api/payments", { Authorization: `Bearer <${state.actor} demo token>`, "Idempotency-Key": key, "Content-Type": "application/json" }, payload);
    const result = await api("/api/payments", { method: "POST", headers: { "Idempotency-Key": key }, body: JSON.stringify(payload) });
    inspectResponse(result);
    if (result.status !== 202) {
      setMessage(describeError(result), true);
      $("paymentState").textContent = `HTTP ${result.status}`;
      $("paymentState").className = "state-badge failed";
      return;
    }

    state.lastRequest = { payload, key, paymentId: result.body.id, actor: state.actor };
    $("repeatTransfer").disabled = false;
    $("conflictTransfer").disabled = false;
    showPayment(result.body, result.status);
    if (repeat) {
      setMessage(`Safe retry returned payment ${result.body.id}. No second payment was created.`);
      return;
    }
    await pollPayment(result.body.id);
  } catch (error) {
    setMessage(error.message, true);
  } finally {
    $("submitTransfer").disabled = false;
  }
}

async function pollPayment(paymentId) {
  const controller = new AbortController();
  state.pollAbort = controller;
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline && !controller.signal.aborted) {
    await delay(900);
    const result = await api(`/api/payments/${paymentId}`, { signal: controller.signal });
    inspectResponse(result);
    if (result.status !== 200) throw new Error(describeError(result));
    showPayment(result.body, result.status);
    if (TERMINAL.has(result.body.state)) {
      const after = await refreshBalances();
      renderComparison(state.before, after);
      setMessage(`Payment reached ${result.body.state}. Durable balances were refreshed.`);
      return;
    }
  }
  if (!controller.signal.aborted) setMessage("Polling stopped after 90 seconds. Inspect Payment and Ledger logs.", true);
}

function renderComparison(before, after) {
  if (!before || !after) return;
  const relevant = new Set([state.lastRequest.payload.source, state.lastRequest.payload.destination]);
  const cards = after.filter(item => relevant.has(item.id)).map(current => {
    const previous = before.find(item => item.id === current.id);
    const delta = Number(current.balanceMinor) - Number(previous.balanceMinor);
    return `<article class="comparison-card">
      <h3>${current.name}<span class="delta ${delta > 0 ? "positive" : delta < 0 ? "negative" : ""}">${delta === 0 ? "No posted change" : `${delta > 0 ? "+" : ""}${minorToMoney(delta)}`}</span></h3>
      <div class="balance-flow"><div><span>Before</span><strong>${minorToMoney(previous.balanceMinor)}</strong></div><b>→</b><div><span>After</span><strong>${minorToMoney(current.balanceMinor)}</strong></div></div>
    </article>`;
  });
  $("comparisonGrid").innerHTML = cards.join("");
  $("comparison").hidden = false;
}

async function refreshAdmin() {
  try {
    const [recon, analytics] = await Promise.all([
      api("/api/admin/reconciliation", {}, "admin"),
      api("/api/admin/analytics", {}, "admin")
    ]);
    if (recon.status !== 200 || analytics.status !== 200) throw new Error("Admin endpoints are unavailable.");
    $("reconciliation").innerHTML = Object.entries(recon.body).map(([name, values]) => `<div class="health-line"><span>${name}</span><strong class="${values.length ? "unhealthy" : "healthy"}">${values.length ? `${values.length} issue(s)` : "Healthy"}</strong></div>`).join("");
    $("analytics").innerHTML = analytics.body.length ? analytics.body.map(metric => `<div class="metric-line"><span>${metric.name}</span><strong>${number.format(metric.value)}</strong></div>`).join("") : '<p class="section-note">No terminal events projected yet.</p>';
  } catch (error) {
    $("reconciliation").textContent = error.message;
    $("analytics").textContent = error.message;
  }
}

function describeError(result) {
  return result.body?.detail || result.body?.message || result.body?.error || `Request failed with HTTP ${result.status}.`;
}
function setMessage(message, error = false) { $("formMessage").textContent = message; $("formMessage").className = `form-message${error ? " error" : ""}`; }
function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
function escapeHtml(value) { const div = document.createElement("div"); div.textContent = value; return div.innerHTML; }

async function changeIdentity() {
  state.actor = $("identity").value;
  $("identityScope").textContent = `${state.actor === "admin" ? "admin" : "payments"} scope`;
  $("adminSection").hidden = state.actor !== "admin";
  document.querySelectorAll(".transfer-panel input, .transfer-panel select, .transfer-panel button").forEach(control => control.disabled = state.actor === "admin");
  if (state.actor !== "admin") {
    syncSourceToActor();
    const ownsLastRequest = state.lastRequest?.actor === state.actor;
    $("repeatTransfer").disabled = !ownsLastRequest;
    $("conflictTransfer").disabled = !ownsLastRequest;
  } else {
    await refreshAdmin();
  }
  try { await getToken(state.actor); setRuntime(true, `Connected as ${state.actor}`); }
  catch { setRuntime(false, "Authentication unavailable"); }
}

function bindEvents() {
  $("identity").addEventListener("change", changeIdentity);
  $("refreshBalances").addEventListener("click", () => refreshBalances(true));
  $("refreshAdmin").addEventListener("click", refreshAdmin);
  $("newKey").addEventListener("click", () => $("idempotencyKey").value = newKey());
  $("amount").addEventListener("input", updateMinorPreview);
  $("source").addEventListener("change", () => { if ($("source").value === $("destination").value) setMessage("Source and destination must be different unless testing invalid input.", true); });
  document.querySelectorAll(".scenario").forEach(button => button.addEventListener("click", () => setScenario(button.dataset.scenario)));
  $("transferForm").addEventListener("submit", event => { event.preventDefault(); submitTransfer(); });
  $("repeatTransfer").addEventListener("click", () => submitTransfer({ repeat: true }));
  $("conflictTransfer").addEventListener("click", () => submitTransfer({ conflict: true }));
  $("clearInspector").addEventListener("click", () => { $("requestInspector").textContent = "No request yet."; $("responseInspector").textContent = "No response yet."; });
}

async function init() {
  fillAccounts();
  $("idempotencyKey").value = newKey();
  updateMinorPreview();
  bindEvents();
  await changeIdentity();
  await refreshBalances(true).catch(() => {});
}

window.addEventListener("DOMContentLoaded", init);
