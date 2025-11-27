const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const params = new URLSearchParams(window.location.search);
const userId = params.get('id');

const userIdEl = document.getElementById('userId');
const userNameEl = document.getElementById('userName');
const userEmailEl = document.getElementById('userEmail');
const userBadgesEl = document.getElementById('userBadges');
const userNicknameEl = document.getElementById('userNickname');
const userStatusEl = document.getElementById('userStatus');
const userWarnsEl = document.getElementById('userWarns');
const userCreatedEl = document.getElementById('userCreated');
const userSanctionEl = document.getElementById('userSanction');
const userSuspendedEl = document.getElementById('userSuspended');
const userPostCountEl = document.getElementById('userPostCount');
const userCommentCountEl = document.getElementById('userCommentCount');
const userReportCountEl = document.getElementById('userReportCount');
const userActionStatusEl = document.getElementById('userActionStatus');
const warnUserBtn = document.getElementById('warnUserBtn');
const blockUserBtn = document.getElementById('blockUserBtn');
const unblockUserBtn = document.getElementById('unblockUserBtn');
const deleteUserBtn = document.getElementById('deleteUserBtn');
const userPostsEl = document.getElementById('userPosts');
const backToUsersBtn = document.getElementById('backToUsers');
const userActionModal = document.getElementById('userActionModal');
const userActionModalTitle = document.getElementById('userActionModalTitle');
const userActionModalNote = document.getElementById('userActionModalNote');
const userActionModalConfirm = document.getElementById('userActionModalConfirm');
const userActionModalCancel = document.getElementById('userActionModalCancel');

async function fetchJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  if (!response.ok) {
    const text = await response.text();
    const error = new Error(text || '요청이 실패했습니다.');
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  const raw = await response.text();
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function ensureAuth(err) {
  if (err && (err.status === 401 || err.status === 403)) {
    window.location.href = 'login.html';
    return true;
  }
  return false;
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ko-KR');
}

function setActionStatus(message, status = '') {
  if (!userActionStatusEl) return;
  userActionStatusEl.textContent = message || '';
  userActionStatusEl.dataset.status = status;
}

function renderUserBadges(detail) {
  const badges = [];
  if (!detail.enabled) {
    badges.push('<span class="badge badge-warning">차단됨</span>');
  }
  if ((detail.warnCount || 0) > 0) {
  badges.push(`<span class="badge badge-danger">경고 ${detail.warnCount}</span>`);
  }
  userBadgesEl.innerHTML = badges.join('') || '<span class="text-muted">-</span>';
}

function openUserActionModal(title, preset = '') {
  if (!userActionModal || !userActionModalTitle || !userActionModalNote || !userActionModalConfirm || !userActionModalCancel) {
    const fallback = window.prompt(title, preset || '');
    return Promise.resolve(fallback ? fallback.trim() : '');
  }
  userActionModalTitle.textContent = title;
  userActionModalNote.value = preset || '';
  userActionModal.classList.remove('hidden');
  userActionModalNote.focus();

  return new Promise((resolve) => {
    const cleanup = () => {
      userActionModal.classList.add('hidden');
      userActionModalNote.value = '';
      userActionModalConfirm.removeEventListener('click', onConfirm);
      userActionModalCancel.removeEventListener('click', onCancel);
      userActionModal.removeEventListener('click', onBackdrop);
      document.removeEventListener('keydown', onKey);
    };
    const onConfirm = () => {
      const value = userActionModalNote.value.trim();
      cleanup();
      resolve(value);
    };
    const onCancel = () => {
      cleanup();
      resolve('');
    };
    const onBackdrop = (event) => {
      if (event.target === userActionModal) {
        cleanup();
        resolve('');
      }
    };
    const onKey = (event) => {
      if (event.key === 'Escape') {
        cleanup();
        resolve('');
      }
      if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
        onConfirm();
      }
    };
    userActionModalConfirm.addEventListener('click', onConfirm);
    userActionModalCancel.addEventListener('click', onCancel);
    userActionModal.addEventListener('click', onBackdrop);
    document.addEventListener('keydown', onKey);
  });
}

async function loadUserDetail() {
  if (!userId) {
    setActionStatus('사용자 ID가 지정되지 않았습니다.', 'error');
    return;
  }
  userIdEl.textContent = userId;
  try {
    const detail = await fetchJson(`/api/admin/users/${userId}`);
    userNameEl.textContent = detail.username || '사용자';
    userEmailEl.textContent = detail.email || '-';
    userNicknameEl.textContent = detail.nickname || '-';
    userStatusEl.textContent = detail.status || '-';
    userWarnsEl.textContent = detail.warnCount ?? 0;
    userCreatedEl.textContent = formatDateTime(detail.createdAt);
    userSanctionEl.textContent = formatDateTime(detail.lastSanctionAt);
    userSuspendedEl.textContent = formatDateTime(detail.suspendedAt);
    userPostCountEl.textContent = detail.postCount ?? 0;
    userCommentCountEl.textContent = detail.commentCount ?? 0;
    userReportCountEl.textContent = detail.reportCount ?? 0;
    renderUserBadges(detail);
  } catch (err) {
    if (ensureAuth(err)) return;
    setActionStatus(err.message || '사용자 정보를 불러오지 못했습니다.', 'error');
  }
}

async function loadUserPosts() {
  if (!userId) return;
  try {
    const posts = await fetchJson(`/api/admin/posts?authorId=${userId}`);
    if (!posts.length) {
      userPostsEl.innerHTML = '<p class="text-muted">작성한 게시글이 없습니다.</p>';
      return;
    }
    const rows = posts.map((post) => `
      <tr>
        <td>#${post.id}</td>
        <td>${post.title || '(제목 없음)'}</td>
        <td>${post.mainBoardName || '-'}</td>
        <td>${formatDateTime(post.createdAt)}</td>
        <td>
          <button class="btn btn-outline btn-sm" data-post-id="${post.id}">상세</button>
        </td>
      </tr>
    `).join('');
    userPostsEl.innerHTML = `
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>제목</th>
            <th>대메뉴</th>
            <th>작성 시각</th>
            <th></th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    `;
    userPostsEl.querySelectorAll('button[data-post-id]').forEach((btn) => {
      btn.addEventListener('click', () => {
        window.location.href = `post-detail.html?id=${btn.dataset.postId}`;
      });
    });
  } catch (err) {
    if (ensureAuth(err)) return;
    userPostsEl.innerHTML = `<p class="text-muted">${err.message || '게시글을 불러오지 못했습니다.'}</p>`;
  }
}

if (warnUserBtn) {
  warnUserBtn.addEventListener('click', async () => {
    if (!userId) return;
    const message = await openUserActionModal('경고 사유를 입력하세요.');
    if (!message) {
      return;
    }
    try {
      await fetchJson(`/api/admin/users/${userId}/warn`, {
        method: 'POST',
        body: JSON.stringify({ message })
      });
      setActionStatus('경고를 발송했습니다.', 'success');
      await loadUserDetail();
    } catch (err) {
      if (ensureAuth(err)) return;
      setActionStatus(err.message || '경고 발송 실패', 'error');
    }
  });
}

if (blockUserBtn) {
  blockUserBtn.addEventListener('click', async () => {
    if (!userId) return;
    const message = await openUserActionModal('차단 사유를 입력하세요.');
    if (!message) {
      return;
    }
    try {
      await fetchJson(`/api/admin/users/${userId}/block`, {
        method: 'POST',
        body: JSON.stringify({ message })
      });
      setActionStatus('사용자를 차단했습니다.', 'success');
      await loadUserDetail();
    } catch (err) {
      if (ensureAuth(err)) return;
      setActionStatus(err.message || '차단 실패', 'error');
    }
  });
}

if (unblockUserBtn) {
  unblockUserBtn.addEventListener('click', async () => {
    if (!userId) return;
    try {
      await fetchJson(`/api/admin/users/${userId}/unblock`, { method: 'POST' });
      setActionStatus('차단을 해제했습니다.', 'success');
      await loadUserDetail();
    } catch (err) {
      if (ensureAuth(err)) return;
      setActionStatus(err.message || '차단 해제 실패', 'error');
    }
  });
}

if (deleteUserBtn) {
  deleteUserBtn.addEventListener('click', async () => {
    if (!userId) return;
    if (!confirm('해당 사용자를 삭제하시겠습니까?')) return;
    try {
      await fetchJson(`/api/admin/users/${userId}`, { method: 'DELETE' });
      alert('사용자가 삭제되었습니다.');
      window.location.href = 'dashboard.html#users';
    } catch (err) {
      if (ensureAuth(err)) return;
      setActionStatus(err.message || '삭제 실패', 'error');
    }
  });
}

loadUserDetail();
loadUserPosts();

if (backToUsersBtn) {
  backToUsersBtn.addEventListener('click', () => {
    localStorage.setItem('adminSection', 'users');
    window.location.href = 'dashboard.html';
  });
}
