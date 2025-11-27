const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const statCardsEl = document.querySelector('#statCards');
const reviewListEl = document.querySelector('#reviewList');
const postTableEl = document.querySelector('#postTable');
const noticeListEl = document.querySelector('#noticeList');
const postSearchInput = document.querySelector('#postSearch');
const postSearchField = document.querySelector('#postSearchField');
const postStatusFilter = document.querySelector('#postStatusFilter');
const noticeSearchInput = document.querySelector('#noticeSearch');
const noticeSearchField = document.querySelector('#noticeSearchField');
const userTableEl = document.querySelector('#userTable');
const reportTableEl = document.querySelector('#reportTable');
const reportSearchInput = document.querySelector('#reportSearch');
const reportSearchField = document.querySelector('#reportSearchField');
const monitoringCardsEl = document.querySelector('#monitoringCards');
const monitoringMessageEl = document.querySelector('#monitoringMessage');
const s3TableBody = document.querySelector('#s3TableBody');
const rekognitionPlaceholder = document.querySelector('#rekognitionPlaceholder');
const rekognitionResults = document.querySelector('#rekognitionResults');
const refreshMonitoringBtn = document.querySelector('#refreshMonitoring');
const grafanaFrame = document.querySelector('#grafanaFrame');
const grafanaFallback = document.querySelector('#grafanaFallback');
const profileButton = document.querySelector('#profileButton');
const logoutButton = document.querySelector('#logoutButton');
const navButtons = document.querySelectorAll('[data-section-target]');
const sections = document.querySelectorAll('[data-section]');
const moderationSearchInput = document.querySelector('#moderationSearch');
const moderationSearchField = document.querySelector('#moderationSearchField');
const profileModal = document.querySelector('#profileModal');
const profileForm = document.querySelector('#profileForm');
const profileUsernameInput = document.querySelector('#profileUsername');
const profileEmailInput = document.querySelector('#profileEmail');
const profileNicknameInput = document.querySelector('#profileNickname');
const profileFeedbackEl = document.querySelector('#profileFeedback');
const closeProfileModalBtn = document.querySelector('#closeProfileModal');
const profileAvatarEl = document.querySelector('#profileAvatar');
const profileNameEl = document.querySelector('#profileName');
const notificationButton = document.querySelector('#notificationButton');
const notificationCountEl = document.querySelector('#notificationCount');
const notificationPanel = document.querySelector('#notificationPanel');
const notificationListEl = document.querySelector('#notificationList');
const notificationEmptyEl = document.querySelector('#notificationEmpty');
const markAllNotificationsBtn = document.querySelector('#markAllNotifications');
const postActionButtonsClass = 'action-hover';

let currentUser = null;
let moderationItems = [];
let notifications = [];
let notificationPollTimer = null;
let reports = [];
let notices = [];

async function fetchJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });
  if (!response.ok) {
    const text = await response.text();
    const error = new Error(text || '요청이 실패했습니다.');
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) {
    return null;
  }
  // 일부 POST 응답이 빈 본문으로 반환될 수 있으므로 안전하게 처리
  const raw = await response.text();
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

function aggregateNotifications(list = []) {
  const map = new Map();
  list.forEach((item) => {
    const key = item.type === 'REPORT'
      ? `report-${item.id}`
      : `review-${item.targetId || item.id}`;
    const existing = map.get(key);
    if (!existing) {
      map.set(key, {
        ...item,
        ids: item.id,
        detectionLabel: (item.detectionLabel || '').trim()
      });
      return;
    }
    // combine ids
    existing.ids = `${existing.ids},${item.id}`;
    // combine detection labels uniquely
    const labels = [...(existing.detectionLabel || '').split(',').map((v) => v.trim()), ...(item.detectionLabel || '').split(',').map((v) => v.trim())]
      .filter(Boolean);
    existing.detectionLabel = Array.from(new Set(labels)).join(', ');
    existing.createdAt = existing.createdAt && item.createdAt
      ? Math.max(new Date(existing.createdAt).getTime(), new Date(item.createdAt).getTime())
      : existing.createdAt || item.createdAt;
    existing.targetLabel = existing.targetLabel || item.targetLabel;
    existing.summary = existing.summary || item.summary;
    existing.message = existing.message || item.message;
    existing.authorLabel = existing.authorLabel || item.authorLabel;
    existing.mainBoardName = existing.mainBoardName || item.mainBoardName;
    existing.subBoardName = existing.subBoardName || item.subBoardName;
  });
  return Array.from(map.values());
}

function ensureAuth(err) {
  if (err && (err.status === 401 || err.status === 403)) {
    window.location.href = 'login.html';
    return true;
  }
  return false;
}

function openPostDetail(postId) {
  if (!postId) return;
  window.location.href = `post-detail.html?id=${postId}`;
}

function openReportDetail(reportId) {
  if (!reportId) return;
  window.location.href = `report-detail.html?id=${reportId}`;
}

function openUserDetail(userId) {
  if (!userId) return;
  window.location.href = `user-detail.html?id=${userId}`;
}

function showSection(target) {
  navButtons.forEach((btn) => {
    const isActive = btn.dataset.sectionTarget === target;
    btn.classList.toggle('active', isActive);
  });
  localStorage.setItem('adminSection', target);

  sections.forEach((section) => {
    section.classList.toggle('hidden', section.dataset.section !== target);
  });
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ko-KR');
}

async function loadDashboard() {
  const data = await fetchJson('/api/admin/dashboard');
  const cards = [
    { label: '총 사용자', value: data.totalUsers },
    { label: '활성 사용자', value: data.activeUsers },
    { label: '차단 사용자', value: data.blockedUsers },
    { label: '유해 게시물', value: data.harmfulPosts }
  ];
  statCardsEl.innerHTML = cards.map((card) => `
    <div class="stat-card">
      <p class="text-muted mb-1">${card.label}</p>
      <p class="h4 fw-bold mb-0">${card.value.toLocaleString()}</p>
    </div>
  `).join('');
  renderCharts(data);
}

function renderCharts(data) {
  const dailyData = data.dailySignups || [];
  const categoryData = data.categoryDistribution || [];
  const detectionCounts = data.detectionCounts || [];
  const systemMetrics = data.systemMetrics || [];

  const harmfulEntry = detectionCounts.find((item) => item.label === 'Harmful Detected') || { count: 0 };
  const cleanEntry = detectionCounts.find((item) => item.label === 'Clean') || { count: 0 };

  new Chart(document.getElementById('signupChart'), {
    type: 'line',
    data: {
      labels: dailyData.map((item) => item.date),
      datasets: [{
        label: '일별 가입자 수',
        data: dailyData.map((item) => item.count),
        borderColor: '#0d6efd',
        backgroundColor: 'rgba(13, 110, 253, 0.2)',
        fill: true,
        tension: 0.3
      }]
    }
  });

  new Chart(document.getElementById('categoryChart'), {
    type: 'pie',
    data: {
      labels: categoryData.map((item) => item.label),
      datasets: [{
        data: categoryData.map((item) => item.count),
        backgroundColor: ['#0d6efd', '#6610f2', '#20c997', '#ffc107', '#fd7e14', '#2dd4bf']
      }]
    }
  });

  new Chart(document.getElementById('harmfulChart'), {
    type: 'bar',
    data: {
      labels: ['Clean', 'Harmful'],
      datasets: [{
        label: '게시글 수',
        data: [cleanEntry.count, harmfulEntry.count],
        backgroundColor: ['rgba(25, 135, 84, 0.85)', 'rgba(220, 53, 69, 0.85)']
      }]
    }
  });

  new Chart(document.getElementById('systemChart'), {
    type: 'line',
    data: {
      labels: systemMetrics.map((item) => item.timestamp?.substring(11, 16) ?? ''),
      datasets: [
        {
          label: 'CPU (%)',
          data: systemMetrics.map((item) => item.cpuUsagePercent),
          borderColor: '#0d6efd',
          fill: false
        },
        {
          label: '메모리 (MB)',
          data: systemMetrics.map((item) => item.memoryUsageMb),
          borderColor: '#fd7e14',
          fill: false,
          yAxisID: 'y1'
        }
      ]
    },
    options: {
      scales: {
        y: { beginAtZero: true, suggestedMax: 100, ticks: { callback: (value) => `${value}%` } },
        y1: {
          position: 'right',
          beginAtZero: true,
          grid: { drawOnChartArea: false },
          ticks: { callback: (value) => `${value} MB` }
        }
      }
    }
  });
}
async function loadReviewItems() {
    moderationItems = await fetchJson('/api/admin/review-items');
    renderModerationTable(moderationItems);
}

function renderModerationTable(items) {
    if (!items.length) {
        reviewListEl.innerHTML = '<p class="text-muted">대기 중인 항목이 없습니다.</p>';
        return;
    }
  const aggregated = aggregateModerationItems(items);
  const rows = aggregated.map((item) => `
    <tr data-detail-id="${item.detailId || item.id}">
     <td>#${item.detailId || item.id}</td>
     <td class="text-left">${item.title || '(제목 없음)'}</td>
     <td>${item.detectionSummary || item.detectionType || item.contentType || '-'}</td>
      <td>${formatBoardLabel(item.mainBoardName, item.subBoardName)}</td>
      <td>${item.authorName || '알 수 없음'}</td>
      <td>${item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</td>
          <td class="actions">
            <button class="btn btn-outline btn-sm ${postActionButtonsClass}" data-action="approve" data-id="${item.id}">승인</button>
            <button class="btn btn-outline btn-sm ${postActionButtonsClass}" data-action="reject" data-id="${item.id}">차단</button>
          </td>
        </tr>
      `).join('');
    reviewListEl.innerHTML = `
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
            <th>ID</th>
            <th>제목</th>
            <th>감지 타입</th>
            <th>메뉴</th>
            <th>작성자</th>
            <th>감지 시각</th>
          <th>작업</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
      </table>
      </div>`;

    reviewListEl.querySelectorAll('tr[data-detail-id]').forEach((row) => {
        row.addEventListener('click', (event) => {
            if (event.target.closest('button')) return;
            const id = row.getAttribute('data-detail-id');
            if (id) {
                window.location.href = `moderation-detail.html?id=${id}`;
            }
        });
    });

    reviewListEl.querySelectorAll('button[data-action]').forEach((btn) => {
        btn.addEventListener('click', async (event) => {
            event.stopPropagation();
            const id = btn.dataset.id;
            const action = btn.dataset.action;
            const endpoint = action === 'approve'
                ? `/api/admin/review-items/${id}/approve`
                : `/api/admin/review-items/${id}/reject`;
            await fetchJson(endpoint, { method: 'POST' });
            await loadReviewItems();
        });
    });
}

function aggregateModerationItems(items = []) {
  const map = new Map();
  items.forEach((item) => {
    const key = item.commentId ? `comment-${item.commentId}` : item.postId ? `post-${item.postId}` : `item-${item.id}`;
    const existing = map.get(key);
    const primaryType = mapModerationType(item.detectionComponent || item.detectionType || item.contentType);
    const secondaryType = !item.detectionComponent ? mapModerationType(item.contentType || item.detectionType) : null;
    if (!existing) {
      map.set(key, {
        ...item,
        detectionTypes: new Set([primaryType, secondaryType].filter(Boolean)),
        detailId: item.id
      });
      return;
    }
    if (item.createdAt && (!existing.createdAt || new Date(item.createdAt) < new Date(existing.createdAt))) {
      existing.createdAt = item.createdAt;
    }
    if (item.title && (!existing.title || existing.title === '(제목 없음)')) {
      existing.title = item.title;
    }
    // prefer item with media for detail view
    if (!existing.contentUrl && item.contentUrl) {
      existing.contentUrl = item.contentUrl;
      existing.contentType = item.contentType;
      existing.detailId = item.id;
    }
    existing.detectionTypes.add(primaryType);
    // secondaryType only if different to avoid duplicates like 제목+본문 when 단일 컴포넌트
    if (secondaryType && secondaryType !== primaryType) {
      existing.detectionTypes.add(secondaryType);
    }
  });

  return Array.from(map.values()).map((item) => {
    const types = Array.from(item.detectionTypes || []);
    const summary = types.filter(Boolean).join(', ');
    return { ...item, detectionSummary: summary };
  });
}

function mapModerationType(value) {
  if (!value) return '-';
  const normalized = String(value).toUpperCase();
  switch (normalized) {
    case 'TITLE':
      return '제목';
    case 'BODY':
    case 'TEXT':
    case 'CONTENT':
      return '본문';
    case 'IMAGE':
      return '이미지';
    case 'VIDEO':
      return '동영상';
    default:
      return value;
  }
}

function filterModerationTable() {
  if (!moderationSearchInput) return;
  const query = moderationSearchInput.value.trim().toLowerCase();
  const field = moderationSearchField ? moderationSearchField.value : 'all';
  if (!query) {
    renderModerationTable(moderationItems);
    return;
  }
  const filtered = moderationItems.filter((item) => {
    const detection = (item.detectionType || item.contentType || '').toLowerCase();
    const author = (item.authorName || '').toLowerCase();
    const created = item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR').toLowerCase() : '';
    const idMatch = `#${item.id}`.includes(query);
    const menu = `${item.mainBoardName || ''} ${item.subBoardName || ''}`.toLowerCase();

    switch (field) {
      case 'id':
        return idMatch;
      case 'detection':
        return detection.includes(query);
      case 'author':
        return author.includes(query);
      case 'menu':
        return menu.includes(query);
      default:
        return detection.includes(query) || author.includes(query) || created.includes(query) || idMatch || menu.includes(query);
    }
  });
  renderModerationTable(filtered);
}

if (moderationSearchInput) {
  moderationSearchInput.addEventListener('input', filterModerationTable);
}
if (moderationSearchField) {
  moderationSearchField.addEventListener('change', () => {
    filterModerationTable();
    moderationSearchInput.focus();
  });
}

if (postSearchInput) {
  postSearchInput.addEventListener('input', loadPosts);
}
if (postSearchField) {
  postSearchField.addEventListener('change', () => {
    loadPosts();
    postSearchInput.focus();
  });
}

async function loadPosts() {
  const posts = await fetchJson(`/api/admin/posts`);
  if (!posts.length) {
    postTableEl.innerHTML = '<p class="text-muted">게시글이 없습니다.</p>';
    return;
  }
  const query = (postSearchInput ? postSearchInput.value : '').trim().toLowerCase();
  const field = postSearchField ? postSearchField.value : 'all';
  const statusFilter = postStatusFilter ? postStatusFilter.value : '';
  const filtered = posts.filter((post) => {
    const statusMatch = statusFilter ? post.status === statusFilter : true;
    if (!statusMatch) return false;

    if (!query) return true;

    const idMatch = `#${post.id}`.includes(query);
    const title = (post.title || '').toLowerCase();
    const author = (post.author || '').toLowerCase();
    const menu = `${post.mainBoardName || ''} ${post.subBoardName || ''}`.toLowerCase();
    const status = (post.status || '').toLowerCase();
    switch (field) {
      case 'id':
        return idMatch;
      case 'title':
        return title.includes(query);
      case 'author':
        return author.includes(query);
      case 'menu':
        return menu.includes(query);
      case 'status':
        return status.includes(query);
      default:
        return idMatch || title.includes(query) || author.includes(query) || menu.includes(query) || status.includes(query);
    }
  });

  if (!filtered.length) {
    postTableEl.innerHTML = '<p class="text-muted">검색 결과가 없습니다.</p>';
    return;
  }
  const rows = filtered.map((post) => `
    <tr data-post-id="${post.id}">
      <td>#${post.id}</td>
      <td>${post.title || '(제목 없음)'}</td>
      <td>${formatBoardLabel(post.mainBoardName, post.subBoardName, '-')}</td>
     <td>${post.author || '익명'}</td>
     <td>${post.createdAt ? new Date(post.createdAt).toLocaleString('ko-KR') : '-'}</td>
     <td>${post.status}</td>
      <td class="actions">
        <button class="btn btn-outline btn-sm ${postActionButtonsClass}" data-action="approve" data-id="${post.id}">승인</button>
        <button class="btn btn-outline btn-sm ${postActionButtonsClass}" data-action="hide" data-id="${post.id}">차단</button>
      </td>
    </tr>
  `).join('');
  postTableEl.innerHTML = `
    <div class="table-responsive">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>제목</th>
          <th>메뉴</th>
          <th>작성자</th>
          <th>작성 시각</th>
          <th>상태</th>
          <th>작업</th>
        </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
  postTableEl.querySelectorAll('tr[data-post-id]').forEach((row) => {
    row.addEventListener('click', (event) => {
      const id = row.dataset.postId;
      if (!id) return;
      if (event.target.closest('button')) return;
      openPostDetail(id);
    });
  });

  postTableEl.querySelectorAll('button[data-action]').forEach((btn) => {
    btn.addEventListener('click', async (event) => {
      event.stopPropagation();
      const { action, id } = btn.dataset;
      if (action === 'approve') {
        await fetchJson(`/api/admin/posts/${id}/status`, {
          method: 'POST',
          body: JSON.stringify({ status: 'PUBLISHED' })
        });
      } else if (action === 'hide') {
        await fetchJson(`/api/admin/posts/${id}/status`, {
          method: 'POST',
          body: JSON.stringify({ status: 'HIDDEN' })
        });
      }
      await loadPosts();
    });
  });
}

async function loadNotices() {
  notices = await fetchJson('/api/admin/notices');
  renderNotices(notices);
}

function filterNotices(list = []) {
  const keyword = (noticeSearchInput ? noticeSearchInput.value : '').trim().toLowerCase();
  const field = noticeSearchField ? noticeSearchField.value : 'all';
  if (!keyword) return list;
  return list.filter((notice) => {
    const title = (notice.title || '').toLowerCase();
    const content = (notice.content || '').toLowerCase();
    const pinned = notice.pinned ? 'pinned 고정 true yes' : 'unpinned false';
    switch (field) {
      case 'title':
        return title.includes(keyword);
      case 'content':
        return content.includes(keyword);
      case 'pinned':
        return pinned.includes(keyword);
      default:
        return title.includes(keyword) || content.includes(keyword) || pinned.includes(keyword);
    }
  });
}

function renderNotices(list = []) {
  if (!noticeListEl) return;
  if (!list.length) {
    noticeListEl.innerHTML = '<p class="text-muted">공지사항이 없습니다.</p>';
    return;
  }
  const filtered = filterNotices(list);
  if (!filtered.length) {
    noticeListEl.innerHTML = '<p class="text-muted">검색 결과가 없습니다.</p>';
    return;
  }
  const rows = filtered.map((notice) => `
    <tr data-notice-id="${notice.id}">
      <td class="text-left">
        <span class="notice-title-wrap">
          ${notice.pinned ? '<span class="pin-icon" title="상단 고정">📌</span>' : ''}
          <span class="notice-title-text">${notice.title}</span>
          ${notice.attachmentUrls && notice.attachmentUrls.length ? '<span class="clip-icon" aria-label="첨부파일 있음" title="첨부파일 있음">📎</span>' : ''}
        </span>
      </td>
      <td>${notice.author || '관리자'}</td>
      <td>${new Date(notice.createdAt || Date.now()).toLocaleString('ko-KR')}</td>
      <td>
        <button class="btn btn-outline btn-sm" data-action="edit" data-id="${notice.id}">수정</button>
        <button class="btn btn-outline btn-sm" data-action="pin" data-id="${notice.id}" data-pinned="${notice.pinned}">
          ${notice.pinned ? '고정 해제' : '상단 고정'}
        </button>
        <button class="btn btn-outline btn-sm" data-action="delete" data-id="${notice.id}">삭제</button>
      </td>
    </tr>
  `).join('');
  noticeListEl.innerHTML = `
    <div class="table-responsive">
      <table>
        <thead>
          <tr>
            <th>제목</th>
            <th>작성자</th>
            <th>작성일</th>
            <th>작업</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
  noticeListEl.querySelectorAll('button[data-action]').forEach((btn) => {
    btn.addEventListener('click', (event) => {
      event.stopPropagation();
      handleNoticeAction(btn);
    });
  });
  noticeListEl.querySelectorAll('tr[data-notice-id]').forEach((row) => {
    row.addEventListener('click', () => {
      const id = row.dataset.noticeId;
      window.location.href = `notice-edit.html?id=${id}`;
    });
  });
}

async function handleNoticeAction(button) {
  const id = button.dataset.id;
  const action = button.dataset.action;
  if (action === 'edit') {
    window.location.href = `notice-edit.html?id=${id}`;
    return;
  }
  if (action === 'pin') {
    const pinned = button.dataset.pinned === 'true';
    await fetchJson(`/api/admin/notices/${id}/pin?pinned=${!pinned}`, { method: 'POST' });
  } else if (action === 'delete') {
    if (!confirm('삭제하시겠습니까?')) return;
    await fetchJson(`/api/admin/notices/${id}`, { method: 'DELETE' });
  }
  await loadNotices();
}

async function loadUsers() {
  const users = await fetchJson('/api/admin/users');
  if (!users.length) {
    userTableEl.innerHTML = '<p class="text-muted">사용자가 없습니다.</p>';
    return;
  }
  const rows = users.map((user) => `
    <tr data-user-id="${user.id}">
      <td>#${user.id}</td>
      <td>${user.username}</td>
      <td>${user.nickname || '-'}</td>
      <td>${user.status}</td>
      <td>${user.createdAt ? new Date(user.createdAt).toLocaleDateString('ko-KR') : '-'}</td>
      <td>
        <button class="btn btn-outline btn-sm" data-action="block" data-id="${user.id}">차단</button>
        <button class="btn btn-outline btn-sm" data-action="delete" data-id="${user.id}">삭제</button>
      </td>
    </tr>
  `).join('');
  userTableEl.innerHTML = `
    <div class="table-responsive">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>아이디</th>
            <th>닉네임</th>
            <th>상태</th>
            <th>가입일</th>
            <th>작업</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
  userTableEl.querySelectorAll('button[data-action]').forEach((btn) => {
    btn.addEventListener('click', async (event) => {
      event.stopPropagation();
      const id = btn.dataset.id;
      if (btn.dataset.action === 'block') {
        await fetchJson(`/api/admin/users/${id}/block`, { method: 'POST' });
      } else if (btn.dataset.action === 'delete') {
        if (!confirm('사용자를 삭제하시겠습니까?')) return;
        await fetchJson(`/api/admin/users/${id}`, { method: 'DELETE' });
      } else {
        return;
      }
      await loadUsers();
    });
  });
  userTableEl.querySelectorAll('tr[data-user-id]').forEach((row) => {
    row.addEventListener('click', () => {
      const id = row.dataset.userId;
      openUserDetail(id);
    });
  });
}

async function loadReports() {
  if (!reportTableEl) return [];
  try {
    reports = await fetchJson(`/api/admin/reports`);
    renderReports(reports);
  } catch (err) {
    if (ensureAuth(err)) return [];
    reportTableEl.innerHTML = `<p class="text-muted">${err.message || '신고 데이터를 불러오지 못했습니다.'}</p>`;
  }
  return reports;
}

function renderReports(list = []) {
  if (!reportTableEl) return;
  const keyword = (reportSearchInput ? reportSearchInput.value : '').trim().toLowerCase();
  const field = reportSearchField ? reportSearchField.value : 'all';
  const filtered = keyword
    ? list.filter((report) => {
        const author = (report.authorName || '').toLowerCase();
        const reporter = (report.reporterName || '').toLowerCase();
        const reason = (report.reason || '').toLowerCase();
        const type = (report.type || '').toLowerCase();
        const status = (report.status || '').toLowerCase();
        const menu = `${report.mainBoardName || ''} ${report.subBoardName || ''}`.toLowerCase();
        const idMatch = `#${report.id}`.includes(keyword);
        switch (field) {
          case 'id':
            return idMatch;
          case 'type':
            return type.includes(keyword);
          case 'status':
            return status.includes(keyword);
          case 'menu':
            return menu.includes(keyword);
          case 'author':
            return author.includes(keyword);
          case 'reporter':
            return reporter.includes(keyword);
          case 'reason':
            return reason.includes(keyword);
          default:
            return idMatch || type.includes(keyword) || status.includes(keyword) || menu.includes(keyword) || author.includes(keyword) || reporter.includes(keyword) || reason.includes(keyword);
        }
      })
    : list;
  const data = filtered;
  if (!list.length) {
    reportTableEl.innerHTML = '<p class="text-muted">등록된 신고가 없습니다.</p>';
    return;
  }
  if (!data.length) {
    reportTableEl.innerHTML = '<p class="text-muted">검색 결과가 없습니다.</p>';
    return;
  }
  const rows = data.map((report) => `
    <tr data-report-id="${report.id}">
      <td>#${report.id}</td>
      <td>${translateReportType(report.type)}</td>
      <td>${translateReportStatus(report.status)}</td>
      <td>${formatBoardLabel(report.mainBoardName, report.subBoardName, '-')}</td>
      <td>${report.authorName || '작성자 미확인'}</td>
      <td>${report.reporterName || '신고자 미확인'}</td>
      <td>${report.reason || '-'}</td>
      <td>${formatDateTime(report.createdAt)}</td>
    </tr>
  `).join('');
  reportTableEl.innerHTML = `
    <div class="table-responsive">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>유형</th>
            <th>상태</th>
            <th>메뉴</th>
            <th>작성자</th>
            <th>신고자</th>
            <th>사유</th>
            <th>신고 시각</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
  `;
  reportTableEl.querySelectorAll('tr[data-report-id]').forEach((row) => {
    row.addEventListener('click', (event) => {
      openReportDetail(row.dataset.reportId);
    });
  });
}

if (reportSearchInput) {
  reportSearchInput.addEventListener('input', () => renderReports(reports));
}
if (reportSearchField) {
  reportSearchField.addEventListener('change', () => {
    renderReports(reports);
    reportSearchInput && reportSearchInput.focus();
  });
}
if (noticeSearchInput) {
  noticeSearchInput.addEventListener('input', () => renderNotices(notices));
}
if (noticeSearchField) {
  noticeSearchField.addEventListener('change', () => {
    renderNotices(notices);
    noticeSearchInput && noticeSearchInput.focus();
  });
}

async function loadMonitoring() {
  try {
    const [metrics, s3Objects] = await Promise.all([
      fetchJson('/api/admin/monitoring/metrics'),
      fetchJson('/api/admin/monitoring/s3')
    ]);
    renderMonitoringCards(metrics);
    renderS3Objects(s3Objects || []);
  } catch (err) {
    if (ensureAuth(err)) return;
    if (monitoringMessageEl) {
      monitoringMessageEl.hidden = false;
      monitoringMessageEl.textContent = err.message || '모니터링 데이터를 불러오지 못했습니다.';
    }
  }
}

function renderMonitoringCards(metrics = {}) {
  const cards = [
    { label: 'CPU 사용률', value: `${((metrics.cpuUsage ?? 0) * 100).toFixed(2)}%` },
    { label: '메모리 사용량', value: `${((metrics.memoryUsageBytes ?? 0) / (1024 * 1024)).toFixed(1)} MB` },
    { label: '요청 수', value: `${Math.round(metrics.requestCount ?? 0).toLocaleString()} 건` },
    { label: '에러율', value: `${((metrics.errorRate ?? 0) * 100).toFixed(2)}%` },
    { label: '오늘 감지된 유해 게시물', value: `${metrics.harmfulPostsToday ?? 0} 건` },
    { label: 'EC2 상태', value: metrics.ec2Status || 'UNKNOWN' },
    { label: 'RDS 상태', value: metrics.rdsStatus || 'UNKNOWN' }
  ];
  monitoringCardsEl.innerHTML = cards.map((card) => `
    <div class="stat-card">
      <p class="text-muted mb-1">${card.label}</p>
      <p class="h5 fw-bold mb-0">${card.value}</p>
    </div>
  `).join('');
}

function renderS3Objects(objects) {
  const sorted = [...objects].sort((a, b) => {
    const aTime = a && a.lastModified ? new Date(a.lastModified).getTime() : 0;
    const bTime = b && b.lastModified ? new Date(b.lastModified).getTime() : 0;
    return bTime - aTime;
  });
  if (!sorted.length) {
    s3TableBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">표시할 파일이 없습니다.</td></tr>';
    return;
  }
  s3TableBody.innerHTML = sorted.map((item) => `
    <tr>
      <td>${item.key || '-'}</td>
      <td>${(item.size / 1024).toFixed(2)} KB</td>
      <td>${item.lastModified ? new Date(item.lastModified).toLocaleString('ko-KR') : '-'}</td>
      <td>
        <button class="btn btn-outline btn-sm" data-object-key="${encodeURIComponent(item.key || '')}">분석</button>
      </td>
    </tr>
  `).join('');
  s3TableBody.querySelectorAll('button[data-object-key]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const key = decodeURIComponent(btn.dataset.objectKey || '');
      handleRekognition(key);
    });
  });
}

async function handleRekognition(objectKey) {
  rekognitionPlaceholder.textContent = '분석 결과를 불러오는 중입니다...';
  rekognitionResults.innerHTML = '';
  try {
    const labels = await fetchJson(`/api/admin/monitoring/rekognition?objectKey=${encodeURIComponent(objectKey)}`);
    if (!labels.length) {
      rekognitionPlaceholder.textContent = '유해 요소가 감지되지 않았습니다.';
      return;
    }
    rekognitionPlaceholder.textContent = '';
    rekognitionResults.innerHTML = labels.map((label) => `
      <li class="d-flex justify-content-between">
        <span>${label.label}</span>
        <span class="badge bg-danger">${(label.confidence ?? 0).toFixed(2)}%</span>
      </li>
    `).join('');
  } catch (err) {
    if (ensureAuth(err)) return;
    rekognitionPlaceholder.textContent = err.message || '결과를 가져오지 못했습니다.';
  }
}

const createNoticeBtn = document.getElementById('createNotice');
if (createNoticeBtn) {
  createNoticeBtn.addEventListener('click', () => {
    window.location.href = 'notice-edit.html';
  });
}
if (postStatusFilter) {
  postStatusFilter.addEventListener('change', loadPosts);
}
document.getElementById('refreshReview').addEventListener('click', loadReviewItems);
if (refreshMonitoringBtn) {
  refreshMonitoringBtn.addEventListener('click', loadMonitoring);
}

if (logoutButton) {
  logoutButton.addEventListener('click', async () => {
    await fetchJson('/api/auth/logout', { method: 'POST', body: '{}' });
    window.location.href = 'login.html';
  });
}

navButtons.forEach((button) => {
  button.addEventListener('click', () => showSection(button.dataset.sectionTarget));
});

async function loadAdminData() {
  await Promise.all([
    loadDashboard(),
    loadReviewItems(),
    loadPosts(),
    loadNotices(),
    loadUsers(),
    loadMonitoring(),
    loadReports()
  ]);
}

function buildGrafanaProxySrc(pathValue) {
  if (!pathValue) return null;
  const trimmed = pathValue.trim();
  if (!trimmed) return null;
  const questionIndex = trimmed.indexOf('?');
  const pathOnly = questionIndex >= 0 ? trimmed.substring(0, questionIndex) : trimmed;
  const queryString = questionIndex >= 0 ? trimmed.substring(questionIndex + 1) : '';
  const normalizedPath = pathOnly.startsWith('/') ? pathOnly : `/${pathOnly}`;
  let url = `${API_BASE}/api/admin/monitoring/grafana/proxy${normalizedPath}`;
  if (queryString) {
    url += `?${queryString}`;
  }
  return url;
}

function initGrafanaFrame() {
  if (!grafanaFrame) return;
  const grafanaPath = window.__GRAFANA_PATH__ || '';

  // Always use backend proxy so we don't rely on direct VPC access from the browser.
  const iframeSrc = buildGrafanaProxySrc(grafanaPath);

  if (!iframeSrc) {
    grafanaFrame.classList.add('hidden');
    if (grafanaFallback) {
      grafanaFallback.hidden = false;
      grafanaFallback.textContent = 'Grafana 경로가 설정되지 않았습니다. config.js를 확인하세요.';
    }
    return;
  }

  grafanaFrame.loading = 'lazy';
  grafanaFrame.referrerPolicy = 'no-referrer';
  grafanaFrame.src = iframeSrc;
  grafanaFrame.classList.remove('hidden');
  if (grafanaFallback) {
    grafanaFallback.hidden = true;
  }
}

function updateProfileUI() {
  if (!currentUser) return;
  const displayName = currentUser.nickname || currentUser.username || '관리자';
  if (profileNameEl) {
    profileNameEl.textContent = displayName;
  }
  if (profileAvatarEl) {
    profileAvatarEl.textContent = displayName.substring(0, 1).toUpperCase();
  }
}

function openProfileModal() {
  if (!currentUser || !profileModal) return;
  profileModal.classList.remove('hidden');
  profileUsernameInput.value = currentUser.username || '';
  profileEmailInput.value = currentUser.email || '';
  profileNicknameInput.value = currentUser.nickname || currentUser.username || '';
  setProfileStatus('');
}

function closeProfileModal() {
  if (profileModal) {
    profileModal.classList.add('hidden');
  }
  setProfileStatus('');
}

function setProfileStatus(message, status = '') {
  if (!profileFeedbackEl) return;
  profileFeedbackEl.textContent = message || '';
  profileFeedbackEl.dataset.status = status;
}

async function saveProfile(event) {
  event.preventDefault();
  const nickname = profileNicknameInput.value.trim();
  if (!nickname) {
    setProfileStatus('표시 이름을 입력하세요.', 'error');
    return;
  }
  const formData = new FormData();
  formData.append('nickname', nickname);
  try {
    const response = await fetch(`${API_BASE}/api/auth/profile`, {
      method: 'PUT',
      credentials: 'include',
      body: formData
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || '프로필을 업데이트하지 못했습니다.');
    }
    setProfileStatus('프로필이 저장되었습니다.', 'success');
    currentUser.nickname = nickname;
    updateProfileUI();
    setTimeout(closeProfileModal, 800);
  } catch (err) {
    if (ensureAuth(err)) return;
    setProfileStatus(err.message || '프로필 저장에 실패했습니다.', 'error');
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

function renderNotificationBadge(count) {
  if (!notificationCountEl) return;
  if (!count) {
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

function highlightReportRow(reportId) {
  if (!reportTableEl || !reportId) return;
  const row = reportTableEl.querySelector(`[data-report-id="${reportId}"]`);
  if (row) {
    row.classList.add('table-highlight');
    row.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setTimeout(() => row.classList.remove('table-highlight'), 2000);
  }
}

function navigateNotification(type, targetId) {
  if (type === 'REPORT') {
    showSection('reports');
    if (reportStatusFilter) {
      reportStatusFilter.value = '';
    }
    loadReports().then(() => highlightReportRow(targetId));
    return;
  }
  if (targetId) {
    window.location.href = `moderation-detail.html?id=${targetId}`;
  } else {
    showSection('moderation');
  }
}

function renderNotificationList() {
  if (!notificationPanel) return;
  const aggregated = aggregateNotifications(notifications || []);
  if (!aggregated.length) {
    notificationEmptyEl.classList.remove('hidden');
    notificationListEl.innerHTML = '';
    return;
  }
  notificationEmptyEl.classList.add('hidden');
  notificationListEl.innerHTML = aggregated.map((item) => {
    const meta = notificationTypeMeta(item);
    const boardText = formatBoardLabel(item.mainBoardName, item.subBoardName, item.boardLabel);
    const detectionLabels = (item.detectionLabel || meta.detectionLabel || '')
      .split(',')
      .map((v) => mapModerationType(v.trim()))
      .filter(Boolean);
    const typeChip = mapModerationType(item.detectionComponent || item.detectionType || item.contentType);
    const detectionBadge = detectionLabels.length
      ? detectionLabels.map((label) => `<span class="badge badge-info">${label}</span>`).join('')
      : typeChip
        ? `<span class="badge badge-info">${typeChip}</span>`
        : '';
    return `
      <li tabindex="0" data-type="${item.type}" data-target-id="${item.targetId || ''}" data-notification-id="${item.ids || item.id}">
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
      if (event.target.closest('button')) {
        return;
      }
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
      fetchJson('/admin/api/notifications?limit=50'),
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
    const ids = String(id || '')
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean);
    if (!ids.length) return;
    await Promise.all(ids.map((nid) => fetchJson(`/admin/api/notifications/${nid}/read`, { method: 'POST' })));
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

function startNotificationPolling() {
  loadNotifications();
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer);
  }
  notificationPollTimer = setInterval(loadNotifications, 15000);
}

if (profileButton) {
  profileButton.addEventListener('click', openProfileModal);
}
if (closeProfileModalBtn) {
  closeProfileModalBtn.addEventListener('click', closeProfileModal);
}
if (profileForm) {
  profileForm.addEventListener('submit', saveProfile);
}
if (notificationButton) {
  notificationButton.addEventListener('click', toggleNotificationPanel);
}
if (markAllNotificationsBtn) {
  markAllNotificationsBtn.addEventListener('click', markAllNotifications);
}
document.addEventListener('click', (event) => {
  if (!notificationPanel || notificationPanel.classList.contains('hidden')) return;
  if (notificationPanel.contains(event.target) || notificationButton.contains(event.target)) {
    return;
  }
  notificationPanel.classList.add('hidden');
});

async function init() {
  try {
    currentUser = await fetchJson('/api/auth/me');
    if (!currentUser || currentUser.role !== 'ADMIN') {
      window.location.href = 'login.html';
      return;
    }
    updateProfileUI();
    initGrafanaFrame();
    const hashSection = window.location.hash ? window.location.hash.replace('#', '') : '';
    const savedSection = hashSection || localStorage.getItem('adminSection') || 'stats';
    if (hashSection) {
      localStorage.setItem('adminSection', hashSection);
    }
    showSection(savedSection);
    await loadAdminData();
    startNotificationPolling();
    showSection(savedSection);
  } catch (err) {
    if (ensureAuth(err)) return;
    console.error(err);
  }
}

init();
function translateReportStatus(status) {
  switch (status) {
    case 'PENDING':
      return '대기';
    case 'ACTION_TAKEN':
      return '조치 완료';
    case 'REJECTED':
      return '기각';
    default:
      return status || '-';
  }
}

function translateReportType(type) {
  if (type === 'COMMENT') return '댓글';
  if (type === 'POST') return '게시글';
  return type || '-';
}
