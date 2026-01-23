// index.js — paste/replace your current file with this

const apiBase = window.location.origin; // same origin since served from Spring Boot
let currentUser = null;
let currentToken = localStorage.getItem('uwh_token') || null;

// --- helper fetch with token if present
async function api(path, options = {}) {
  options.headers = options.headers || {};
  if (currentToken) options.headers['X-Auth-Token'] = currentToken;
  options.headers['Content-Type'] = options.headers['Content-Type'] || 'application/json';
  const res = await fetch(apiBase + path, options);
  if (!res.ok) {
    const txt = await res.text();
    throw new Error(res.status + ' ' + res.statusText + ': ' + txt);
  }
  if (res.status === 204) return null;
  return res.json();
}

// small helper to safely toggle classes
function setVisible(id, visible) {
  const el = document.getElementById(id);
  if (!el) return;
  if (visible) el.classList.remove('hidden');
  else el.classList.add('hidden');
}

// --- admin UI toggler
function setAdminUI(isAdmin) {
  // admin panel
  setVisible('admin-panel', !!isAdmin);

  // teams card (admin-only UI)
  setVisible('teams-card', !!isAdmin);

  // create event buttons (admin-only)
  const createEventBtn = document.getElementById('create-event-btn');
  const createRecurringBtn = document.getElementById('create-recurring-btn');
  if (createEventBtn) createEventBtn.style.display = isAdmin ? '' : 'none';
  if (createRecurringBtn) createRecurringBtn.style.display = isAdmin ? '' : 'none';
}

// --- auth UI
function showLoggedIn(user) {
  const not = document.getElementById('not-logged-in');
  const logged = document.getElementById('logged-in');
  if (not) not.classList.add('hidden');
  if (logged) logged.classList.remove('hidden');
  const who = document.getElementById('who');
  if (who) who.innerText = user.name + ' (' + user.email + ')';
}
function showLoggedOut() {
  const not = document.getElementById('not-logged-in');
  const logged = document.getElementById('logged-in');
  if (not) not.classList.remove('hidden');
  if (logged) logged.classList.add('hidden');
  const who = document.getElementById('who');
  if (who) who.innerText = '';
}

// --- auth actions
async function register() {
  const name = document.getElementById('reg-name').value;
  const email = document.getElementById('reg-email').value;
  const password = document.getElementById('reg-password').value;
  try {
    await api('/users', { method: 'POST', body: JSON.stringify({ name, email, password }) });
    document.getElementById('reg-msg').innerText = 'Account created. You can log in.';
  } catch (e) {
    document.getElementById('reg-msg').innerText = 'Error: ' + e.message;
  }
}

async function login() {
  const email = document.getElementById('login-email').value;
  const password = document.getElementById('login-password').value;
  try {
    const res = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    currentToken = res.token;
    currentUser = res.user;
    localStorage.setItem('uwh_token', currentToken);
    showLoggedIn(currentUser);
    // ask backend for full profile (including isAdmin)
    try {
      const me = await api('/auth/me', { method: 'GET' });
      currentUser = me;
      setAdminUI(!!me.isAdmin);
    } catch (e) {
      setAdminUI(false);
    }
    await fetchEvents();
    document.getElementById('login-msg').innerText = 'Logged in';
  } catch (e) {
    document.getElementById('login-msg').innerText = 'Login failed: ' + e.message;
  }
}

function logout() {
  currentToken = null;
  currentUser = null;
  localStorage.removeItem('uwh_token');
  showLoggedOut();
  setAdminUI(false);
}

// --- events UI
async function fetchEvents() {
  try {
    const events = await api('/events', { method: 'GET' });
    const list = document.getElementById('events-list');
    const select = document.getElementById('event-select');
    if (list) list.innerHTML = '';
    if (select) select.innerHTML = '<option value="">(pick)</option>';
    events.forEach(ev => {
      const div = document.createElement('div');
      div.innerHTML = `<strong>${ev.title}</strong> — ${ev.location || ''} — ${ev.startTime || ''}`;
      // RSVP buttons (visible to users)
      const yesBtn = document.createElement('button');
      yesBtn.innerText = 'Yes';
      yesBtn.onclick = () => rsvp(ev.id, 'yes');
      const noBtn = document.createElement('button');
      noBtn.innerText = 'No';
      noBtn.onclick = () => rsvp(ev.id, 'no');
      div.appendChild(document.createElement('br'));
      div.appendChild(yesBtn);
      div.appendChild(noBtn);

      // show attendees count (quick)
      const attendeesBtn = document.createElement('button');
      attendeesBtn.innerText = 'Show attendees';
      attendeesBtn.onclick = () => showAttendees(ev.id);
      div.appendChild(attendeesBtn);

      if (list) list.appendChild(div);

      if (select) {
        const opt = document.createElement('option');
        opt.value = ev.id;
        opt.innerText = ev.title + ' (' + ev.startTime + ')';
        select.appendChild(opt);
      }
    });
  } catch (e) {
    console.error('fetchEvents', e);
  }
}

// --- create event (admin-only)
async function createEventPrompt() {
  if (!currentUser || !currentUser.isAdmin) {
    alert('Only admins can create events.');
    return;
  }
  const title = prompt('Title for the event', 'UWH Session');
  if (!title) return;
  const location = prompt('Location', 'Local Pool');
  const startTime = prompt('Start time (ISO instant or leave blank)', '');
  const body = { title, location };
  if (startTime) body.startTime = startTime;
  try {
    await api('/events', { method: 'POST', body: JSON.stringify(body) });
    await fetchEvents();
  } catch (e) {
    alert('Create event failed: ' + e.message);
  }
}

// --- create recurring (admin-only)
async function createRecurringNow() {
  if (!currentUser || !currentUser.isAdmin) {
    alert('Only admins can trigger recurring creation.');
    return;
  }
  try {
    await api('/events/create-recurring', { method: 'POST', body: JSON.stringify({}) });
    alert('Triggered recurring creation (check events list).');
    await fetchEvents();
  } catch (e) {
    alert('Failed: ' + e.message);
  }
}

// --- RSVP (uses logged-in user token)
async function rsvp(eventId, status) {
  if (!currentUser) { alert('Log in first'); return; }
  try {
    await api(`/events/${eventId}/rsvp`, { method: 'POST', body: JSON.stringify({ userId: currentUser.id, status }) });
    alert('RSVP saved');
  } catch (e) {
    alert('RSVP failed: ' + e.message);
  }
}

async function showAttendees(eventId) {
  try {
    const users = await api(`/events/${eventId}/attendees`, { method: 'GET' });
    alert('Attendees: ' + users.map(u => u.name).join(', '));
  } catch (e) {
    alert('Failed to load attendees: ' + e.message);
  }
}

// --- Teams generation (admin-only)
async function generateTeams() {
  if (!currentUser || !currentUser.isAdmin) {
    alert('Only admins can generate teams.');
    return;
  }
  const eventId = document.getElementById('event-select').value;
  if (!eventId) { alert('Pick an event first'); return; }
  const teamSize = parseInt(document.getElementById('team-size').value || '5', 10);
  const method = document.getElementById('team-method').value;

  try {
    const teams = await api(`/events/${eventId}/generate-teams`, {
      method: 'POST',
      body: JSON.stringify({ teamSize, method })
    });
    const c = document.getElementById('teams-container');
    if (c) c.innerHTML = '';
    teams.forEach(t => {
      const div = document.createElement('div');
      div.className = 'card';
      const members = (t.members || []).map(m => m.name || m).join(', ');
      div.innerHTML = `<strong>Team ${t.teamIndex || t.name || '?'}</strong><div>${members}</div>`;
      if (c) c.appendChild(div);
    });
  } catch (e) {
    alert('Generate teams failed: ' + e.message);
  }
}

// --- Admin functions
async function fetchAllUsers() {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  try {
    const users = await api('/admin/users', { method: 'GET' });
    const container = document.getElementById('admin-users');
    if (container) container.innerHTML = '';
    users.forEach(u => {
      const div = document.createElement('div');
      div.innerHTML = `
        ${u.id} — ${u.name} (${u.email}) — skill: <input id="skill-${u.id}" value="${u.skill || 0}" style="width:50px"/>
        <button onclick="adminUpdateSkill(${u.id})">Save</button>
        <button onclick="adminDeleteUser(${u.id})">Delete</button>
      `;
      if (container) container.appendChild(div);
    });
  } catch (e) {
    alert('fetchAllUsers failed: ' + e.message);
  }
}

async function adminUpdateSkill(id) {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  const skill = parseInt(document.getElementById(`skill-${id}`).value || '0', 10);
  try {
    await api(`/admin/users/${id}/skill`, { method: 'PUT', body: JSON.stringify({ skill }) });
    alert('skill updated');
    await fetchAllUsers();
  } catch (e) {
    alert('update failed: ' + e.message);
  }
}

async function adminDeleteUser(id) {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  if (!confirm('Delete user ' + id + '?')) return;
  try {
    await api(`/admin/users/${id}`, { method: 'DELETE' });
    alert('deleted');
    await fetchAllUsers();
  } catch (e) {
    alert('delete failed: ' + e.message);
  }
}

async function adminCreateEvent() {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  const title = document.getElementById('admin-event-title').value;
  const startTime = document.getElementById('admin-event-start').value;
  try {
    await api('/admin/events', { method: 'POST', body: JSON.stringify({ title, startTime }) });
    alert('event created');
    await fetchEvents();
  } catch (e) {
    alert('create event failed: ' + e.message);
  }
}

// On load: if token present, try to fetch /auth/me
(async function init() {
  // hide admin pieces by default until we know user's role
  setAdminUI(false);

  if (currentToken) {
    try {
      const res = await api('/auth/me', { method: 'GET' });
      currentUser = res;
      if (res.isAdmin) {
        setAdminUI(true);
      } else {
        setAdminUI(false);
      }
      showLoggedIn(currentUser);
    } catch (e) {
      // invalid token
      localStorage.removeItem('uwh_token');
      currentToken = null;
      currentUser = null;
      showLoggedOut();
      setAdminUI(false);
    }
  } else {
    showLoggedOut();
    setAdminUI(false);
  }
  await fetchEvents();
})();
