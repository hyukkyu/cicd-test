const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';

const reportIdEl = document.getElementById('reportId');
const statusMessageEl = document.getElementById('statusMessage');
const reportTypeEl = document.getElementById('reportType');
const reportStatusEl = document.getElementById('reportStatus');
const reportActionEl = document.getElementById('reportAction');
const reportCreatedEl = document.getElementById('reportCreated');
const reportProcessedEl = document.getElementById('reportProcessed');
const reportReporterEl = document.getElementById('reportReporter');
const reportReasonEl = document.getElementById('reportReason');
const postInfoEl = document.getElementById('postInfo');
const commentInfoEl = document.getElementById('commentInfo');
const savedNoteEl = document.getElementById('savedNote');
const approveBtn = document.getElementById('approveReport');
const rejectBtn = document.getElementById('rejectReport');
const backBtn = document.getElementById('backToReports');
const actionModal = document.getElementById('actionModal');
const actionModalTitle = document.getElementById('actionModalTitle');
const actionModalNote = document.getElementById('actionModalNote');
const actionModalConfirm = document.getElementById('actionModalConfirm');
const actionModalCancel = document.getElementById('actionModalCancel');

const params = new URLSearchParams(window.location.search);
const reportId = params.get('id');

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

function translateType(type) {
  if (!type) return '-';
  return type === 'POST' ? '게시글' : '댓글';
}

function translateStatus(status) {
  const normalized = (status || '').toUpperCase();
  switch (normalized) {
    case 'PENDING':
      return '<span class="pill pill-info">대기</span>';
    case 'ACTION_TAKEN':
      return '<span class="pill pill-danger">차단됨</span>';
    case 'REJECTED':
      return '<span class="pill pill-danger">반려</span>';
    default:
      return status || '-';
  }
}

function translateAction(action) {
  const normalized = (action || '').toUpperCase();
  switch (normalized) {
    case 'WARN_AND_HIDE':
      return '<span class="badge badge-danger">경고+차단</span>';
    case 'HIDE_ONLY':
      return '<span class="badge badge-danger">차단</span>';
    case 'APPROVED':
      return '<span class="badge badge-info">승인</span>';
    case 'REJECTED':
      return '<span class="badge badge-warning">반려</span>';
    default:
      return '-';
  }
}

function renderPostInfo(post) {
  if (!post) {
    postInfoEl.innerHTML = '<p class="text-muted">관련 게시글이 없습니다.</p>';
    postInfoEl.classList.add('text-muted');
    return;
  }
  postInfoEl.classList.remove('text-muted');
  const author = post.authorUsername || '알 수 없음';
  const authorLink = post.authorId ? `<a class="link" href="user-detail.html?id=${post.authorId}">${author}</a>` : author;
  postInfoEl.innerHTML = `
    <div><span>ID</span><p>#${post.id}</p></div>
    <div><span>제목</span><p>${post.title || '(제목 없음)'}</p></div>
    <div><span>작성자</span><p>${authorLink}</p></div>
    <div><span>메뉴</span><p>${formatBoard(post.mainBoardName, post.subBoardName)}</p></div>
    <div><span>작성 시각</span><p>${formatDateTime(post.createdAt)}</p></div>
    <div class="span-2"><span>내용</span><p>${post.content ? post.content.replace(/\n/g, '<br/>') : '-'}</p></div>
  `;
}

function renderCommentInfo(comment) {
  if (!comment) {
    commentInfoEl.innerHTML = '<p class="text-muted">관련 댓글이 없습니다.</p>';
    commentInfoEl.classList.add('text-muted');
    return;
  }
  commentInfoEl.classList.remove('text-muted');
  const author = comment.authorUsername || '알 수 없음';
  const authorLink = comment.authorId ? `<a class="link" href="user-detail.html?id=${comment.authorId}">${author}</a>` : author;
  const statusLabel = comment.status === 'BLOCKED'
    ? '<span class="badge badge-danger">차단됨</span>'
    : '<span class="badge badge-info">정상</span>';
  commentInfoEl.innerHTML = `
    <div><span>ID</span><p>#${comment.id}</p></div>
    <div><span>작성자</span><p>${authorLink}</p></div>
    <div><span>상태</span><p>${statusLabel}</p></div>
    <div><span>작성 시각</span><p>${formatDateTime(comment.createdAt)}</p></div>
    <div><span>게시글 ID</span><p>${comment.postId ? `#${comment.postId}` : '-'}</p></div>
    <div class="span-2"><span>내용</span><p>${comment.content ? comment.content.replace(/\n/g, '<br/>') : '-'}</p></div>
  `;
}

function formatBoard(main, sub) {
  if (main && sub) return `${main} / ${sub}`;
  if (main) return main;
  return sub || '-';
}

function updateActionState(detail) {
  const isPending = (detail.status || '').toUpperCase() === 'PENDING';
  if (approveBtn) approveBtn.disabled = !isPending;
  if (rejectBtn) rejectBtn.disabled = !isPending;
}

function renderDetail(detail) {
  if (!detail) return;
  reportIdEl.textContent = detail.id || '-';
  reportTypeEl.textContent = translateType(detail.type);
  reportStatusEl.innerHTML = translateStatus(detail.status);
  reportActionEl.innerHTML = translateAction(detail.action);
  reportCreatedEl.textContent = formatDateTime(detail.createdAt);
  reportProcessedEl.textContent = formatDateTime(detail.processedAt);
  reportReporterEl.textContent = detail.reporterName || detail.reporterUsername || '익명';
  reportReasonEl.textContent = detail.reason || '-';
  if (savedNoteEl) {
    savedNoteEl.textContent = detail.adminNote || '-';
  }
  renderPostInfo(detail.post);
  renderCommentInfo(detail.comment);
  updateActionState(detail);

  const statusText = (detail.status === 'ACTION_TAKEN' && detail.action === 'APPROVED')
    ? '신고가 승인(차단)되었습니다.'
    : detail.status === 'ACTION_TAKEN'
      ? '신고 대상이 차단되었습니다.'
      : detail.status === 'REJECTED'
        ? '신고가 반려되었습니다.'
        : '처리를 진행하세요.';
  if (statusMessageEl) {
    statusMessageEl.textContent = statusText;
    statusMessageEl.classList.toggle('text-muted', detail.status === 'PENDING');
  }
}

async function loadDetail() {
  if (!reportId) {
    statusMessageEl.textContent = '신고 ID가 지정되지 않았습니다.';
    return;
  }
  try {
    const detail = await fetchJson(`/api/admin/reports/${reportId}`);
    renderDetail(detail);
  } catch (err) {
    if (ensureAuth(err)) return;
    statusMessageEl.textContent = err.message || '신고 정보를 불러오지 못했습니다.';
  }
}

async function performAction(action) {
  if (!reportId) return;
  const preset = (savedNoteEl?.textContent || '').trim();
  const note = await openActionModal(action === 'approve' ? '승인(차단) 사유' : '반려 사유', preset);
  if (!note) return;
  const endpoint = action === 'approve'
    ? `/api/admin/reports/${reportId}/approve`
    : `/api/admin/reports/${reportId}/reject`;
  try {
    const updated = await fetchJson(endpoint, {
      method: 'POST',
      body: JSON.stringify({ note: note || '' })
    });
    renderDetail(updated);
    const message = action === 'approve'
      ? '신고를 승인(차단)했습니다.'
      : '신고를 반려했습니다.';
    alert(message);
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '처리에 실패했습니다.');
  }
}

if (backBtn) {
  backBtn.addEventListener('click', () => {
    localStorage.setItem('adminSection', 'reports');
    window.location.href = 'dashboard.html';
  });
}
if (approveBtn) {
  approveBtn.addEventListener('click', () => performAction('approve'));
}
if (rejectBtn) {
  rejectBtn.addEventListener('click', () => performAction('reject'));
}

loadDetail();

function openActionModal(title, preset = '') {
  if (!actionModal || !actionModalTitle || !actionModalNote || !actionModalConfirm || !actionModalCancel) {
    const fallback = window.prompt(title, preset === '-' ? '' : preset || '');
    return Promise.resolve(fallback ? fallback.trim() : '');
  }
  actionModalTitle.textContent = title;
  const normalizedPreset = preset === '-' ? '' : preset || '';
  actionModalNote.value = normalizedPreset;
  actionModal.classList.remove('hidden');
  actionModalNote.focus();

  return new Promise((resolve) => {
    const cleanup = () => {
      actionModal.classList.add('hidden');
      actionModalNote.value = '';
      actionModalConfirm.removeEventListener('click', onConfirm);
      actionModalCancel.removeEventListener('click', onCancel);
      actionModal.removeEventListener('click', onBackdrop);
      document.removeEventListener('keydown', onKey);
    };
    const onConfirm = () => {
      const value = actionModalNote.value.trim();
      cleanup();
      resolve(value);
    };
    const onCancel = () => {
      cleanup();
      resolve('');
    };
    const onBackdrop = (event) => {
      if (event.target === actionModal) {
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
    actionModalConfirm.addEventListener('click', onConfirm);
    actionModalCancel.addEventListener('click', onCancel);
    actionModal.addEventListener('click', onBackdrop);
    document.addEventListener('keydown', onKey);
  });
}
