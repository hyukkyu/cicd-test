const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';

const params = new URLSearchParams(window.location.search);
const noticeId = params.get('id');

const titleEl = document.getElementById('noticeDetailTitle');
const metaEl = document.getElementById('noticeDetailMeta');
const bodyEl = document.getElementById('noticeDetailBody');
const attachmentsWrap = document.getElementById('noticeDetailAttachments');
const attachmentListEl = document.getElementById('noticeAttachmentList');
const statusEl = document.getElementById('noticeDetailStatus');

function formatDate(value) {
  if (!value) return '';
  const date = typeof value === 'string' || typeof value === 'number' ? new Date(value) : value;
  return date.toLocaleString('ko-KR');
}

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
    throw new Error(text || '요청이 실패했습니다.');
  }
  if (response.status === 204) return null;
  return response.json();
}

function isImageUrl(url = '') {
  const clean = url.split('?')[0].toLowerCase();
  return /\.(jpe?g|png|gif|webp|avif|bmp|svg)$/.test(clean);
}

function renderNotice(notice) {
  titleEl.textContent = notice.title || '제목 없음';
  const author = notice.authorNickname || notice.authorUsername || '관리자';
  const createdAt = notice.createdAt || notice.createDate || notice.updateDate;
  metaEl.innerHTML = `
    <span>${author}</span>
    <span>·</span>
    <span>작성일 ${createdAt ? formatDate(createdAt) : '-'}</span>
    ${notice.pinned ? '<span class="badge badge-warning" title="상단 고정">📌 고정</span>' : ''}
  `;
  bodyEl.textContent = notice.content || '내용이 없습니다.';

  const attachments = Array.isArray(notice.attachmentUrls) ? notice.attachmentUrls : [];
  if (attachments.length && attachmentsWrap && attachmentListEl) {
    attachmentsWrap.classList.remove('hidden');
    attachmentListEl.innerHTML = attachments
      .map((url) => {
        const name = url.split('/').pop();
        if (isImageUrl(url)) {
          return `
            <a class="notice-attachment notice-attachment--image" href="${url}" target="_blank" rel="noopener">
              <img src="${url}" alt="${name}" loading="lazy" />
              <span class="notice-attachment__name">${name}</span>
            </a>`;
        }
        return `
          <a class="notice-attachment notice-attachment--file" href="${url}" target="_blank" rel="noopener">
            <span class="notice-attachment__icon">📎</span>
            <span class="notice-attachment__name">${name}</span>
          </a>`;
      })
      .join('');
  } else if (attachmentsWrap) {
    attachmentsWrap.classList.add('hidden');
  }
}

async function loadNoticeDetail() {
  if (!noticeId) {
    statusEl.textContent = '잘못된 공지 요청입니다.';
    return;
  }
  statusEl.textContent = '공지사항을 불러오는 중입니다...';
  try {
    const notice = await fetchJson(`/api/notices/${noticeId}`);
    renderNotice(notice);
    statusEl.textContent = '';
  } catch (err) {
    statusEl.textContent = err.message || '공지사항을 불러오지 못했습니다.';
  }
}

loadNoticeDetail();
