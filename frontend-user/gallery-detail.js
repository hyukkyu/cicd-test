const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const runWhenReady =
  window.__runWhenDocumentReady ||
  function (callback) {
    if (typeof callback !== 'function') return;
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback);
    } else {
      callback();
    }
  };

const detailState = {
  id: null,
  detail: null,
  comments: { page: 0, size: 20, hasNext: true, items: [], total: 0 },
  replyTarget: null
};
let reportTarget = null;
let currentUser = null;

function initGalleryDetailPage() {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  if (!id) {
    setStatus(document.getElementById('detailStatus'), '잘못된 접근입니다.', 'error');
    return;
  }
  detailState.id = id;
  bindDetailEvents();
  initAuthBridge();
  loadDetail();
}

runWhenReady(initGalleryDetailPage);

function initAuthBridge() {
  const auth = window.CMSAuth;
  if (!auth) {
    refreshCurrentUserFallback();
    return;
  }
  currentUser = auth.getUser() || null;
  auth.onChange((user) => {
    currentUser = user || null;
  });
  if (!currentUser) {
    auth.refresh();
  }
}

async function refreshCurrentUserFallback() {
  try {
    const user = await fetchJson('/api/auth/me');
    currentUser = user || null;
  } catch {
    currentUser = null;
  }
}

function bindDetailEvents() {
  document.getElementById('detailVoteButton')?.addEventListener('click', voteDetail);
  document.getElementById('detailReportButton')?.addEventListener('click', () => openReportModal('POST', detailState.id, detailState.detail?.title));
  document.getElementById('detailCommentForm')?.addEventListener('submit', submitDetailComment);
  document.getElementById('detailLoadMoreComments')?.addEventListener('click', () => loadDetailComments(false));
  document.getElementById('detailCancelReplyButton')?.addEventListener('click', clearDetailReplyTarget);
  document.getElementById('detailCommentList')?.addEventListener('click', handleCommentListClick);
  document.getElementById('reportForm')?.addEventListener('submit', submitReport);
  document.getElementById('closeReportModal')?.addEventListener('click', closeReportModal);
  document.getElementById('cancelReportButton')?.addEventListener('click', closeReportModal);
  document.getElementById('reportModal')?.addEventListener('click', (event) => {
    if (event.target === event.currentTarget) closeReportModal();
  });
}

async function loadDetail() {
  setStatus(document.getElementById('detailStatus'), '작품을 불러오는 중입니다...', 'info');
  try {
    const detail = await fetchJson(`/api/gallery/${detailState.id}`);
    detailState.detail = detail;
    renderDetail(detail);
    setStatus(document.getElementById('detailStatus'), '', 'info');
    await loadDetailComments(true);
  } catch (error) {
    setStatus(document.getElementById('detailStatus'), error.message || '작품을 불러오지 못했습니다.', 'error');
  }
}

function renderDetail(detail) {
  document.getElementById('detailCategory').textContent = `${detail.mainBoardName} / ${detail.subBoardName}`;
  document.getElementById('detailTitle').textContent = detail.title;
  document.getElementById('detailMeta').textContent = `작성자 ${detail.authorNickname || detail.authorUsername || '익명'}`;
  document.getElementById('detailLikeCount').textContent = `추천 ${detail.likeCount ?? 0}`;
  document.getElementById('detailViewCount').textContent = `조회 ${detail.viewCount ?? 0}`;
  document.getElementById('detailCreatedAt').textContent = new Date(detail.createdAt).toLocaleString('ko-KR');
  document.getElementById('detailDescription').textContent = detail.content || '';
  const voteBtn = document.getElementById('detailVoteButton');
  if (voteBtn) {
    voteBtn.textContent = '추천';
    voteBtn.dataset.liked = detail.viewerHasLiked ? 'true' : 'false';
    voteBtn.setAttribute('aria-pressed', detail.viewerHasLiked ? 'true' : 'false');
    voteBtn.classList.toggle('btn-liked', detail.viewerHasLiked);
  }
  const attachments = detail.attachments || [];
  const inlineMedia = document.getElementById('detailInlineMedia');
  inlineMedia.innerHTML = '';
  if (attachments.length) {
    inlineMedia.classList.remove('hidden');
    attachments.forEach((url) => {
      const isImage = url.match(/\.(jpg|jpeg|png|gif|webp)$/i);
      const isVideo = url.match(/\.(mp4|webm|ogg)$/i);
      if (isVideo) {
        const wrapper = document.createElement('div');
        wrapper.className = 'detail-inline-media__item detail-inline-media__item--video';
        const video = document.createElement('video');
        video.src = url;
        video.controls = true;
        video.preload = 'metadata';
        wrapper.appendChild(video);
        inlineMedia.appendChild(wrapper);
        return;
      }
      const item = document.createElement('a');
      item.href = url;
      item.target = '_blank';
      item.rel = 'noopener';
      item.className = 'detail-inline-media__item';
      if (isImage) {
        const thumb = document.createElement('img');
        thumb.src = url;
        thumb.alt = detail.title;
        item.appendChild(thumb);
      } else {
        const badge = document.createElement('span');
        badge.textContent = '첨부 열기';
        item.appendChild(badge);
      }
      inlineMedia.appendChild(item);
    });
  } else {
    inlineMedia.classList.add('hidden');
  }
}

async function voteDetail() {
  if (!currentUser) {
    setStatus(document.getElementById('detailStatus'), '로그인 후 추천할 수 있습니다.', 'error');
    return;
  }
  try {
    await fetchJson(`/api/gallery/${detailState.id}/vote`, { method: 'POST' });
    await loadDetail();
  } catch (err) {
    setStatus(document.getElementById('detailStatus'), err.message || '추천에 실패했습니다.', 'error');
  }
}

async function loadDetailComments(reset) {
  const page = reset ? 0 : detailState.comments.page + 1;
  const params = new URLSearchParams({
    postId: detailState.id,
    page,
    size: detailState.comments.size,
    sort: 'oldest'
  });
  try {
    const res = await fetchJson(`/api/comments?${params.toString()}`);
    detailState.comments = {
      page,
      size: res.size ?? detailState.comments.size,
      hasNext: !res.last,
      items: reset ? res.content : detailState.comments.items.concat(res.content),
      total: res.totalElements ?? res.content.length
    };
    renderDetailComments(detailState.comments.items);
    const loadMore = document.getElementById('detailLoadMoreComments');
    if (loadMore) {
      loadMore.disabled = !detailState.comments.hasNext;
      loadMore.textContent = detailState.comments.hasNext ? '더 보기' : '마지막 댓글입니다';
    }
  } catch (err) {
    setStatus(document.getElementById('detailStatus'), err.message || '댓글을 불러오지 못했습니다.', 'error');
  }
}

function renderDetailComments(comments) {
  const list = document.getElementById('detailCommentList');
  const count = document.getElementById('detailCommentCount');
  if (!list) return;
  list.innerHTML = '';
  const map = new Map();
  comments.forEach((comment) => map.set(comment.id, { ...comment, children: [] }));
  const roots = [];
  map.forEach((comment) => {
    if (comment.parentId && map.has(comment.parentId)) {
      map.get(comment.parentId).children.push(comment);
    } else {
      roots.push(comment);
    }
  });
  roots.forEach((comment) => list.appendChild(renderDetailCommentItem(comment, 0)));
  if (count) {
    count.textContent = `${detailState.comments.total}개의 댓글`;
  }
}

function renderDetailCommentItem(comment, depth) {
  const item = document.createElement('li');
  item.className = 'comment-item';
  if (depth > 0) {
    item.classList.add('comment-child');
  }
  const author = comment.authorNickname || comment.authorUsername || '익명';
  const parentInfo = comment.parentId ? `<span class="comment-parent">↳ ${comment.parentId}번 댓글</span>` : '';
  const controls = [];
  controls.push(`<button class="btn btn-text btn-detail-reply" data-comment-id="${comment.id}" data-comment-author="${author}">답글</button>`);
  if (currentUser && currentUser.username === comment.authorUsername) {
    controls.push(`<button class="btn btn-text btn-detail-delete" data-comment-id="${comment.id}">삭제</button>`);
  }
  controls.push(`<button class="btn btn-text btn-report" data-report-type="COMMENT" data-report-id="${comment.id}" data-report-label="댓글 #${comment.id}">신고</button>`);
  item.innerHTML = `
    <strong>${author}</strong>
    <small>${new Date(comment.createdAt).toLocaleString('ko-KR')}</small>
    ${parentInfo}
    <p>${comment.content}</p>
    <div class="comment-actions">${controls.join('')}</div>
  `;
  const children = comment.children || [];
  if (children.length) {
    const childList = document.createElement('ul');
    childList.className = 'comment-children';
    children.forEach((child) => childList.appendChild(renderDetailCommentItem(child, depth + 1)));
    item.appendChild(childList);
  }
  return item;
}

async function submitDetailComment(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const content = form.querySelector('textarea').value;
  if (!content.trim()) return;
  try {
    await fetchJson('/api/comments', {
      method: 'POST',
      body: JSON.stringify({
        postId: detailState.id,
        parentId: detailState.replyTarget ? detailState.replyTarget.id : null,
        content: content.trim()
      })
    });
    form.reset();
    clearDetailReplyTarget();
    await loadDetailComments(true);
    setStatus(document.getElementById('detailStatus'), '댓글이 등록되었습니다.', 'success');
  } catch (err) {
    setStatus(document.getElementById('detailStatus'), err.message || '댓글 등록에 실패했습니다.', 'error');
  }
}

function handleCommentListClick(event) {
  const target = event.target instanceof Element ? event.target : null;
  if (!target) return;
  const replyBtn = target.closest('.btn-detail-reply');
  if (replyBtn) {
    setDetailReplyTarget(Number(replyBtn.dataset.commentId), replyBtn.dataset.commentAuthor);
    return;
  }
  const deleteBtn = target.closest('.btn-detail-delete');
  if (deleteBtn) {
    deleteGalleryComment(Number(deleteBtn.dataset.commentId));
    return;
  }
  const reportBtn = target.closest('[data-report-type="COMMENT"]');
  if (reportBtn) {
    openReportModal('COMMENT', Number(reportBtn.dataset.reportId), reportBtn.dataset.reportLabel);
  }
}

function setDetailReplyTarget(id, authorLabel) {
  detailState.replyTarget = { id };
  document.getElementById('detailCommentParentId').value = id;
  document.getElementById('detailReplyLabel').textContent = `${authorLabel} 님에게 답글 작성`;
  document.getElementById('detailReplyTarget').classList.remove('hidden');
}

function clearDetailReplyTarget() {
  detailState.replyTarget = null;
  document.getElementById('detailCommentParentId').value = '';
  document.getElementById('detailReplyTarget').classList.add('hidden');
  document.getElementById('detailReplyLabel').textContent = '';
}

function openReportModal(type, targetId, label) {
  reportTarget = { type, targetId, label };
  document.getElementById('reportTargetLabel').textContent = label || '선택된 대상';
  document.getElementById('reportStatus').textContent = '';
  document.getElementById('reportModal').classList.remove('hidden');
  document.body.classList.add('modal-open');
}

function closeReportModal() {
  document.getElementById('reportModal').classList.add('hidden');
  document.body.classList.remove('modal-open');
  reportTarget = null;
  document.getElementById('reportForm').reset();
}

async function submitReport(event) {
  event.preventDefault();
  if (!reportTarget) return;
  const reason = event.currentTarget.reason.value;
  try {
    await fetchJson('/api/reports', {
      method: 'POST',
      body: JSON.stringify({
        targetId: reportTarget.targetId,
        type: reportTarget.type,
        reason
      })
    });
    document.getElementById('reportStatus').textContent = '신고가 접수되었습니다.';
    setTimeout(() => closeReportModal(), 800);
  } catch (err) {
    document.getElementById('reportStatus').textContent = err.message || '신고 접수에 실패했습니다.';
  }
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
    throw new Error(text || '요청에 실패했습니다.');
  }
  const contentType = response.headers.get('content-type') || '';
  const body = await response.text();
  if (!body) {
    return null;
  }
  if (contentType.includes('application/json')) {
    try {
      return JSON.parse(body);
    } catch (err) {
      throw new Error('올바르지 않은 응답 형식입니다.');
    }
  }
  return body;
}

function setStatus(element, message, type = 'info') {
  if (!element) return;
  element.textContent = message;
  element.dataset.status = type;
}
