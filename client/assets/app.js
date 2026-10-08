/* Облік особового складу: клієнт без збирання (чистий HTML + JS).
   Усі запити йдуть на адресу /api/...:
   - локально: Spring сам відкидає префікс /api;
   - в Docker: це робить nginx (proxy_pass .../ з косою рискою в кінці). */
(() => {
  'use strict';

  const API = window.API_BASE ?? '/api';
  const STATUSES = {
    present: 'На місці', leave: 'У відпустці', sick: 'Хворіє',
    mission: 'У відрядженні', duty: 'У наряді',
  };
  const ROUTES = {
    home: { title: 'Головна', render: renderHome },
    personnel: { title: 'Особовий склад', render: renderPersonnel },
    about: { title: 'Про систему', render: renderAbout },
  };

  const $ = (sel, root = document) => root.querySelector(sel);
  const view = $('#view');
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDate = (iso) => iso ? new Date(iso).toLocaleString('uk-UA',
    { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
  const chip = (st) => `<span class="chip s-${esc(st)}">${esc(STATUSES[st] || st)}</span>`;

  /* ---------- токен входу (зберігається в браузері) ---------- */
  const TOKEN_KEY = 'personnel_token';
  const USER_KEY = 'personnel_user';
  const store = {
    get: (k) => { try { return localStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* режим без сховища */ } },
    del: (k) => { try { localStorage.removeItem(k); } catch { /* ігноруємо */ } },
  };

  function showLogin(message) {
    document.body.classList.add('anon');
    $('#login').hidden = false;
    const box = $('#login-error');
    box.hidden = !message;
    box.textContent = message || '';
    const f = $('#login-form').elements.username;
    if (f) f.focus();
  }

  function showApp() {
    document.body.classList.remove('anon');
    $('#login').hidden = true;
    $('#who').textContent = store.get(USER_KEY) || '';
  }

  function logout() {
    store.del(TOKEN_KEY);
    store.del(USER_KEY);
    showLogin();
  }

  /* ---------- мережа ---------- */
  async function api(path, opts = {}) {
    const token = store.get(TOKEN_KEY);
    const res = await fetch(API + path, {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      ...opts,
    });
    if (res.status === 204) return null;
    // 401 поза сторінкою входу: токен недійсний або прострочений, просимо увійти знову
    if (res.status === 401 && path !== '/auth/login') {
      store.del(TOKEN_KEY);
      showLogin('Сесія закінчилась. Увійдіть знову.');
      const err = new Error('Потрібен вхід');
      err.status = 401;
      throw err;
    }
    let data = null;
    try { data = await res.json(); } catch { /* тіла може не бути */ }
    if (!res.ok) {
      const parts = Array.isArray(data?.errors)
        ? data.errors.map((e) => `${e.field ?? ''} ${e.defaultMessage ?? e.message ?? ''}`.trim())
        : [];
      const err = new Error(parts.join('; ') || data?.message || data?.detail || `Помилка ${res.status}`);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  let toastTimer;
  function toast(msg, isErr = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast' + (isErr ? ' err' : '');
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 3500);
  }

  /* ---------- маршрутизація за # ---------- */
  function route() {
    if (!store.get(TOKEN_KEY)) { showLogin(); return; }
    showApp();
    const name = (location.hash.replace(/^#\/?/, '') || 'home');
    const key = ROUTES[name] ? name : 'home';
    document.querySelectorAll('#nav a').forEach((a) =>
      a.classList.toggle('active', a.dataset.route === key));
    $('#title').textContent = ROUTES[key].title;
    ROUTES[key].render().catch((e) => {
      if (e.status === 401) return;   // уже показано сторінку входу
      view.innerHTML = `<div class="card empty">Не вдалося завантажити дані.<br>
        <span class="muted">${esc(e.message)}</span><br>
        <button class="btn" onclick="location.reload()">Спробувати знову</button></div>`;
    });
  }

  /* ---------- Головна ---------- */
  const ICONS = {
    people: '<svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><path d="M16 5.2a3.2 3.2 0 0 1 0 5.6M18 14.3c1.9.8 3 2.7 3 5.7"/></svg>',
    check: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16 9.7"/></svg>',
    away: '<svg viewBox="0 0 24 24"><path d="M3 11.5 12 4l9 7.5M5.5 10v10h13V10"/></svg>',
  };

  async function renderHome() {
    const [sum, list] = await Promise.all([
      api('/personnel/summary'), api('/personnel?size=100'),
    ]);
    const present = sum.by_status.present || 0;
    const today = new Date().toLocaleDateString('uk-UA',
      { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

    let html = `
      <div class="kpis">
        <div class="hero"><h2>${esc(today.charAt(0).toUpperCase() + today.slice(1))}</h2></div>
        <div class="kpi"><span class="ico blue">${ICONS.people}</span>
          <div><div class="lbl">Особового складу</div><div class="val">${sum.total}</div></div></div>
        <div class="kpi"><span class="ico green">${ICONS.check}</span>
          <div><div class="lbl">На місці</div><div class="val">${present}</div></div></div>
        <div class="kpi"><span class="ico violet">${ICONS.away}</span>
          <div><div class="lbl">Відсутні</div><div class="val">${sum.total - present}</div></div></div>
      </div>`;

    if (sum.total === 0) {
      html += `<div class="card empty">
        <strong>Записів поки немає.</strong><br>
        Додайте першого на сторінці «Особовий склад» або заповніть демонстраційними даними.<br>
        <button class="btn primary" id="seed">Додати 12 демонстраційних записів</button></div>`;
      view.innerHTML = html;
      $('#seed').onclick = seedDemo;
      return;
    }

    // Матриця «підрозділ × статус», підрахована з завантаженого списку
    const units = {};
    list.items.forEach((p) => {
      const u = p.unit || 'Без підрозділу';
      units[u] = units[u] || {};
      units[u][p.status] = (units[u][p.status] || 0) + 1;
    });
    const cols = Object.keys(STATUSES);
    const head = cols.map((s) => `<th class="num ${s === 'present' ? 'today' : ''}">${STATUSES[s]}</th>`).join('');
    const rows = Object.keys(units).sort().map((u) => {
      const total = cols.reduce((n, s) => n + (units[u][s] || 0), 0);
      const cells = cols.map((s) => {
        const n = units[u][s];
        return `<td class="num ${s === 'present' ? 'today' : ''}">${n ? `<span class="cell s-${s}">${n}</span>` : ''}</td>`;
      }).join('');
      return `<tr><td class="unit">${esc(u)}</td>${cells}<td class="num"><strong>${total}</strong></td></tr>`;
    }).join('');
    html += `<div class="card"><h2>Стан по підрозділах</h2>
      <div class="tbl-wrap"><table class="matrix"><thead><tr><th>Підрозділ</th>${head}<th class="num">Усього</th></tr></thead>
      <tbody>${rows}</tbody></table></div>
      ${list.total > 100 ? '<p class="muted">Показано перші 100 записів.</p>' : ''}</div>`;
    view.innerHTML = html;
  }

  async function seedDemo() {
    const demo = [
      ['Іваненко Іван Петрович', 'сержант', 'командир відділення', '1 рота', 'present'],
      ['Коваленко Олена Сергіївна', 'солдат', "зв'язківець", '1 рота', 'present'],
      ['Мельник Андрій Олегович', 'молодший сержант', 'навідник', '1 рота', 'duty'],
      ['Шевченко Дмитро Васильович', 'солдат', 'стрілець', '1 рота', 'sick'],
      ['Бондаренко Наталія Іванівна', 'старший солдат', 'медик', '2 рота', 'present'],
      ['Ткаченко Сергій Михайлович', 'сержант', 'командир відділення', '2 рота', 'leave'],
      ['Кравченко Юрій Андрійович', 'солдат', 'водій', '2 рота', 'mission'],
      ['Олійник Тетяна Петрівна', 'солдат', 'оператор', '2 рота', 'present'],
      ['Лисенко Максим Романович', 'лейтенант', 'командир взводу', 'Штаб', 'present'],
      ['Поліщук Ірина Юріївна', 'старший лейтенант', 'начальник штабу', 'Штаб', 'present'],
      ['Савченко Роман Іванович', 'солдат', 'кухар', 'Тиловий взвод', 'present'],
      ['Руденко Віктор Степанович', 'сержант', 'старшина', 'Тиловий взвод', 'leave'],
    ];
    try {
      for (const [full_name, rank, position, unit, status] of demo) {
        await api('/personnel', { method: 'POST', body: JSON.stringify({ full_name, rank, position, unit, status }) });
      }
      toast('Додано 12 записів');
      route();
    } catch (e) { toast(e.message, true); }
  }

  /* ---------- Особовий склад ---------- */
  const state = { search: '', unit: '', status: '', page: 0, size: 15 };
  let unitNames = [];

  async function renderPersonnel() {
    const sum = await api('/personnel/summary');
    unitNames = Object.keys(sum.by_unit);
    view.innerHTML = `<div class="card">
      <div class="toolbar">
        <input type="search" id="q" placeholder="Пошук за ПІБ…" value="${esc(state.search)}">
        <select id="f-unit"><option value="">Усі підрозділи</option>
          ${unitNames.map((u) => `<option ${u === state.unit ? 'selected' : ''}>${esc(u)}</option>`).join('')}</select>
        <select id="f-st"><option value="">Усі статуси</option>
          ${Object.entries(STATUSES).map(([k, v]) => `<option value="${k}" ${k === state.status ? 'selected' : ''}>${v}</option>`).join('')}</select>
        <span class="grow"></span>
        <button class="btn primary" id="add">+ Додати</button>
      </div>
      <div id="tbl"></div></div>`;

    let timer;
    $('#q').oninput = (e) => {
      clearTimeout(timer);
      timer = setTimeout(() => { state.search = e.target.value; state.page = 0; loadTable(); }, 300);
    };
    $('#f-unit').onchange = (e) => { state.unit = e.target.value; state.page = 0; loadTable(); };
    $('#f-st').onchange = (e) => { state.status = e.target.value; state.page = 0; loadTable(); };
    $('#add').onclick = () => openForm();
    await loadTable();
  }

  async function loadTable() {
    const p = new URLSearchParams({ page: state.page, size: state.size });
    if (state.search) p.set('search', state.search);
    if (state.unit) p.set('unit', state.unit);
    if (state.status) p.set('status', state.status);
    const data = await api('/personnel?' + p);
    const box = $('#tbl');
    if (!box) return;

    if (data.total === 0) {
      box.innerHTML = `<div class="empty">${state.search || state.unit || state.status
        ? 'Нічого не знайдено за цими умовами.'
        : 'Список порожній. Натисніть «+ Додати», щоб створити перший запис.'}</div>`;
      return;
    }
    const rows = data.items.map((x) => `<tr>
      <td><strong>${esc(x.full_name)}</strong></td><td>${esc(x.rank) || '—'}</td>
      <td>${esc(x.position) || '—'}</td><td>${esc(x.unit) || '—'}</td>
      <td>${chip(x.status)}</td><td class="muted">${fmtDate(x.status_changed_at)}</td>
      <td style="white-space:nowrap">
        <button class="btn sm" data-edit="${x.id}">Змінити</button>
        <button class="btn sm danger" data-del="${x.id}" data-name="${esc(x.full_name)}">Видалити</button></td></tr>`).join('');
    const pages = Math.max(1, Math.ceil(data.total / data.size));
    box.innerHTML = `<div class="tbl-wrap"><table>
      <thead><tr><th>ПІБ</th><th>Звання</th><th>Посада</th><th>Підрозділ</th><th>Статус</th><th>Статус змінено</th><th></th></tr></thead>
      <tbody>${rows}</tbody></table></div>
      <div class="pager"><span>Усього: ${data.total}</span>
        <button class="btn sm" id="prev" ${data.page <= 0 ? 'disabled' : ''}>‹</button>
        <span>${data.page + 1} / ${pages}</span>
        <button class="btn sm" id="next" ${data.page + 1 >= pages ? 'disabled' : ''}>›</button></div>`;

    $('#prev').onclick = () => { state.page--; loadTable(); };
    $('#next').onclick = () => { state.page++; loadTable(); };
    box.querySelectorAll('[data-edit]').forEach((b) => {
      b.onclick = () => openForm(data.items.find((i) => String(i.id) === b.dataset.edit));
    });
    box.querySelectorAll('[data-del]').forEach((b) => {
      b.onclick = async () => {
        if (!confirm(`Видалити запис «${b.dataset.name}»?`)) return;
        try {
          await api('/personnel/' + b.dataset.del, { method: 'DELETE' });
          toast('Запис видалено');
          if (data.items.length === 1 && state.page > 0) state.page--;
          loadTable();
        } catch (e) { toast(e.message, true); }
      };
    });
  }

  /* ---------- діалог додавання / редагування ---------- */
  const dlg = $('#dlg');
  const form = $('#form');
  let editingId = null;
  $('#f-status').innerHTML = Object.entries(STATUSES).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');
  $('#dlg-cancel').onclick = () => dlg.close();

  function openForm(person) {
    editingId = person ? person.id : null;
    $('#dlg-title').textContent = person ? 'Редагування запису' : 'Новий запис';
    $('#form-error').hidden = true;
    form.reset();
    $('#units-list').innerHTML = unitNames.map((u) => `<option value="${esc(u)}">`).join('');
    if (person) {
      ['full_name', 'rank', 'position', 'unit', 'status', 'note'].forEach((k) => {
        form.elements[k].value = person[k] ?? '';
      });
    }
    dlg.showModal();
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const body = Object.fromEntries(new FormData(form).entries());
    Object.keys(body).forEach((k) => { if (body[k] === '') body[k] = null; });
    try {
      await api(editingId ? '/personnel/' + editingId : '/personnel',
        { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(body) });
      dlg.close();
      toast(editingId ? 'Зміни збережено' : 'Запис додано');
      route();
    } catch (e) {
      const box = $('#form-error');
      box.textContent = e.message;
      box.hidden = false;
    }
  });

  /* ---------- Про систему ---------- */
  async function renderAbout() {
    let h = null, ok = false;
    try {
      const res = await fetch(API + '/health');
      ok = res.ok;
      h = await res.json();
    } catch { /* сервер недоступний */ }
    view.innerHTML = `<div class="card"><h2>Стан сервера</h2>
      ${h ? `<dl class="kv">
        <dt>Статус</dt><dd><span class="dot ${ok ? 'ok' : 'bad'}"></span>${esc(h.status)}</dd>
        <dt>Версія</dt><dd>${esc(h.version)}</dd>
        <dt>Середовище</dt><dd>${esc(h.env)}</dd>
        <dt>База даних</dt><dd>${esc(h.database)} (${esc(h.database_mode)})</dd></dl>`
        : '<p class="muted">Сервер не відповідає.</p>'}
      </div>
      <div class="card"><h2>Адреси</h2><dl class="kv">
        <dt>Сторінка</dt><dd>${esc(location.origin)}</dd>
        <dt>API</dt><dd>${esc(location.origin + API)}</dd>
        <dt>Перевірка стану</dt><dd><a href="${esc(API)}/health" target="_blank">${esc(API)}/health</a></dd></dl></div>`;
  }

  /* ---------- старт ---------- */
  async function loadMeta() {
    try {
      const res = await fetch(API + '/health');
      const h = await res.json();
      $('#env-badge').textContent = h.env;
      $('#side-foot').textContent = 'версія ' + h.version;
    } catch {
      $('#env-badge').textContent = 'сервер недоступний';
    }
  }
  $('#logout').onclick = logout;
  $('#login-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target.elements;
    try {
      const r = await api('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: f.username.value, password: f.password.value }),
      });
      store.set(TOKEN_KEY, r.access_token);
      store.set(USER_KEY, r.username);
      f.password.value = '';
      route();
    } catch (e) {
      showLogin(e.status === 401 ? 'Невірний логін або пароль' : e.message);
    }
  });

  window.addEventListener('hashchange', route);
  loadMeta();
  route();
})();
