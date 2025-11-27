(function () {
  const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';

  const notificationButton = document.querySelector('#notificationButton');
  const notificationCountEl = document.querySelector('#notificationCount');
  const notificationPanel = document.querySelector('#notificationPanel');
  const notificationListEl = document.querySelector('#notificationList');
  const notificationEmptyEl = document.querySelector('#notificationEmpty');
  const markAllNotificationsBtn = document.querySelector('#markAllNotifications');
  const profileButton = document.querySelector('#profileButton');
  const profileAvatarEl = document.querySelector('#profileAvatar');
  const profileNameEl = document.querySelector('#profileName');
  const logoutButton = document.querySelector('#logoutButton');
  const profileDropdown = document.querySelector('#profileDropdown');

  let notifications = [];
  let notificationPollTimer = null;

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
    return response.json();
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

  function renderNotificationBadge(count) {
    if (!notificationCountEl) return;
    if (!count || count < 1) {
      notificationCountEl.classList.add('hidden');
      notificationCountEl.textContent = '0';
      return;
    }
    notificationCountEl.classList.remove('hidden');
    notificationCountEl.textContent = count > 99 ? '99+' : String(count);
  }

  function notificationTypeMeta(item) {
    if (item.type === 'REPORT') {
      return { label: '신고', badgeClass: 'badge badge-warning', detectionLabel: null };
    }
    return {
      label: '유해 감지',
      badgeClass: 'badge badge-danger',
      detectionLabel: item.detectionLabel || null
    };
  }

  function formatBoardLabel(main, sub, fallback = '게시판 미확인') {
    if (main && sub) return `${main} / ${sub}`;
    if (main) return main;
    return sub || fallback;
  }

  function renderNotificationList() {
    if (!notificationPanel || !notificationListEl) return;
    if (!notifications.length) {
      notificationEmptyEl && notificationEmptyEl.classList.remove('hidden');
      notificationListEl.innerHTML = '';
      return;
    }
    notificationEmptyEl && notificationEmptyEl.classList.add('hidden');
    notificationListEl.innerHTML = notifications.map((item) => {
      const meta = notificationTypeMeta(item);
      const boardText = formatBoardLabel(item.mainBoardName, item.subBoardName, item.boardLabel);
      const detectionLabels = (meta.detectionLabel || '')
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean);
      const detectionBadge = detectionLabels.length
        ? detectionLabels.map((label) => `<span class="badge badge-info">${label}</span>`).join('')
        : '';
      const readClass = item.read ? 'notification-read' : '';
      return `
        <li tabindex="0" class="${readClass}" data-type="${item.type}" data-target-id="${item.targetId || ''}" data-notification-id="${item.id}">
          <div class="notification-tags">
            <span class="${meta.badgeClass}">${meta.label}</span>
            ${detectionBadge}
            <span>${boardText}</span>
          </div>
          <strong>${item.targetLabel || item.summary || item.message || '알림'}</strong>
          <p>${item.summary || item.message || '-'}</p>
          <div class="notification-detail">
            <span class="detail-label">상세정보</span>
            <div class="notification-meta">
              <span>작성자: ${item.authorLabel || '알 수 없음'}</span>
              ${item.reporterLabel ? `<span>신고자: ${item.reporterLabel}</span>` : ''}
              <span>${formatDateTime(item.createdAt)}</span>
              ${item.read ? '' : `<button class="btn btn-outline btn-sm" data-notification-id="${item.id}">읽음</button>`}
            </div>
          </div>
        </li>
      `;
    }).join('');
    notificationListEl.querySelectorAll('li[data-type]').forEach((li) => {
      li.addEventListener('click', async (event) => {
        if (event.target.closest('button')) return;
        const notificationId = li.dataset.notificationId;
        if (notificationId) {
          await markNotificationRead(notificationId);
        }
        navigateNotification(li.dataset.type, li.dataset.targetId);
      });
    });
    notificationListEl.querySelectorAll('button[data-notification-id]').forEach((btn) => {
      btn.addEventListener('click', async (event) => {
        event.stopPropagation();
        await markNotificationRead(btn.dataset.notificationId);
      });
    });
  }

  async function loadNotifications() {
    try {
      const [list, countResp] = await Promise.all([
        fetchJson('/admin/api/notifications?limit=10'),
        fetchJson('/admin/api/notifications/unread-count')
      ]);
      notifications = list || [];
      renderNotificationBadge(countResp.count);
      if (notificationPanel && !notificationPanel.classList.contains('hidden')) {
        renderNotificationList();
      }
    } catch (err) {
      if (ensureAuth(err)) return;
      console.warn('알림을 불러오지 못했습니다.', err);
    }
  }

  async function markNotificationRead(id) {
    try {
      await fetchJson(`/admin/api/notifications/${id}/read`, { method: 'POST' });
      await loadNotifications();
    } catch (err) {
      if (ensureAuth(err)) return;
      console.warn('알림 읽음 처리 실패', err);
    }
  }

  async function markAllNotifications() {
    try {
      await fetchJson('/admin/api/notifications/read-all', { method: 'POST' });
      await loadNotifications();
    } catch (err) {
      if (ensureAuth(err)) return;
      console.warn('알림 전체 읽음 실패', err);
    }
  }

  function toggleNotificationPanel() {
    if (!notificationPanel) return;
    const isHidden = notificationPanel.classList.contains('hidden');
    if (isHidden) {
      notificationPanel.classList.remove('hidden');
      renderNotificationList();
    } else {
      notificationPanel.classList.add('hidden');
    }
  }

  function navigateNotification(type, targetId) {
    if (type === 'REPORT') {
      window.location.href = 'dashboard.html#reports';
      return;
    }
    if (targetId) {
      window.location.href = `moderation-detail.html?id=${targetId}`;
    } else {
      window.location.href = 'dashboard.html#moderation';
    }
  }

  async function loadProfile() {
    try {
      const profile = await fetchJson('/api/auth/profile', { method: 'GET' });
      if (profileAvatarEl) {
        profileAvatarEl.textContent = (profile.nickname || profile.username || 'A').charAt(0).toUpperCase();
      }
      if (profileNameEl) {
        profileNameEl.textContent = profile.nickname || profile.username || '관리자';
      }
      if (profileDropdown) {
        profileDropdown.classList.add('hidden');
      }
    } catch (err) {
      if (ensureAuth(err)) return;
    }
  }

  function initHeader() {
    if (logoutButton) {
      logoutButton.addEventListener('click', async () => {
        await fetchJson('/api/auth/logout', { method: 'POST', body: '{}' });
        window.location.href = 'login.html';
      });
    }
    if (notificationButton) {
      notificationButton.addEventListener('click', toggleNotificationPanel);
    }
    if (markAllNotificationsBtn) {
      markAllNotificationsBtn.addEventListener('click', markAllNotifications);
    }
    document.addEventListener('click', (event) => {
      if (!notificationPanel || notificationPanel.classList.contains('hidden')) return;
      if (notificationPanel.contains(event.target) || (notificationButton && notificationButton.contains(event.target))) {
        return;
      }
      notificationPanel.classList.add('hidden');
    });
    loadProfile();
    loadNotifications();
    if (notificationPollTimer) {
      clearInterval(notificationPollTimer);
    }
    notificationPollTimer = setInterval(loadNotifications, 15000);
  }

  // Only run when header exists on the page.
  if (document.querySelector('.admin-header')) {
    initHeader();
  }
})();
