import { api } from "./api.js";

const statusText = {
  PENDING: "正在扣库存",
  RESERVED: "库存已留，待付款",
  REJECTED: "库存不足",
  PAID: "已付款",
  RELEASING: "正在退回库存",
  CANCELLED: "已取消，库存已退回"
};

const state = {
  token: sessionStorage.getItem("ink-token") || "",
  account: JSON.parse(sessionStorage.getItem("ink-account") || "null"),
  products: [],
  stock: {},
  qty: {},
  orders: [],
  authMode: "login",
  draft: { email: "", password: "", name: "" },
  notice: "",
  error: "",
  busy: false,
  instancePort: ""
};

let watchGeneration = 0;

const app = document.querySelector("#app");

app.addEventListener("click", (event) => {
  const button = event.target.closest("[data-action]");
  if (!button || state.busy) {
    return;
  }
  const action = button.dataset.action;
  if (action === "auth-mode") {
    const form = app.querySelector("#auth-form");
    if (form) {
      const data = new FormData(form);
      state.draft.email = String(data.get("email") || "").trim();
      state.draft.password = String(data.get("password") || "");
      if (data.get("name") !== null) {
        state.draft.name = String(data.get("name") || "").trim();
      }
    }
    state.authMode = button.dataset.mode;
    state.error = "";
    render();
  } else if (action === "logout") {
    logout();
  } else if (action === "qty") {
    changeQty(Number(button.dataset.id), Number(button.dataset.step));
  } else if (action === "place") {
    placeOrder();
  } else if (action === "pay") {
    pay(Number(button.dataset.id));
  } else if (action === "refresh") {
    loadShop();
  }
});

app.addEventListener("submit", (event) => {
  const form = event.target;
  event.preventDefault();
  if (form.id === "auth-form") {
    submitAuth(new FormData(form));
  } else if (form.id === "stock-form") {
    saveStock(new FormData(form));
  } else if (form.id === "product-form") {
    createProduct(new FormData(form));
  }
});

loadShop();

async function loadShop() {
  state.error = "";
  try {
    const products = await api("/products");
    state.products = products.payload;
    state.instancePort = products.instancePort || "";
    const stocks = await Promise.all(state.products.map((product) => api(`/inventory/${product.id}`)));
    state.stock = Object.fromEntries(stocks.map((item) => [item.payload.productId, item.payload.available]));
    if (state.token) {
      await loadOrders();
    }
  } catch (error) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    state.error = error.message;
  }
  render();
  watchOpenOrders();
}

async function loadOrders() {
  const orders = await api("/orders", { token: state.token });
  state.orders = orders.payload;
  state.instancePort = orders.instancePort || state.instancePort;
}

async function submitAuth(form) {
  state.draft = {
    email: String(form.get("email") || "").trim(),
    password: String(form.get("password") || ""),
    name: String(form.get("name") || "").trim()
  };
  state.busy = true;
  state.error = "";
  render();
  try {
    if (state.authMode === "register") {
      const registered = await api("/users", {
        method: "POST",
        body: {
          email: state.draft.email,
          password: state.draft.password,
          name: state.draft.name
        }
      });
      state.draft.name = registered.payload.name || state.draft.name;
    }
    const session = await api("/sessions", {
      method: "POST",
      body: {
        email: state.draft.email,
        password: state.draft.password
      }
    });
    state.token = session.payload.token;
    state.account = { email: state.draft.email, name: state.draft.name };
    state.draft.password = "";
    sessionStorage.setItem("ink-token", state.token);
    sessionStorage.setItem("ink-account", JSON.stringify(state.account));
    state.notice = "已经登上舰桥。";
    await loadOrders();
    await refreshStock();
  } catch (error) {
    state.error = error.message;
  } finally {
    state.busy = false;
    render();
    watchOpenOrders();
  }
}

function logout() {
  watchGeneration += 1;
  state.token = "";
  state.account = null;
  state.orders = [];
  state.notice = "";
  sessionStorage.removeItem("ink-token");
  sessionStorage.removeItem("ink-account");
  render();
}

function expireSession() {
  watchGeneration += 1;
  state.token = "";
  state.account = null;
  state.orders = [];
  state.busy = false;
  sessionStorage.removeItem("ink-token");
  sessionStorage.removeItem("ink-account");
  state.error = "登录已失效，请重新登舰";
  render();
}

function changeQty(productId, step) {
  const available = state.stock[productId] ?? 0;
  const next = Math.max(0, Math.min(available, (state.qty[productId] || 0) + step));
  state.qty = { ...state.qty, [productId]: next };
  render();
}

async function placeOrder() {
  watchGeneration += 1;
  const lines = state.products
    .filter((product) => (state.qty[product.id] || 0) > 0)
    .map((product) => ({ productId: product.id, quantity: state.qty[product.id] }));
  if (lines.length === 0) {
    state.error = "先选一两样。";
    render();
    return;
  }
  state.busy = true;
  state.error = "";
  state.notice = "订单已交给店铺，正在留库存。";
  render();
  try {
    const placed = await api("/orders", {
      method: "POST",
      token: state.token,
      idempotencyKey: crypto.randomUUID(),
      body: { lines }
    });
    state.qty = {};
    const settled = await waitFor(placed.payload.id, ["RESERVED", "REJECTED"]);
    state.notice = settled.status === "RESERVED" ? "库存已经留下，可以付款。" : "这单没有留住库存。";
    await refreshStock();
    await loadOrders();
  } catch (error) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    state.error = error.message;
    state.notice = "";
  } finally {
    state.busy = false;
    render();
    if (state.token) {
      watchOpenOrders();
    }
  }
}

async function pay(orderId) {
  watchGeneration += 1;
  state.busy = true;
  state.error = "";
  state.notice = "正在付款。";
  render();
  try {
    const paid = await api(`/orders/${orderId}/payments`, {
      method: "POST",
      token: state.token,
      idempotencyKey: crypto.randomUUID()
    });
    const settled = paid.payload.status === "RELEASING"
      ? await waitFor(orderId, ["CANCELLED"])
      : paid.payload;
    state.notice = settled.status === "PAID" ? "付款完成。" : "余额不够，库存已经退回。";
    await refreshStock();
    await loadOrders();
  } catch (error) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    state.error = error.message;
    state.notice = "";
  } finally {
    state.busy = false;
    render();
    if (state.token) {
      watchOpenOrders();
    }
  }
}

async function saveStock(form) {
  watchGeneration += 1;
  const productId = Number(form.get("productId"));
  const available = Number(form.get("available"));
  if (!Number.isInteger(available) || available < 0) {
    state.error = "可售数量要填 0 或正整数。";
    render();
    return;
  }
  state.busy = true;
  state.error = "";
  render();
  try {
    const saved = await api(`/inventory/${productId}`, {
      method: "PUT",
      token: state.token,
      body: { available }
    });
    state.stock = { ...state.stock, [saved.payload.productId]: saved.payload.available };
    state.qty = { ...state.qty, [productId]: Math.min(state.qty[productId] || 0, saved.payload.available) };
    state.notice = "货舱数量已经写上。";
  } catch (error) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    state.error = error.message;
  } finally {
    state.busy = false;
    render();
  }
}

async function createProduct(form) {
  watchGeneration += 1;
  const name = String(form.get("name") || "").trim();
  const description = String(form.get("description") || "").trim();
  const priceCents = Math.round(Number(form.get("priceYuan")) * 100);
  if (!name || !description || !Number.isInteger(priceCents) || priceCents < 1) {
    state.error = "上架需要名称、简介，单价至少 0.01 元。";
    render();
    return;
  }
  state.busy = true;
  state.error = "";
  render();
  try {
    await api("/products", {
      method: "POST",
      token: state.token,
      body: { name, description, priceCents }
    });
    state.notice = "新货已经上架，库存先是 0，可以再补给。";
    const products = await api("/products");
    state.products = products.payload;
    await refreshStock();
  } catch (error) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    state.error = error.message;
  } finally {
    state.busy = false;
    render();
  }
}

async function watchOpenOrders() {
  const generation = ++watchGeneration;
  const open = state.orders.filter((order) => order.status === "PENDING" || order.status === "RELEASING");
  if (open.length === 0 || !state.token) {
    return;
  }
  await Promise.all(open.map(async (order) => {
    const expected = order.status === "PENDING" ? ["RESERVED", "REJECTED"] : ["CANCELLED"];
    try {
      const settled = await waitFor(order.id, expected);
      if (generation !== watchGeneration) {
        return;
      }
      state.orders = state.orders.map((item) => item.id === settled.id ? settled : item);
      state.notice = noticeFor(settled);
    } catch (error) {
      if (generation !== watchGeneration) {
        return;
      }
      if (error.status === 401) {
        expireSession();
      }
    }
  }));
  if (generation !== watchGeneration || state.busy) {
    return;
  }
  await refreshStock();
  render();
}

function noticeFor(order) {
  if (order.status === "RESERVED") {
    return "库存已经留下，可以付款。";
  }
  if (order.status === "REJECTED") {
    return "这单没有留住库存。";
  }
  if (order.status === "CANCELLED") {
    return "余额不够，库存已经退回。";
  }
  return state.notice;
}

async function waitFor(orderId, expected) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const current = await api(`/orders/${orderId}`, { token: state.token });
    if (expected.includes(current.payload.status)) {
      return current.payload;
    }
    await delay(400);
  }
  throw new Error("店铺还没有回完这张订单的状态");
}

async function refreshStock() {
  const stocks = await Promise.all(state.products.map((product) => api(`/inventory/${product.id}`)));
  state.stock = Object.fromEntries(stocks.map((item) => [item.payload.productId, item.payload.available]));
}

function render() {
  const lines = state.products.filter((product) => (state.qty[product.id] || 0) > 0);
  const total = lines.reduce((sum, product) => sum + product.priceCents * state.qty[product.id], 0);
  app.innerHTML = `
    <div class="sky">
      <div class="stars" aria-hidden="true"></div>
      <span class="comet" aria-hidden="true"></span>
      <header class="topbar">
        ${ship()}
        <div class="brand">
          <p class="eyebrow">下一站 · 彩虹海</p>
          <h1>墨坊星航</h1>
        </div>
        <button class="chip" data-action="refresh" type="button">重新扫描货舱</button>
      </header>
      ${banner()}
      <div class="stage">
        <section class="shelf">
          <h2>航线补给</h2>
          <div class="cards">
            ${state.products.map((product) => productRow(product)).join("") || `<p class="empty">货舱还是空的。</p>`}
          </div>
        </section>
        <aside class="ticket">
          ${accountPanel()}
          <section class="slip">
            <h2>航行清单</h2>
            ${lines.map((product) => `
              <p class="slip-line"><span>${escapeHtml(product.name)} × ${state.qty[product.id]}</span><span>${yuan(product.priceCents * state.qty[product.id])}</span></p>
            `).join("") || `<p class="empty">清单还是空白，先从货舱挑一件。</p>`}
            <p class="total"><span>合计</span><strong>${yuan(total)}</strong></p>
            <button class="primary" data-action="place" type="button" ${state.token && lines.length && !state.busy ? "" : "disabled"}>发射订单</button>
            ${placeHint(lines)}
          </section>
          <section class="slip">
            <h2>航行记录</h2>
            ${state.token ? ordersHtml() : `<p class="empty">登舰后才能看见自己的订单。</p>`}
          </section>
          ${state.token ? dutyPanel() : ""}
        </aside>
      </div>
      <section class="crew">
        <h2>同行的人</h2>
        <div class="crew-row">
          <figure>
            <img src="/cast/maidang.png?v=3" alt="麦当" />
            <figcaption>麦当</figcaption>
          </figure>
          <figure>
            <img src="/cast/gudong.png?v=3" alt="咕咚" />
            <figcaption>咕咚</figcaption>
          </figure>
          <figure>
            <img src="/cast/diya.png?v=3" alt="笛亚" />
            <figcaption>笛亚</figcaption>
          </figure>
        </div>
      </section>
    </div>
  `;
}

function ship() {
  return `
    <svg class="ship" viewBox="0 0 120 88" aria-hidden="true">
      <path d="M8 58 H46 L62 28 H92 L104 58 H112" fill="none" stroke="#ffd15c" stroke-width="4" stroke-linecap="round"/>
      <path d="M22 58 L48 36 H86 L102 58 Z" fill="#ff3b30"/>
      <path d="M48 36 H70 L62 50 H40 Z" fill="#7ee0ff"/>
      <circle cx="78" cy="50" r="6" fill="#ffe14a" stroke="#fff" stroke-width="2"/>
      <path d="M18 64 Q8 70 16 76" fill="none" stroke="#7a5cff" stroke-width="4" stroke-linecap="round"/>
      <path d="M14 70 Q4 76 14 82" fill="none" stroke="#3dde7a" stroke-width="4" stroke-linecap="round"/>
    </svg>
  `;
}

function productRow(product) {
  const available = state.stock[product.id];
  const selected = state.qty[product.id] || 0;
  return `
    <article class="card tone-${product.id}">
      <div class="sticker" aria-hidden="true">${sticker(product.id)}</div>
      <div>
        <h3>${escapeHtml(product.name)}</h3>
        <p>${escapeHtml(product.description)}</p>
      </div>
      <p class="price">${yuan(product.priceCents)}</p>
      <p class="stock">${available === undefined ? "扫描中…" : `货舱还剩 ${available}`}</p>
      <div class="stepper">
        <button type="button" data-action="qty" data-id="${product.id}" data-step="-1" aria-label="减少${escapeHtml(product.name)}" ${selected <= 0 ? "disabled" : ""}>−</button>
        <span>${selected}</span>
        <button type="button" data-action="qty" data-id="${product.id}" data-step="1" aria-label="增加${escapeHtml(product.name)}" ${available !== undefined && selected >= available ? "disabled" : ""}>+</button>
      </div>
    </article>
  `;
}

function sticker(id) {
  const faces = {
    1: `<circle cx="36" cy="36" r="22" fill="#241838"/><ellipse cx="36" cy="36" rx="32" ry="10" fill="none" stroke="url(#bow)" stroke-width="4"/><defs><linearGradient id="bow" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#ff4d4d"/><stop offset="0.5" stop-color="#ffe14a"/><stop offset="1" stop-color="#3aa0ff"/></linearGradient></defs>`,
    2: `<circle cx="36" cy="38" r="22" fill="#ff7a18"/><path d="M24 30 Q36 8 48 30 Q40 24 36 28 Q32 22 24 30Z" fill="#ffd15c"/>`,
    3: `<path d="M16 48 L36 12 L56 48 Z" fill="#e8f6ff" stroke="#7ee0ff" stroke-width="3"/><path d="M28 48 Q36 28 44 48" fill="none" stroke="#3aa0ff" stroke-width="2"/>`,
    4: `<path d="M36 8 L44 28 H66 L48 40 L56 62 L36 48 L16 62 L24 40 L6 28 H28 Z" fill="#ff3b30"/>`
  };
  return `<svg viewBox="0 0 72 72">${faces[id] || faces[1]}</svg>`;
}

function accountPanel() {
  if (state.token) {
    const name = state.account?.name || state.account?.email || "客人";
    return `
      <section class="account">
        <p class="eyebrow">舰桥</p>
        <h2>${escapeHtml(name)}</h2>
        <p class="meta">${state.instancePort ? `这回落到舰位 ${escapeHtml(state.instancePort)}` : "新船员余额 500.00 元"}</p>
        <button class="chip" data-action="logout" type="button">离舰</button>
      </section>
    `;
  }
  return `
    <section class="account">
      <div class="tabs">
        <button type="button" data-action="auth-mode" data-mode="login" class="${state.authMode === "login" ? "on" : ""}">登舰</button>
        <button type="button" data-action="auth-mode" data-mode="register" class="${state.authMode === "register" ? "on" : ""}">入队</button>
      </div>
      <form id="auth-form">
        ${state.authMode === "register" ? `<label>昵称<input name="name" required maxlength="40" value="${escapeHtml(state.draft.name)}" /></label>` : ""}
        <label>邮箱<input name="email" type="email" required maxlength="120" value="${escapeHtml(state.draft.email)}" /></label>
        <label>密码<input name="password" type="password" required minlength="8" value="${escapeHtml(state.draft.password)}" /></label>
        <button class="primary" type="submit" ${state.busy ? "disabled" : ""}>${state.authMode === "register" ? "入队并登舰" : "登舰"}</button>
      </form>
    </section>
  `;
}

function ordersHtml() {
  if (state.orders.length === 0) {
    return `<p class="empty">还没有航行记录。</p>`;
  }
  return state.orders.map((order) => `
    <article class="order ${order.status.toLowerCase()}">
      <p><strong>第 ${order.id} 号</strong><span>${yuan(order.totalCents)}</span></p>
      <p class="status">${statusText[order.status] || escapeHtml(order.status)}</p>
      <ul class="lines">
        ${(order.lines || []).map((line) => `<li><span>${escapeHtml(line.productName)} × ${line.quantity}</span><span>${yuan(line.unitPriceCents)}</span></li>`).join("")}
      </ul>
      <time datetime="${escapeHtml(order.createdAt || "")}">${formatTime(order.createdAt)}</time>
      ${order.status === "RESERVED" ? `<button class="primary slim" data-action="pay" data-id="${order.id}" type="button" ${state.busy ? "disabled" : ""}>支付能量</button>` : ""}
    </article>
  `).join("");
}

function dutyPanel() {
  const options = state.products.map((product) => `
    <option value="${product.id}">${escapeHtml(product.name)}（现有 ${state.stock[product.id] ?? "…"}）</option>
  `).join("");
  return `
    <section class="slip">
      <h2>舰务</h2>
      <form id="stock-form">
        <label>补给哪一件<select name="productId" required>${options}</select></label>
        <label>改成可售数量<input name="available" type="number" min="0" step="1" required /></label>
        <button class="primary" type="submit" ${state.busy ? "disabled" : ""}>写入货舱</button>
      </form>
      <form id="product-form">
        <label>新货名称<input name="name" required maxlength="80" /></label>
        <label>单价（元）<input name="priceYuan" type="number" min="0.01" step="0.01" required /></label>
        <label>简介<input name="description" required maxlength="400" /></label>
        <button class="primary" type="submit" ${state.busy ? "disabled" : ""}>上架</button>
      </form>
    </section>
  `;
}

function placeHint(lines) {
  if (state.token && lines.length) {
    return "";
  }
  const text = state.token ? "先从货舱挑一件。" : "先登舰，才能发射订单。";
  return `<p class="hint">${text}</p>`;
}

function formatTime(value) {
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) {
    return "";
  }
  return time.toLocaleString("zh-CN", { hour12: false });
}

function banner() {
  if (!state.notice && !state.error) {
    return "";
  }
  return `<p class="${state.error ? "banner error" : "banner"}">${escapeHtml(state.error || state.notice)}</p>`;
}

function yuan(cents) {
  return `¥${(cents / 100).toFixed(2)}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
