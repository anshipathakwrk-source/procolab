/* Tandem - Project Collaboration Platform (vanilla JS single-page frontend) */
const API = '/api';
const STATUSES = [['TODO', 'To do'], ['IN_PROGRESS', 'In progress'], ['DONE', 'Done']];
const PRIORITIES = [['LOW', 'Low'], ['MEDIUM', 'Medium'], ['HIGH', 'High']];

const S = {
  token: localStorage.getItem('token'),
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  projects: [], current: null, tab: 'board',
  tasks: [], members: [], files: [], activity: [], progress: null, comments: [], replyTo: null, openTaskId: null
};

/* ---------- helpers ---------- */
const $ = (s, r = document) => r.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const initials = n => String(n || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
const fmtDate = d => d ? new Date(d + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '';
const fmtTime = t => new Date(t).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const fmtSize = b => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB';
const label = (list, v) => (list.find(x => x[0] === v) || [v, v])[1];
const canManage = () => S.current && S.current.myRole !== 'MEMBER';

function toast(msg, isErr) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'show' + (isErr ? ' err' : '');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.className = ''), 3200);
}

async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (S.token) headers.Authorization = 'Bearer ' + S.token;
  let payload;
  if (form) payload = form;
  else if (body) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch(API + path, { method, headers, body: payload });
  if (res.status === 401 && S.token) { logout(); throw new Error('Your session expired. Sign in again.'); }
  if (!res.ok) {
    let msg = 'Something went wrong. Try again.';
    try { msg = (await res.json()).message || msg; } catch (_) { /* no body */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

function showModal(html, cls = '') {
  const m = $('#modal');
  m.className = cls;
  m.innerHTML = html;
  if (!m.open) m.showModal();
}
function closeModal() { const m = $('#modal'); if (m.open) m.close(); S.openTaskId = null; }
$('#modal').addEventListener('click', e => { if (e.target.id === 'modal') closeModal(); });
$('#modal').addEventListener('close', () => { S.openTaskId = null; });

/* ---------- auth ---------- */
let authMode = 'login';
function setAuthMode(mode) {
  authMode = mode;
  $('#tab-login').classList.toggle('on', mode === 'login');
  $('#tab-register').classList.toggle('on', mode === 'register');
  $('#name-field').hidden = mode === 'login';
  $('#name-field input').required = mode === 'register';
  $('#auth-submit').textContent = mode === 'login' ? 'Sign in' : 'Create account';
  $('#auth-form [name=password]').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
  $('#auth-error').textContent = '';
}

async function submitAuth(e) {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  const body = authMode === 'login'
    ? { email: f.email, password: f.password }
    : { name: f.name, email: f.email, password: f.password };
  try {
    const r = await api('/auth/' + authMode, { method: 'POST', body });
    S.token = r.token; S.user = r.user;
    localStorage.setItem('token', r.token);
    localStorage.setItem('user', JSON.stringify(r.user));
    e.target.reset();
    await startApp();
  } catch (err) { $('#auth-error').textContent = err.message; }
}

function logout() {
  localStorage.removeItem('token'); localStorage.removeItem('user');
  S.token = null; S.user = null; S.current = null; S.projects = [];
  closeModal();
  $('#app-view').hidden = true;
  $('#auth-view').hidden = false;
  setAuthMode('login');
}

async function startApp() {
  $('#auth-view').hidden = true;
  $('#app-view').hidden = false;
  $('#me-avatar').textContent = initials(S.user.name);
  $('#me-name').textContent = S.user.name;
  $('#me-email').textContent = S.user.email;
  await loadProjects();
  renderHome();
}

/* ---------- sidebar ---------- */
async function loadProjects() {
  try { S.projects = await api('/projects'); } catch (e) { toast(e.message, true); }
  renderSidebar();
}

function renderSidebar() {
  $('.side-home').classList.toggle('on', !S.current);
  $('#project-list').innerHTML = S.projects.length
    ? S.projects.map(p => `
      <button class="project-item ${S.current && S.current.id === p.id ? 'on' : ''}" onclick="openProject(${p.id})">
        <b>${esc(p.title)}</b>
        <div class="bar"><i style="width:${p.progress}%"></i></div>
        <small>${p.progress}% done${p.status === 'ARCHIVED' ? ' · archived' : ''}</small>
      </button>`).join('')
    : '<p class="side-empty">No projects yet.</p>';
}

/* ---------- home: my tasks ---------- */
async function renderHome() {
  S.current = null;
  renderSidebar();
  let mine = [];
  try { mine = await api('/tasks/mine'); } catch (e) { toast(e.message, true); }
  const late = mine.filter(t => t.overdue).length;
  const first = S.user.name.split(' ')[0];
  const summary = mine.length
    ? `You have ${mine.length} open task${mine.length > 1 ? 's' : ''} assigned to you${late ? `, ${late} overdue` : ''}.`
    : 'Nothing is assigned to you right now.';
  $('#main').innerHTML = `
    <header class="phead">
      <div class="ptitle"><h1>Hello, ${esc(first)}</h1><p>${summary}</p></div>
      <div class="actions"><button class="btn primary" onclick="projectForm()">New project</button></div>
    </header>
    <div class="tabs"><button class="on">My open tasks</button></div>
    ${mine.length ? `<div class="panel">${mine.map(t => `
      <div class="list-row task-row" tabindex="0" onclick="goTask(${t.projectId},${t.id})" onkeydown="if(event.key==='Enter')goTask(${t.projectId},${t.id})">
        <span class="dot ${t.priority}" title="${label(PRIORITIES, t.priority)} priority"></span>
        <div class="grow"><b>${esc(t.title)}</b><small>${esc(t.projectTitle)} · ${label(STATUSES, t.status)}</small></div>
        ${t.dueDate ? `<small class="due ${t.overdue ? 'late' : ''}">${t.overdue ? 'Overdue · ' : 'Due '}${fmtDate(t.dueDate)}</small>` : ''}
      </div>`).join('')}</div>`
      : `<div class="empty">${S.projects.length ? 'Open a project to see its board.' : 'Create your first project, then add tasks and invite your team.'}</div>`}`;
}

async function goTask(pid, tid) { await openProject(pid); openTask(tid); }

/* ---------- project ---------- */
async function openProject(id, tab = 'board') {
  try {
    const [project, tasks, members, progress] = await Promise.all([
      api('/projects/' + id), api(`/projects/${id}/tasks`), api(`/projects/${id}/members`), api(`/projects/${id}/progress`)
    ]);
    Object.assign(S, { current: project, tasks, members, progress, tab });
    renderSidebar();
    renderProject();
  } catch (e) { toast(e.message, true); }
}

async function refresh() {
  const id = S.current.id;
  const [project, tasks, members, progress] = await Promise.all([
    api('/projects/' + id), api(`/projects/${id}/tasks`), api(`/projects/${id}/members`), api(`/projects/${id}/progress`)
  ]);
  Object.assign(S, { current: project, tasks, members, progress });
  S.projects = await api('/projects');
  renderSidebar();
  renderProject();
}

function renderProject() {
  const p = S.current, pr = S.progress;
  const archived = p.status === 'ARCHIVED';
  const dates = [p.startDate && 'Starts ' + fmtDate(p.startDate), p.endDate && 'Due ' + fmtDate(p.endDate)].filter(Boolean).join(' · ');
  const w = n => pr.total ? (n / pr.total * 100) : 0;
  $('#main').innerHTML = `
    <header class="phead">
      <div class="ptitle">
        <h1>${esc(p.title)}</h1>${archived ? '<span class="badge">Archived</span>' : ''}
        <p>${esc(p.description || 'No description yet.')}</p>
        <div class="meta">Owned by ${esc(p.ownerName)} · ${p.memberCount} member${p.memberCount > 1 ? 's' : ''}${dates ? ' · ' + dates : ''}</div>
      </div>
      <div class="actions">
        ${canManage() ? '<button class="btn" onclick="projectForm(true)">Edit project</button>' : ''}
        ${p.myRole === 'OWNER' ? `<button class="btn" onclick="toggleArchive()">${archived ? 'Restore project' : 'Archive project'}</button>` : ''}
        <button class="btn primary" onclick="taskForm()">New task</button>
      </div>
    </header>
    <section class="progress" aria-label="Project progress">
      <div>
        <h3>${pr.percent}% complete</h3>
        <div class="pbar" role="progressbar" aria-valuenow="${pr.percent}" aria-valuemin="0" aria-valuemax="100">
          <i class="d" style="width:${w(pr.done)}%"></i><i class="p" style="width:${w(pr.inProgress)}%"></i>
        </div>
      </div>
      <div class="stats">
        <div><b>${pr.todo}</b><small>To do</small></div>
        <div><b>${pr.inProgress}</b><small>In progress</small></div>
        <div><b>${pr.done}</b><small>Done</small></div>
        <div class="over"><b>${pr.overdue}</b><small>Overdue</small></div>
      </div>
    </section>
    <nav class="tabs">
      ${[['board', 'Board'], ['files', 'Files'], ['team', 'Team'], ['activity', 'Activity']]
        .map(([k, n]) => `<button class="${S.tab === k ? 'on' : ''}" onclick="setTab('${k}')">${n}</button>`).join('')}
    </nav>
    <div id="tab-body"></div>`;
  renderTab();
}

async function setTab(t) { S.tab = t; renderProject(); }

async function renderTab() {
  const body = $('#tab-body');
  try {
    if (S.tab === 'board') renderBoard(body);
    else if (S.tab === 'files') { S.files = await api(`/projects/${S.current.id}/files`); renderFiles(body); }
    else if (S.tab === 'team') renderTeam(body);
    else { S.activity = await api(`/projects/${S.current.id}/activity`); renderActivity(body); }
  } catch (e) { toast(e.message, true); }
}

/* ---------- board (drag and drop) ---------- */
function renderBoard(body) {
  body.innerHTML = `<div class="board">${STATUSES.map(([key, name]) => {
    const items = S.tasks.filter(t => t.status === key);
    return `<section class="col" data-status="${key}" ondragover="onDragOver(event)" ondragleave="this.classList.remove('over')" ondrop="onDrop(event,'${key}')">
      <h3>${name}<span>${items.length}</span></h3>
      ${items.length ? items.map(taskCard).join('') : `<p class="empty">${key === 'TODO' ? 'Add a task to get started.' : 'Drag tasks here.'}</p>`}
    </section>`;
  }).join('')}</div>`;
}

function taskCard(t) {
  return `<div class="card ${t.priority.toLowerCase()} ${t.status === 'DONE' ? 'is-done' : ''}" draggable="true" tabindex="0" role="button"
      ondragstart="onDragStart(event,${t.id})" ondragend="this.classList.remove('dragging')"
      onclick="openTask(${t.id})" onkeydown="if(event.key==='Enter')openTask(${t.id})">
    <b>${esc(t.title)}</b>
    <div class="card-foot">
      <span class="due ${t.overdue ? 'late' : ''}">${t.dueDate ? (t.overdue ? 'Overdue · ' : '') + fmtDate(t.dueDate) : 'No due date'}${t.commentCount ? ' · ' + t.commentCount + ' comment' + (t.commentCount > 1 ? 's' : '') : ''}</span>
      ${t.assigneeName ? `<span class="avatar" title="${esc(t.assigneeName)}">${esc(initials(t.assigneeName))}</span>` : '<span>Unassigned</span>'}
    </div>
  </div>`;
}

function onDragStart(e, id) { e.dataTransfer.setData('text/plain', String(id)); e.dataTransfer.effectAllowed = 'move'; e.currentTarget.classList.add('dragging'); }
function onDragOver(e) { e.preventDefault(); e.currentTarget.classList.add('over'); }
async function onDrop(e, status) {
  e.preventDefault();
  e.currentTarget.classList.remove('over');
  const id = Number(e.dataTransfer.getData('text/plain'));
  const t = S.tasks.find(x => x.id === id);
  if (t && t.status !== status) await changeStatus(id, status);
}

async function changeStatus(id, status) {
  try {
    await api(`/tasks/${id}/status`, { method: 'PATCH', body: { status } });
    await refresh();
    if (S.openTaskId) drawTask();
    toast('Moved to ' + label(STATUSES, status));
  } catch (e) { toast(e.message, true); }
}

/* ---------- task form ---------- */
function taskForm(id) {
  const t = id ? S.tasks.find(x => x.id === id) : null;
  showModal(`<form class="dlg" onsubmit="saveTask(event,${id || 'null'})">
    <h2>${t ? 'Edit task' : 'New task'}</h2>
    <label>Title<input name="title" required maxlength="200" value="${esc(t?.title)}"></label>
    <label>Description<textarea name="description">${esc(t?.description)}</textarea></label>
    <div class="row">
      <label>Assign to<select name="assigneeId"><option value="">Unassigned</option>
        ${S.members.map(m => `<option value="${m.userId}" ${t?.assigneeId === m.userId ? 'selected' : ''}>${esc(m.name)}</option>`).join('')}</select></label>
      <label>Priority<select name="priority">
        ${PRIORITIES.map(([k, n]) => `<option value="${k}" ${(t?.priority || 'MEDIUM') === k ? 'selected' : ''}>${n}</option>`).join('')}</select></label>
    </div>
    <label>Due date<input type="date" name="dueDate" value="${t?.dueDate || ''}"></label>
    <div class="dlg-actions"><button type="button" class="btn" onclick="closeModal()">Cancel</button><button class="btn primary">${t ? 'Save task' : 'Create task'}</button></div>
  </form>`);
  $('#modal [name=title]').focus();
}

async function saveTask(e, id) {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  const body = { title: f.title, description: f.description, assigneeId: f.assigneeId ? Number(f.assigneeId) : null, priority: f.priority, dueDate: f.dueDate || null };
  try {
    await api(id ? `/tasks/${id}` : `/projects/${S.current.id}/tasks`, { method: id ? 'PUT' : 'POST', body });
    closeModal(); await refresh(); toast(id ? 'Task saved' : 'Task created');
  } catch (err) { toast(err.message, true); }
}

async function deleteTask(id) {
  if (!confirm('Delete this task and its comments? Attached files stay in the project.')) return;
  try { await api('/tasks/' + id, { method: 'DELETE' }); closeModal(); await refresh(); toast('Task deleted'); }
  catch (e) { toast(e.message, true); }
}

/* ---------- task detail + comments ---------- */
async function openTask(id) {
  const t = S.tasks.find(x => x.id === id);
  if (!t) return;
  S.openTaskId = id; S.replyTo = null;
  try { S.comments = await api(`/tasks/${id}/comments`); } catch (e) { return toast(e.message, true); }
  drawTask();
}

function drawTask() {
  const t = S.tasks.find(x => x.id === S.openTaskId);
  if (!t) return closeModal();
  const reply = S.comments.find(c => c.id === S.replyTo);
  showModal(`<div class="dlg">
    <div class="drawer-head">
      <h2>${esc(t.title)}</h2>
      <button class="btn" onclick="closeModal()" aria-label="Close">Close</button>
    </div>
    <dl class="kv">
      <dt>Status</dt><dd><select onchange="changeStatus(${t.id},this.value)" style="margin:0;width:auto">
        ${STATUSES.map(([k, n]) => `<option value="${k}" ${t.status === k ? 'selected' : ''}>${n}</option>`).join('')}</select></dd>
      <dt>Assignee</dt><dd>${esc(t.assigneeName || 'Unassigned')}</dd>
      <dt>Priority</dt><dd><span class="dot ${t.priority}" style="display:inline-block;margin-right:6px"></span>${label(PRIORITIES, t.priority)}</dd>
      <dt>Due</dt><dd class="due ${t.overdue ? 'late' : ''}">${t.dueDate ? fmtDate(t.dueDate) + (t.overdue ? ' (overdue)' : '') : 'No due date'}</dd>
    </dl>
    ${t.description ? `<p class="desc">${esc(t.description)}</p>` : ''}
    <div class="actions"><button class="btn" onclick="taskForm(${t.id})">Edit task</button><button class="btn danger" onclick="deleteTask(${t.id})">Delete task</button></div>
    <section class="cmts">
      <h3>Comments (${S.comments.length})</h3>
      ${S.comments.length ? `<ul>${commentTree(null)}</ul>` : '<p class="empty" style="padding:8px 0;text-align:left">No comments yet. Start the discussion below.</p>'}
      <form onsubmit="postComment(event)">
        ${reply ? `<div class="reply-note"><span>Replying to ${esc(reply.userName)}</span><button type="button" class="link" onclick="setReply(null)">Cancel</button></div>` : ''}
        <textarea name="content" placeholder="Write a comment" required></textarea>
        <div class="dlg-actions"><button class="btn primary">${reply ? 'Post reply' : 'Post comment'}</button></div>
      </form>
    </section>
  </div>`, 'drawer');
}

function commentTree(parent) {
  return S.comments.filter(c => c.parentId === parent).map(c => {
    const mine = c.userId === S.user.id;
    const kids = S.comments.some(x => x.parentId === c.id);
    return `<li class="cmt">
      <div class="cmt-h"><b>${esc(c.userName)}</b><time>${fmtTime(c.createdAt)}</time></div>
      <p>${esc(c.content)}</p>
      <div class="cmt-a"><button class="link" onclick="setReply(${c.id})">Reply</button>
        ${mine || canManage() ? `<button class="link danger" onclick="removeComment(${c.id})">Delete</button>` : ''}</div>
      ${kids ? `<ul>${commentTree(c.id)}</ul>` : ''}
    </li>`;
  }).join('');
}

function setReply(id) { S.replyTo = id; drawTask(); const ta = $('#modal textarea'); if (ta) ta.focus(); }

async function postComment(e) {
  e.preventDefault();
  const content = new FormData(e.target).get('content');
  try {
    await api(`/tasks/${S.openTaskId}/comments`, { method: 'POST', body: { content, parentId: S.replyTo } });
    S.replyTo = null;
    S.comments = await api(`/tasks/${S.openTaskId}/comments`);
    S.tasks = await api(`/projects/${S.current.id}/tasks`);
    drawTask(); renderBoardIfVisible();
  } catch (err) { toast(err.message, true); }
}

async function removeComment(id) {
  if (!confirm('Delete this comment and its replies?')) return;
  try {
    await api('/comments/' + id, { method: 'DELETE' });
    S.comments = await api(`/tasks/${S.openTaskId}/comments`);
    S.tasks = await api(`/projects/${S.current.id}/tasks`);
    drawTask(); renderBoardIfVisible();
  } catch (e) { toast(e.message, true); }
}
function renderBoardIfVisible() { if (S.tab === 'board' && $('#tab-body')) renderBoard($('#tab-body')); }

/* ---------- files ---------- */
function renderFiles(body) {
  body.innerHTML = `<div class="panel">
    <form class="inline" onsubmit="uploadFile(event)">
      <label>File (max 10 MB)<input type="file" name="file" required></label>
      <label>Attach to<select name="taskId"><option value="">Whole project</option>
        ${S.tasks.map(t => `<option value="${t.id}">${esc(t.title)}</option>`).join('')}</select></label>
      <button class="btn primary">Upload file</button>
    </form>
    ${S.files.length ? S.files.map(f => `<div class="list-row">
        <div class="grow"><b>${esc(f.fileName)}</b>
          <small>${fmtSize(f.size)} · ${esc(f.uploadedByName)} · ${fmtTime(f.uploadedAt)}${f.taskTitle ? ' · on “' + esc(f.taskTitle) + '”' : ''}</small></div>
        <button class="btn" onclick="downloadFile(${f.id})">Download</button>
        <button class="link danger" onclick="removeFile(${f.id})">Delete</button>
      </div>`).join('') : '<p class="empty">No files yet. Upload specs, designs or reports for the team.</p>'}
  </div>`;
}

async function uploadFile(e) {
  e.preventDefault();
  const form = new FormData(e.target);
  if (!form.get('taskId')) form.delete('taskId');
  try { await api(`/projects/${S.current.id}/files`, { method: 'POST', form }); toast('File uploaded'); renderTab(); }
  catch (err) { toast(err.message, true); }
}

async function downloadFile(id) {
  const f = S.files.find(x => x.id === id);
  try {
    const r = await fetch(`${API}/files/${id}/download`, { headers: { Authorization: 'Bearer ' + S.token } });
    if (!r.ok) throw new Error('Download failed. The file may have been removed.');
    const url = URL.createObjectURL(await r.blob());
    const a = Object.assign(document.createElement('a'), { href: url, download: f ? f.fileName : 'file' });
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  } catch (e) { toast(e.message, true); }
}

async function removeFile(id) {
  if (!confirm('Delete this file?')) return;
  try { await api('/files/' + id, { method: 'DELETE' }); toast('File deleted'); renderTab(); }
  catch (e) { toast(e.message, true); }
}

/* ---------- team ---------- */
function renderTeam(body) {
  body.innerHTML = `<div class="panel">
    ${canManage() ? `<form class="inline" onsubmit="addMember(event)">
      <label>Teammate's email<input type="email" name="email" required placeholder="name@example.com"></label>
      <label style="flex:0 1 160px">Role<select name="role"><option value="MEMBER">Member</option><option value="MANAGER">Manager</option></select></label>
      <button class="btn primary">Add to project</button>
    </form>` : ''}
    ${S.members.map(m => `<div class="list-row">
      <span class="avatar">${esc(initials(m.name))}</span>
      <div class="grow"><b>${esc(m.name)}</b><small>${esc(m.email)}</small></div>
      <span class="chip ${m.role}">${m.role.charAt(0) + m.role.slice(1).toLowerCase()}</span>
      ${canManage() && m.role !== 'OWNER' ? `<button class="link danger" onclick="removeMember(${m.id})">Remove</button>` : ''}
    </div>`).join('')}
  </div>`;
}

async function addMember(e) {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  try { await api(`/projects/${S.current.id}/members`, { method: 'POST', body: f }); await refresh(); toast('Teammate added'); }
  catch (err) { toast(err.message, true); }
}

async function removeMember(id) {
  if (!confirm('Remove this person from the project? Their tasks stay assigned until you change them.')) return;
  try { await api(`/projects/${S.current.id}/members/${id}`, { method: 'DELETE' }); await refresh(); toast('Teammate removed'); }
  catch (e) { toast(e.message, true); }
}

/* ---------- activity ---------- */
function renderActivity(body) {
  body.innerHTML = `<div class="panel">${S.activity.length ? S.activity.map(a => `
    <div class="list-row"><span class="avatar">${esc(initials(a.userName))}</span>
      <div class="grow"><b>${esc(a.userName)} ${esc(a.action)}</b><small>${fmtTime(a.createdAt)}</small></div></div>`).join('')
    : '<p class="empty">No activity yet.</p>'}</div>`;
}

/* ---------- project form ---------- */
function projectForm(edit) {
  const p = edit ? S.current : null;
  showModal(`<form class="dlg" onsubmit="saveProject(event,${edit ? 'true' : 'false'})">
    <h2>${p ? 'Edit project' : 'New project'}</h2>
    <label>Title<input name="title" required maxlength="150" value="${esc(p?.title)}"></label>
    <label>Description<textarea name="description">${esc(p?.description)}</textarea></label>
    <div class="row">
      <label>Start date<input type="date" name="startDate" value="${p?.startDate || ''}"></label>
      <label>End date<input type="date" name="endDate" value="${p?.endDate || ''}"></label>
    </div>
    <div class="dlg-actions"><button type="button" class="btn" onclick="closeModal()">Cancel</button><button class="btn primary">${p ? 'Save project' : 'Create project'}</button></div>
  </form>`);
  $('#modal [name=title]').focus();
}

async function saveProject(e, edit) {
  e.preventDefault();
  const f = Object.fromEntries(new FormData(e.target));
  const body = { title: f.title, description: f.description, startDate: f.startDate || null, endDate: f.endDate || null };
  try {
    if (edit) { await api('/projects/' + S.current.id, { method: 'PUT', body }); closeModal(); await refresh(); toast('Project saved'); }
    else { const p = await api('/projects', { method: 'POST', body }); closeModal(); await loadProjects(); await openProject(p.id); toast('Project created'); }
  } catch (err) { toast(err.message, true); }
}

async function toggleArchive() {
  const archiving = S.current.status === 'ACTIVE';
  if (archiving && !confirm('Archive this project? You can restore it later.')) return;
  try { await api(`/projects/${S.current.id}/archive`, { method: 'PATCH' }); await refresh(); toast(archiving ? 'Project archived' : 'Project restored'); }
  catch (e) { toast(e.message, true); }
}

/* ---------- boot ---------- */
setAuthMode('login');
if (S.token && S.user) startApp().catch(() => logout());
else $('#auth-view').hidden = false;
