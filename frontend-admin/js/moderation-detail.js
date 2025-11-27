const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const detailIdEl = document.getElementById('detailId');
const detailDetectionEl = document.getElementById('detailDetection');
const detailStatusEl = document.getElementById('detailStatus');
const detailCreatedEl = document.getElementById('detailCreated');
const detailMainBoardEl = document.getElementById('detailMainBoard');
const detectionTextEl = document.getElementById('detectionText');
const contentPreviewEl = document.getElementById('contentPreview');
const flaggedMediaGroup = document.getElementById('flaggedMediaGroup');
const flaggedMediaList = document.getElementById('flaggedMediaList');
const postInfoEl = document.getElementById('postInfo');
const commentInfoEl = document.getElementById('commentInfo');
const moderationJsonEl = document.getElementById('moderationJson');
const approveBtn = document.getElementById('approveBtn');
const rejectBtn = document.getElementById('rejectBtn');
const backToModerationBtn = document.getElementById('backToModeration');
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

let notifications = [];
let notificationPollTimer = null;
let currentUser = null;

if (backToModerationBtn) {
  backToModerationBtn.addEventListener('click', (event) => {
    event.preventDefault();
    window.location.href = 'dashboard.html';
    localStorage.setItem('adminSection', 'moderation');
  });
}

const params = new URLSearchParams(window.location.search);
const reviewId = params.get('id');

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
  if (response.status === 204) return null;
  // 일부 POST 응답이 빈 본문을 반환할 수 있으므로 안전하게 처리
  const raw = await response.text();
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (e) {
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

function renderInfo(container, info) {
  if (!info) {
    container.innerHTML = '<p class="text-muted">데이터가 없습니다.</p>';
    return;
  }
  container.classList.remove('text-muted');
  const author = info.authorName || '알 수 없음';
  const authorLink = info.authorId ? `<a class="link" href="user-detail.html?id=${info.authorId}" aria-label="작성자 상세로 이동">${author}</a>` : author;
  container.innerHTML = `
    <div>
      <span>ID</span>
      <p>#${info.id}</p>
    </div>
    ${info.title ? `<div><span>제목</span><p>${info.title}</p></div>` : ''}
    ${info.mainBoardName ? `<div><span>메뉴</span><p>${formatBoard({ post: info })}</p></div>` : ''}
    <div>
      <span>작성자</span>
      <p>${authorLink}</p>
    </div>
    <div>
      <span>작성 시각</span>
      <p>${formatDateTime(info.createdAt)}</p>
    </div>
    <div class="span-2">
      <span>내용</span>
      <p>${info.content ? info.content.replace(/\n/g, '<br/>') : '-'}</p>
    </div>
  `;
}

function renderPreview(detail) {
  contentPreviewEl.innerHTML = '';
  contentPreviewEl.classList.remove('alert-text');
  if (detail.inappropriateDetected) {
    contentPreviewEl.classList.add('alert-text');
  }
  if (detail.contentType === 'IMAGE' && detail.contentUrl) {
    const img = document.createElement('img');
    img.src = detail.contentUrl;
    img.alt = 'Moderation Image';
    contentPreviewEl.appendChild(img);
  } else if (detail.contentType === 'VIDEO' && detail.contentUrl) {
    const video = document.createElement('video');
    video.src = detail.contentUrl;
    video.controls = true;
    contentPreviewEl.appendChild(video);
  } else {
    const pre = document.createElement('pre');
    pre.textContent = detail.moderatedText || '콘텐츠 정보를 불러올 수 없습니다.';
    contentPreviewEl.appendChild(pre);
  }
}

function renderDetection(detail) {
  const labels = Array.isArray(detail.detectionLabels) && detail.detectionLabels.length
    ? detail.detectionLabels.map((l) => mapModerationType(l))
    : [mapModerationType(detail.contentType || detail.detectionType || '-')];
  const detector =
    detail.contentType === 'IMAGE' || detail.contentType === 'VIDEO'
      ? 'Rekognition'
      : 'Comprehend';
  const typePills = labels.filter(Boolean).map((l) => `<span class="pill pill-danger">${l}</span>`).join(' ');
  detailDetectionEl.innerHTML = typePills || '-';
  // show detection types on JSON header
  const jsonHeader = document.querySelector('#jsonHeaderLabel');
  if (jsonHeader) {
    jsonHeader.textContent = `AI 검출 결과 (${labels.join(', ') || detector})`;
  }
}

function renderStatus(status) {
  const normalized = (status || '').toUpperCase();
  let label = status || '-';
  let klass = '';
  if (normalized === 'PENDING') {
    klass = 'pill-info';
    label = '대기';
  } else if (normalized === 'APPROVED') {
    klass = 'pill-info';
    label = '승인';
  } else if (normalized === 'REJECTED') {
    klass = 'pill-danger';
    label = '차단';
  }
  detailStatusEl.innerHTML = klass ? `<span class="pill ${klass}">${label}</span>` : label;
}

function renderDetectionText(detail) {
  if (!detectionTextEl) return;
  const parts = [];
  (detail.titleTexts || []).forEach((txt) => {
    parts.push(`<div><strong>제목</strong><br><mark class="detected-mark">${txt}</mark></div>`);
  });
  (detail.bodyTexts || []).forEach((txt) => {
    parts.push(`<div><strong>본문</strong><br><mark class="detected-mark">${txt}</mark></div>`);
  });
  const raw = detail.moderatedText || '';
  const cleaned = raw.replace(/^\[[^\]]+\]\s*/, '');
  if (cleaned && !parts.length) {
    parts.push(`<div><mark class="detected-mark">${cleaned}</mark></div>`);
  }
  detectionTextEl.innerHTML = parts.length ? parts.join('') : '표시할 감지 텍스트가 없습니다.';
}

function formatBoard(detail) {
  const main = detail.post && detail.post.mainBoardName ? detail.post.mainBoardName : null;
  const sub = detail.post && detail.post.subBoardName ? detail.post.subBoardName : null;
  if (main && sub) return `${main} / ${sub}`;
  if (main) return main;
  if (sub) return sub;
  return '-';
}

function renderPreview(detail) {
  if (!contentPreviewEl) return;
  contentPreviewEl.innerHTML = '';
  const mediaUrl = detail.mediaUrl || detail.contentUrl;
  const fallbackUrls = detail.post && Array.isArray(detail.post.fileUrls) ? detail.post.fileUrls : [];
  const tryUrl = mediaUrl || fallbackUrls[0];
  const detected = detectMediaType(tryUrl);
  const mediaType = (detected || detail.mediaType || detail.contentType || '').toUpperCase();
  const urlType = detected || mediaType;

  if (tryUrl && urlType === 'IMAGE') {
    const img = document.createElement('img');
    img.src = tryUrl;
    img.alt = '감지된 이미지';
    img.loading = 'lazy';
    contentPreviewEl.appendChild(img);
    return;
  }
  if (tryUrl && urlType === 'VIDEO') {
    const video = document.createElement('video');
    video.src = tryUrl;
    video.controls = true;
    video.style.maxHeight = '320px';
    contentPreviewEl.appendChild(video);
    return;
  }
  const msg = document.createElement('p');
  msg.className = 'text-muted';
  msg.textContent = '미디어가 없습니다.';
  contentPreviewEl.appendChild(msg);
}

function detectMediaType(url) {
  if (!url) return '';
  const lower = url.split('?')[0].toLowerCase();
  if (/\.(mp4|mov|webm|m4v|avi|mkv)$/i.test(lower)) return 'VIDEO';
  if (/\.(jpe?g|png|gif|webp|avif|bmp|svg)$/i.test(lower)) return 'IMAGE';
  return '';
}

function extractTopLabels(rawResult) {
  if (!rawResult) return [];
  const candidates = [];

  const collect = (node) => {
    if (Array.isArray(node)) {
      node.forEach(collect);
      return;
    }
    if (node && typeof node === 'object') {
      const name = node.Name || node.Label || node.Category || node.Type || node.ModerationLabel || node.LabelName;
      const score = node.Confidence ?? node.Score ?? node.Severity;
      if (name) {
        const numeric = Number(score);
        candidates.push({ name: String(name), score: Number.isFinite(numeric) ? numeric : null });
      }
      Object.values(node).forEach(collect);
    }
  };

  try {
    const parsed = JSON.parse(rawResult);
    collect(parsed);
  } catch {
    // try simple line-based parse: "Label: 92.3"
    rawResult.split(/\r?\n/).forEach((line) => {
      const m = line.match(/([A-Za-z가-힣0-9 _-]+)\s*[:=]\s*([0-9]{1,3}(?:\.[0-9]+)?)/);
      if (m) {
        candidates.push({ name: m[1].trim(), score: Number(m[2]) });
      }
    });
  }

  if (!candidates.length) return [];
  candidates.sort((a, b) => (b.score || 0) - (a.score || 0));
  return candidates.slice(0, 3).map((c) => c.score != null ? `${c.name} (${c.score.toFixed(1)}%)` : c.name);
}

function renderFlaggedMedia(detail) {
  if (!flaggedMediaGroup || !flaggedMediaList) return;
  const flagged = detail.flaggedMediaUrls || [];
  if (!flagged.length) {
    flaggedMediaGroup.classList.add('hidden');
    flaggedMediaList.innerHTML = '';
    return;
  }
  flaggedMediaGroup.classList.remove('hidden');
  flaggedMediaList.innerHTML = flagged
    .map((url) => {
      const name = url.split('/').pop();
      const type = detectMediaType(url);
      const thumb = type === 'IMAGE'
        ? `<img src="${url}" alt="${name}" class="file-thumb" />`
        : '<div class="file-thumb file-thumb-fallback">📎</div>';
      return `
        <div class="file-item">
          <div class="file-thumb-wrap">${thumb}</div>
          <div class="file-meta">
            <a class="link" href="${url}" target="_blank" rel="noopener">${name}</a>
          </div>
        </div>`;
    })
    .join('');
}

async function loadDetail() {
  if (!reviewId) {
    if (contentPreviewEl) contentPreviewEl.textContent = 'ID가 지정되지 않았습니다.';
    return;
  }
  try {
    const detail = await fetchJson(`/api/admin/review-items/${reviewId}`);
    const related = await fetchJson('/api/admin/review-items') || [];
    const aggregated = aggregateDetail(detail, related);

    detailIdEl.textContent = aggregated.id;
    renderDetection(aggregated);
    renderStatus(aggregated.reviewStatus);
    detailCreatedEl.textContent = formatDateTime(aggregated.createdAt);
    detailMainBoardEl.textContent = formatBoard(aggregated);
    renderInfo(postInfoEl, aggregated.post);
    renderInfo(commentInfoEl, aggregated.comment);
    renderDetectionText(aggregated);
    renderPreview(aggregated);
    renderFlaggedMedia(aggregated);
    moderationJsonEl.textContent = aggregated.moderationResult || '결과가 없습니다.';

    approveBtn.addEventListener('click', () => performAction('approve'));
    rejectBtn.addEventListener('click', () => performAction('reject'));
  } catch (err) {
    if (ensureAuth(err)) return;
    if (contentPreviewEl) contentPreviewEl.textContent = err.message || '상세 정보를 불러오지 못했습니다.';
  }
}

async function performAction(action) {
  const endpoint = action === 'approve'
    ? `/api/admin/review-items/${reviewId}/approve`
    : `/api/admin/review-items/${reviewId}/reject`;
  try {
    await fetchJson(endpoint, { method: 'POST' });
    alert('처리가 완료되었습니다.');
    window.location.href = 'dashboard.html';
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '처리에 실패했습니다.');
  }
}

function aggregateDetail(detail, related = []) {
  const combined = { ...detail };
  const same = related.filter((item) => {
    if (!item) return false;
    if (detail.post && item.postId && detail.post.id === item.postId) return true;
    if (detail.comment && item.commentId && detail.comment.id === item.commentId) return true;
    return item.id === detail.id;
  });
  const all = [detail, ...same];
  const labelSet = new Set();
  const typeSet = new Set();
  const rawLabelsSet = new Set();
  let mediaUrl = detail.contentUrl;
  let mediaType = detail.contentType;
  const titleTexts = [];
  const bodyTexts = [];

  all.forEach((item) => {
    const mappedType = mapModerationType(item.detectionComponent || item.contentType || item.detectionType || '-');
    labelSet.add(mappedType);
    typeSet.add(mappedType);
    (item.detectionLabels || []).forEach((l) => rawLabelsSet.add(l));
    const ctype = (item.contentType || '').toUpperCase();
    if (!mediaUrl && (ctype === 'IMAGE' || ctype === 'VIDEO') && item.contentUrl) {
      mediaUrl = item.contentUrl;
      mediaType = ctype;
    }
    const componentMatch = (item.moderatedText || '').match(/^\[([^\]]+)\]/);
    const comp = componentMatch ? componentMatch[1].toUpperCase() : ctype;
    const cleaned = (item.moderatedText || item.excerpt || '').replace(/^\[[^\]]+\]\s*/, '');
    if (cleaned) {
      if (comp === 'TITLE') {
        titleTexts.push(cleaned);
      } else if (comp === 'BODY' || comp === 'TEXT' || comp === 'CONTENT') {
        bodyTexts.push(cleaned);
      }
    }
  });

  const labelsArr = Array.from(labelSet);
  combined.detectionLabels = labelsArr;
  combined.detectionSummary = Array.from(typeSet).join(', ');
  combined.rawLabels = Array.from(rawLabelsSet);
  combined.mediaUrl = mediaUrl;
  combined.mediaType = mediaType;
  // Fallback: if 제목/본문 감지 라벨이 있는데 해당 텍스트가 비어 있으면 게시글 제목/내용을 사용
  if (!titleTexts.length && labelsArr.includes('제목') && combined.post?.title) {
    titleTexts.push(combined.post.title);
  }
  if (!bodyTexts.length && labelsArr.includes('본문') && combined.post?.content) {
    bodyTexts.push(combined.post.content);
  }

  combined.titleTexts = Array.from(new Set(titleTexts.filter(Boolean)));
  combined.bodyTexts = Array.from(new Set(bodyTexts.filter(Boolean)));
  combined.flaggedMediaUrls = Array.from(new Set(all.map((it) => it.contentUrl).filter(Boolean)));
  return combined;
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
}

function startNotificationPolling() {
  loadNotifications();
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer);
  }
  notificationPollTimer = setInterval(loadNotifications, 15000);
}

loadDetail();
initHeader();
startNotificationPolling();
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
    notificationEmptyEl.classList.remove('hidden');
    notificationListEl.innerHTML = '';
    return;
  }
  notificationEmptyEl.classList.add('hidden');
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
