const apiBase = window.location.origin; // same origin since served from Spring Boot
let currentUser = null;
let currentToken = localStorage.getItem('uwh_token') || null;
let pastVisible = false; // whether past events are shown

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

  // teams card should be visible to everyone (we only hide generation controls for non-admins)
  setVisible('teams-card', true);

  // show/hide the internal generation UI
  setVisible('generate-controls', !!isAdmin);

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
  if (who) who.innerText = user.name + ' (' + user.username + ')';
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
  const username = document.getElementById('reg-username').value;
  const password = document.getElementById('reg-password').value;
  if (name.length < 1 || username.length < 1 || password.length < 1) {
    document.getElementById('reg-msg').innerText = 'All fields required';
    return;
  }
  try {
    await api('/users', { method: 'POST', body: JSON.stringify({ name, username, password }) });
    document.getElementById('reg-msg').innerText = 'Account created. You can log in.';
  } catch (e) {
    document.getElementById('reg-msg').innerText = 'Error: ' + e.message;
  }
}

async function login() {
  const username = document.getElementById('login-username').value;
  const password = document.getElementById('login-password').value;
  try {
    const res = await api('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) });
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

// --- helper: format date nicely (falls back to raw string)
function formatStart(iso) {
  if (!iso) return '(no time)';
  const t = Date.parse(iso);
  if (isNaN(t)) return iso;
  const d = new Date(t);
  return d.toLocaleString(undefined, {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true // optional: use 24h format
});
}

// helper to create event DOM node (keeps consistent UI)
function createEventDiv(ev) {
  const div = document.createElement('div');
  div.className = 'event-item';
  const when = ev.startTime ? formatStart(ev.startTime) : '(no time)';
  const location = ev.location ? ' — ' + ev.location : '';
  div.innerHTML = `<strong>${ev.title}</strong> — ${when}${location}`;
  div.appendChild(document.createElement('br'));

  // --- RSVP button group (larger, spaced, selectable)
  const rsvpGroup = document.createElement('div');
  rsvpGroup.className = 'rsvp-group';

  const yesBtn = document.createElement('button');
  yesBtn.innerText = 'Yes';
  yesBtn.className = 'rsvp-btn rsvp-yes';
  yesBtn.setAttribute('aria-pressed', 'false');

  const noBtn = document.createElement('button');
  noBtn.innerText = 'No';
  noBtn.className = 'rsvp-btn rsvp-no';
  noBtn.setAttribute('aria-pressed', 'false');

  // optimistic UI + keep buttons disabled while request in flight
  yesBtn.onclick = () => {
    yesBtn.disabled = true; noBtn.disabled = true;
    // optimistic highlight
    yesBtn.classList.add('selected');
    noBtn.classList.remove('selected');
    yesBtn.setAttribute('aria-pressed', 'true');
    noBtn.setAttribute('aria-pressed', 'false');

    // call existing rsvp function (it handles alerts/errors)
    // rsvp returns a promise (it uses fetch internally)
    rsvp(ev.id, 'yes').finally(() => {
      yesBtn.disabled = false; noBtn.disabled = false;
    });
  };

  noBtn.onclick = () => {
    yesBtn.disabled = true; noBtn.disabled = true;
    noBtn.classList.add('selected');
    yesBtn.classList.remove('selected');
    noBtn.setAttribute('aria-pressed', 'true');
    yesBtn.setAttribute('aria-pressed', 'false');

    rsvp(ev.id, 'no').finally(() => {
      yesBtn.disabled = false; noBtn.disabled = false;
    });
  };

  rsvpGroup.appendChild(yesBtn);
  rsvpGroup.appendChild(noBtn);
  div.appendChild(rsvpGroup);

  // attendees
  const attendeesBtn = document.createElement('button');
  attendeesBtn.innerText = 'Show attendees';
  attendeesBtn.onclick = () => showAttendees(ev.id);
  div.appendChild(attendeesBtn);

  // show saved teams
  const showTeamsBtn = document.createElement('button');
  showTeamsBtn.innerText = 'Show saved teams';
  showTeamsBtn.onclick = () => {
    console.log('Show saved teams clicked for event', ev.id);
    showSavedTeams(ev.id);
  };
  div.appendChild(showTeamsBtn);

  // admin quick-edit (unchanged)
  if (currentUser && currentUser.isAdmin) {
    const editBtn = document.createElement('button');
    editBtn.innerText = 'Edit (admin)';
    editBtn.onclick = async () => {
      const newTitle = prompt('New title', ev.title);
      if (!newTitle) return;
      try {
        await api(`/admin/events/${ev.id}`, { method: 'PUT', body: JSON.stringify({ title: newTitle }) });
        await fetchEvents();
      } catch (e) {
        alert('Edit failed: ' + e.message);
      }
    };
    const delBtn = document.createElement('button');
    delBtn.innerText = 'Delete (admin)';
    delBtn.onclick = async () => {
      if (!confirm('Delete event?')) return;
      try {
        await api(`/admin/events/${ev.id}`, { method: 'DELETE' });
        await fetchEvents();
      } catch (e) {
        alert('Delete failed: ' + e.message);
      }
    };
    div.appendChild(editBtn);
    div.appendChild(delBtn);
  }

  return div;
}

// toggle past visibility
function togglePast() {
  pastVisible = !pastVisible;
  const list = document.getElementById('past-events-list');
  const btn = document.getElementById('toggle-past-btn');
  if (pastVisible) {
    list.classList.remove('hidden');
    btn.innerText = 'Hide past events';
  } else {
    list.classList.add('hidden');
    btn.innerText = 'Show past events';
  }
}

// --- events UI
async function fetchEvents() {
  try {
    const events = await api('/events', { method: 'GET' }) || [];
    const upcomingList = document.getElementById('upcoming-events-list');
    const pastList = document.getElementById('past-events-list');
    const select = document.getElementById('event-select');
    if (upcomingList) upcomingList.innerHTML = '';
    if (pastList) pastList.innerHTML = '';
    if (select) select.innerHTML = '<option value="">(pick)</option>';

    const now = Date.now();
    const upcoming = [];
    const past = [];

    events.forEach(ev => {
      let startMillis = NaN;
      if (ev.startTime) {
        startMillis = Date.parse(ev.startTime);
      }
      // If startTime is invalid or missing, treat as upcoming (put at end)
      if (!isNaN(startMillis) && startMillis < now) {
        past.push(Object.assign({}, ev, { _startMillis: startMillis }));
      } else {
        upcoming.push(Object.assign({}, ev, { _startMillis: isNaN(startMillis) ? Number.POSITIVE_INFINITY : startMillis }));
      }
    });

    // Sort upcoming ascending (soonest first), past descending (most recent past first)
    upcoming.sort((a, b) => a._startMillis - b._startMillis);
    past.sort((a, b) => b._startMillis - a._startMillis);

    // render upcoming
    upcoming.forEach(ev => {
      const div = createEventDiv(ev);
      if (upcomingList) upcomingList.appendChild(div);

      // add upcoming events to select for teams
      if (select) {
        const opt = document.createElement('option');
        opt.value = ev.id;
        opt.innerText = ev.title + ' (' + (ev.startTime ? formatStart(ev.startTime) : 'no time') + ')';
        select.appendChild(opt);
      }

    });

    // render past
    past.forEach(ev => {
      const div = createEventDiv(ev);
      if (pastList) pastList.appendChild(div);
    });

    // update past summary count
    const pastSummary = document.getElementById('past-summary');
    if (pastSummary) pastSummary.innerText = `${past.length} past event${past.length === 1 ? '' : 's'}`;

    // ensure past visibility state respected
    const listElem = document.getElementById('past-events-list');
    const btn = document.getElementById('toggle-past-btn');
    if (pastVisible) {
      listElem.classList.remove('hidden');
      btn.innerText = 'Hide past events';
    } else {
      listElem.classList.add('hidden');
      btn.innerText = 'Show past events';
    }

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
  const startTime = prompt('Start time (ISO) — e.g. 2026-02-01T19:00:00Z (leave blank)', '');
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
  const method = document.getElementById('team-method').value;

  try {
    const teams = await api(`/events/${eventId}/generate-teams`, {
      method: 'POST',
      body: JSON.stringify({ method })
    });
    const c = document.getElementById('teams-container');
    if (c) c.innerHTML = '';
    teams.forEach((t, idx) => {
      const div = document.createElement('div');
      div.className = 'card';
      const members = (t.members || []).map(m => m.name || m).join(', ');

      // compute numeric teamIndex (fallback to array index + 1)
      const teamIndex = (t.teamIndex || (typeof t.name === 'number' ? t.name : null) || (idx + 1));

      div.innerHTML = `<strong>Team ${t.teamIndex || t.name || '?'}</strong><div>${members}</div>`;

      if (currentUser && currentUser.isAdmin) {
        const plus = document.createElement('button');
        plus.innerText = '+';
        plus.onclick = () => {
          console.log('adminAdjust + clicked', { eventId, teamIndex });
          adminAdjustTeamSkill(eventId, teamIndex, +1);
        };

        const minus = document.createElement('button');
        minus.innerText = '−';
        minus.onclick = () => {
          console.log('adminAdjust - clicked', { eventId, teamIndex });
          adminAdjustTeamSkill(eventId, teamIndex, -1);
        };

        const recordBtn = document.createElement('button');
        recordBtn.innerText = 'Record result';
        recordBtn.onclick = () => promptAndRecordMatches(eventId);

        div.appendChild(plus);
        div.appendChild(minus);
        div.appendChild(recordBtn);
      }

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

      // only admins should ever see or edit skill values; this function is admin-only but keep the check defensive
      if (currentUser && currentUser.isAdmin) {
        div.innerHTML = `
          ${u.name} (${u.username}) — skill: <input id="skill-${u.id}" value="${u.skill || 0}" style="width:50px"/>
          <button onclick="adminUpdateSkill(${u.id})">Save</button>
          <button onclick="adminDeleteUser(${u.id})">Delete</button>
        `;
      } else {
        // defensive fallback: don't show skills to non-admins
        div.innerHTML = `${u.id} — ${u.name} (${u.username})`;
      }

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

async function showSavedTeams(eventId) {
  console.log('showSavedTeams called for eventId=', eventId);
  try {
    // call server
    const teams = await api(`/events/${eventId}/teams`, { method: 'GET' });
    console.log('teams response:', teams);

    // ensure container exists
    const c = document.getElementById('teams-container');
    if (!c) {
      console.warn('teams-container element not found in DOM');
      alert('UI error: cannot find teams container (element id="teams-container")');
      return;
    }

    // clear previous contents
    c.innerHTML = '';

    // handle empty response
    if (!teams || teams.length === 0) {
      const emptyDiv = document.createElement('div');
      emptyDiv.className = 'card';
      emptyDiv.innerText = 'No saved teams for this event';
      c.appendChild(emptyDiv);
      alert('No saved teams for this event');
      return;
    }

    // determine whether current viewer is an admin
    const isAdmin = !!(currentUser && currentUser.isAdmin);

    // render teams
    teams.forEach((t, idx) => {
      const div = document.createElement('div');
      div.className = 'card';

      // friendly member text: Name (skill: X) only for admins
      const members = (t.members || []).map(m => {
        const name = m && (m.name || m.username) ? (m.name || m.username) : ('id:' + (m && (m.id || '?')));
        if (isAdmin) {
          const skill = typeof m.skill === 'number' ? m.skill : (m.skill ? m.skill : 0);
          return `${name} (skill: ${skill})`;
        }
        return name; // non-admins see only names
      }).join(', ');

      // compute total skill only for admins (otherwise don't expose it)
      let headerText = `Team ${t.teamIndex || (idx + 1)}`;
      if (isAdmin) {
        const totalSkill = (t.members || []).reduce((sum, m) => {
          const skill = typeof m.skill === 'number' ? m.skill : (m.skill ? m.skill : 0);
          return sum + skill;
        }, 0);
        headerText += `   -  Skill ${totalSkill}`;
      }

      div.innerHTML = `<strong>${headerText}</strong><div>${members}</div>`;

      // compute teamIndex numeric fallback
      const teamIndex = (t.teamIndex || (idx + 1));

      if (currentUser && currentUser.isAdmin) {
        const plus = document.createElement('button');
        plus.innerText = '+';
        plus.onclick = () => {
          console.log('adminAdjust + clicked (saved)', { eventId, teamIndex });
          adminAdjustTeamSkill(eventId, teamIndex, +1);
        };
        const minus = document.createElement('button');
        minus.innerText = '−';
        minus.onclick = () => {
          console.log('adminAdjust - clicked (saved)', { eventId, teamIndex });
          adminAdjustTeamSkill(eventId, teamIndex, -1);
        };

        const recordBtn = document.createElement('button');
        recordBtn.innerText = 'Record result';
        recordBtn.onclick = () => promptAndRecordMatches(eventId);

        div.appendChild(plus);
        div.appendChild(minus);
        div.appendChild(recordBtn);
      }

      c.appendChild(div);
    });


    // ensure admin teams panel is visible
    setVisible('teams-card', true);

    // select the event in the team-select dropdown (nice UX)
    const select = document.getElementById('event-select');
    if (select) select.value = String(eventId);

    // scroll into view a little so user sees results
    c.scrollIntoView({ behavior: 'smooth', block: 'start' });

  } catch (e) {
    console.error('showSavedTeams error', e);
    // unwrap error message where possible
    const msg = (e && e.message) ? e.message : String(e);
    alert('Failed to load teams: ' + msg);
  }
}

async function subscribeUser(publicVapidKeyBase64Url) {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    console.warn('Push not supported');
    return;
  }

  const swReg = await navigator.serviceWorker.register('/sw.js');
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('permission not granted');

  const convertedKey = urlBase64ToUint8Array(publicVapidKeyBase64Url);

  const sub = await swReg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: convertedKey
  });

  // send subscription to server
  await fetch('/api/push/subscribe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sub)
  });

  return sub;
}

/* helper */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding).replace(/\-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; ++i) output[i] = raw.charCodeAt(i);
  return output;
}
// after showing logged-in user, request public key and subscribe
async function ensureSubscribed() {
  try {
    const res = await fetch('/api/push/vapidPublicKey');
    if (!res.ok) return;
    const data = await res.json();
    const publicKey = data.publicKey;
    await subscribeUser(publicKey);
    console.log('Subscribed to push service');
  } catch (e) {
    console.warn('subscribe error', e);
  }
}

// call to adjust skill for each player on a team by delta (admin-only)
async function adminAdjustTeamSkill(eventId, teamIndex, delta) {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  try {
    const res = await api(`/admin/events/${eventId}/teams/${teamIndex}/adjust-skill`, {
      method: 'POST',
      body: JSON.stringify({ delta })
    });
    alert('Adjusted team skill by ' + delta);
    // refresh saved teams and user list if admin
    await fetchEvents();
    if (currentUser && currentUser.isAdmin) fetchAllUsers().catch(() => { });
  } catch (e) {
    alert('Adjust failed: ' + e.message);
  }
}

// record matches: expects JSON array of matches: e.g.
// [{"teamA":1,"teamB":2,"winner":1},{"teamA":3,"teamB":4,"winner":4}]
async function adminRecordMatches(eventId, matches, kFactor = 24) {
  if (!currentUser || !currentUser.isAdmin) { alert('Admin only'); return; }
  try {
    await api(`/admin/events/${eventId}/matches`, {
      method: 'POST',
      body: JSON.stringify({ matches, kFactor })
    });
    alert('Match results recorded (ELO updated)');
    await fetchEvents();
  } catch (e) {
    alert('Record matches failed: ' + e.message);
  }
}

// helper that prompts admin to enter match JSON (simple flow)
async function promptAndRecordMatches(eventId) {
  const example = '[{"teamA":1,"teamB":2,"winner":1}]';
  const raw = prompt('Enter matches as JSON array. Example: ' + example, example);
  if (!raw) return;
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    alert('Invalid JSON: ' + e.message);
    return;
  }
  const kStr = prompt('kFactor (optional, default 24)', '24');
  const k = parseInt(kStr || '24', 10);
  await adminRecordMatches(eventId, parsed, k);
}


// call this from a button or after login if you want user to opt-in interactively
async function subscribeToPush() {
  if (!currentUser) {
    alert('Please log in to enable notifications.');
    return;
  }
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) {
    alert("Push / Service Worker not supported in this browser");
    return;
  }

  try {
    const registration = await navigator.serviceWorker.register("/sw.js");
    console.log("Service worker registered");

    const permission = await Notification.requestPermission();
    if (permission !== "granted") {
      alert("Notifications blocked");
      return;
    }

    // Use your VAPID key (you already use this pattern elsewhere)
    const publicKeyResp = await fetch('/api/push/vapidPublicKey');
    if (!publicKeyResp.ok) {
      console.warn('Could not fetch VAPID public key');
      return;
    }
    const { publicKey } = await publicKeyResp.json();

    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey)
    });

    console.log("Push subscription:", subscription);

    // POST to your backend subscription endpoint (include auth token if available)
    const headers = { 'Content-Type': 'application/json' };
    if (currentToken) headers['X-Auth-Token'] = currentToken;

    const res = await fetch('/api/subscriptions', {
      method: 'POST',
      headers,
      body: JSON.stringify(subscription)
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(res.status + ' ' + text);
    }

    alert("Subscribed to notifications!");
  } catch (err) {
    console.error("subscribeToPush error:", err);
    alert("Subscription failed: " + err.message);
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
      // call when user logs in or when page init sees user is logged in
      if (currentUser) {
        ensureSubscribed().catch(err => console.warn('subscribe failed', err));
      }
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
