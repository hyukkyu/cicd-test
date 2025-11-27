const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const params = new URLSearchParams(window.location.search);
const postId = params.get('id');

const postIdEl = document.getElementById('postId');
const postTitleEl = document.getElementById('postTitle');
const postBoardEl = document.getElementById('postBoard');
const postBadgesEl = document.getElementById('postBadges');
const postMetaEl = document.getElementById('postMeta');
const postContentEl = document.getElementById('postContent');
const postFilesEl = document.getElementById('postFiles');
const textModerationEl = document.getElementById('textModeration');
const postCommentsEl = document.getElementById('postComments');
const backToPostsBtn = document.getElementById('backToPosts');
const postApproveBtn = document.getElementById('postApprove');
const postHideBtn = document.getElementById('postHide');

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

function formatBoard(main, sub) {
  if (main && sub) return `${main} / ${sub}`;
  return main || sub || '게시판 정보 없음';
}

function renderBadges(detail) {
  const badges = [];
  if (detail.blocked) badges.push('<span class="badge badge-warning">차단됨</span>');
  if (detail.harmful) badges.push('<span class="badge badge-danger">유해판정</span>');
  if (detail.publishedAfterHarmful) {
    badges.push('<span class="badge badge-info">유해판정 이후 승인</span>');
  }
  postBadgesEl.innerHTML = badges.join('') || '<span class="text-muted">-</span>';
}

function renderFiles(files = []) {
  if (!files.length) {
    postFilesEl.innerHTML = '<li class="text-muted">첨부 파일 없음</li>';
    return;
  }
  postFilesEl.innerHTML = files.map((url) => {
    const label = url.split('/').pop();
    return `<li><a href="${url}" class="link" target="_blank" rel="noopener">${label}</a></li>`;
  }).join('');
}

function renderTextModeration(textMod) {
  if (!textMod) {
    textModerationEl.innerHTML = '<p class="text-muted">텍스트 모더레이션 데이터가 없습니다.</p>';
    return;
  }
  textModerationEl.innerHTML = `
    <div><span>감정</span><p>${textMod.dominantSentiment || '-'}</p></div>
    <div><span>부정 점수</span><p>${(textMod.negativeScore || 0).toFixed(2)}</p></div>
    <div><span>PII 감지</span><p>${textMod.piiDetected ? '감지됨' : '없음'}</p></div>
    <div class="span-2"><span>요약</span><p>${textMod.summary || '-'}</p></div>
    <div class="span-2"><span>세부 JSON</span><pre class="content-preview">${textMod.sentimentScoresJson || '-'}</pre></div>
  `;
}

function renderComments(comments = []) {
  if (!comments.length) {
    postCommentsEl.innerHTML = '<p class="text-muted">댓글이 없습니다.</p>';
    return;
  }
  const renderAuthor = (comment) => {
    const name = comment.authorUsername || '익명';
    if (comment.authorId) {
      return `<a class="link" href="user-detail.html?id=${comment.authorId}">${name}</a>`;
    }
    return name;
  };
  const formatStatus = (comment) => (comment.blocked
    ? '<span class="badge badge-danger">차단</span>'
    : '<span class="badge badge-info">정상</span>');
  const rows = comments.map((comment) => `
    <tr>
      <td>#${comment.id}</td>
      <td>${renderAuthor(comment)}</td>
      <td>${comment.content || '-'}</td>
      <td>${formatStatus(comment)}</td>
      <td>${formatDateTime(comment.createdAt)}</td>
    </tr>
  `).join('');
  postCommentsEl.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>작성자</th>
          <th>내용</th>
          <th>상태</th>
          <th>작성 시각</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

async function loadPostDetail() {
  if (!postId) {
    postContentEl.textContent = '게시글 ID가 지정되지 않았습니다.';
    return;
  }
  postIdEl.textContent = postId;
  try {
    const detail = await fetchJson(`/api/admin/posts/${postId}`);
    postTitleEl.textContent = detail.title || '(제목 없음)';
    postBoardEl.textContent = formatBoard(detail.mainBoardName, detail.subBoardName);
    renderBadges(detail);
    const authorEl = document.getElementById('postAuthor');
    if (authorEl) {
      const name = detail.authorUsername || '익명';
      if (detail.authorId) {
        authorEl.innerHTML = `<a class="link" href="user-detail.html?id=${detail.authorId}">${name}</a>`;
      } else {
        authorEl.textContent = name;
      }
    }
    document.getElementById('postStatus').textContent = detail.status || '-';
    document.getElementById('postModeration').textContent = detail.moderationStatus || '-';
    document.getElementById('postCreated').textContent = formatDateTime(detail.createdAt);
    document.getElementById('postViews').textContent = detail.viewCount ?? 0;
    const memoEl = document.getElementById('postModerationMemo');
    if (memoEl) {
      if (detail.publishedAfterHarmful) {
        memoEl.classList.remove('hidden');
      } else {
        memoEl.classList.add('hidden');
      }
    }
    postContentEl.innerHTML = detail.content
      ? detail.content.replace(/\n/g, '<br/>')
      : '<span class="text-muted">내용 없음</span>';
    renderFiles(detail.fileUrls || []);
    renderTextModeration(detail.textModeration);
    renderComments(detail.comments || []);
  } catch (err) {
    if (ensureAuth(err)) return;
    postContentEl.textContent = err.message || '게시글을 불러오지 못했습니다.';
  }
}

loadPostDetail();

if (backToPostsBtn) {
  backToPostsBtn.addEventListener('click', () => {
    localStorage.setItem('adminSection', 'posts');
    window.location.href = 'dashboard.html';
  });
}

async function updatePostStatus(status) {
  if (!postId) return;
  try {
    await fetchJson(`/api/admin/posts/${postId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    await loadPostDetail();
    alert(status === 'PUBLISHED' ? '게시글을 승인했습니다.' : '게시글을 숨김 처리했습니다.');
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '상태 변경에 실패했습니다.');
  }
}

if (postApproveBtn) {
  postApproveBtn.addEventListener('click', () => updatePostStatus('PUBLISHED'));
}
if (postHideBtn) {
  postHideBtn.addEventListener('click', () => updatePostStatus('HIDDEN'));
}
