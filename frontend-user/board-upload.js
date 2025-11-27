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

const uploadState = {
  boards: [],
  main: null,
  sub: null,
  files: []
};

runWhenReady(initBoardUploadPage);

function initBoardUploadPage() {
  loadBoards()
    .then(() => {
      applyInitialSelection();
      renderSelectors();
      updateCategoryBadges();
      bindUploadForm();
    })
    .catch((error) => {
      setStatus(document.getElementById('uploadStatus'), error.message || '게시판 정보를 불러오지 못했습니다.', 'error');
    });
}

async function loadBoards() {
  const response = await fetchJson('/api/home');
  uploadState.boards = response?.boards || [];
  if (!uploadState.boards.length) {
    throw new Error('게시판 정보를 찾을 수 없습니다.');
  }
}

function applyInitialSelection() {
  const params = new URLSearchParams(window.location.search);
  const desiredMain = params.get('main');
  const desiredSub = params.get('sub');
  const board = uploadState.boards.find((b) => b.name === desiredMain) || uploadState.boards[0];
  uploadState.main = board?.name || null;
  const subs = board?.subBoards || [];
  uploadState.sub = subs.includes(desiredSub) ? desiredSub : subs[0] || null;
}

function bindUploadForm() {
  const mainSelect = document.getElementById('uploadMainSelect');
  const subSelect = document.getElementById('uploadSubSelect');
  const fileInput = document.getElementById('uploadFileInput');
  const form = document.getElementById('boardUploadForm');
  const cancelButton = document.getElementById('uploadCancelButton');

  mainSelect?.addEventListener('change', () => {
    uploadState.main = mainSelect.value || null;
    const board = uploadState.boards.find((b) => b.name === uploadState.main);
    const subs = board?.subBoards || [];
    uploadState.sub = subs[0] || null;
    renderSubSelect();
    updateCategoryBadges();
  });

  subSelect?.addEventListener('change', () => {
    uploadState.sub = subSelect.value || null;
    updateCategoryBadges();
  });

  fileInput?.addEventListener('change', handleUploadFileSelect);

  form?.addEventListener('submit', submitBoardUpload);

  cancelButton?.addEventListener('click', () => {
    uploadState.files = [];
    renderUploadList();
    window.history.length > 1 ? window.history.back() : (window.location.href = 'board-list.html');
  });
}

function renderSelectors() {
  const mainSelect = document.getElementById('uploadMainSelect');
  const subSelect = document.getElementById('uploadSubSelect');
  if (mainSelect) {
    mainSelect.innerHTML = uploadState.boards
      .map((board) => `<option value="${board.name}">${board.displayName}</option>`)
      .join('');
    mainSelect.value = uploadState.main || '';
  }
  renderSubSelect();
  if (subSelect) {
    subSelect.value = uploadState.sub || '';
  }
}

function renderSubSelect() {
  const subSelect = document.getElementById('uploadSubSelect');
  if (!subSelect) return;
  const board = uploadState.boards.find((b) => b.name === uploadState.main);
  const subs = board?.subBoards || [];
  if (!subs.length) {
    subSelect.innerHTML = '<option value="">세부 카테고리가 없습니다</option>';
    subSelect.disabled = true;
    uploadState.sub = null;
    return;
  }
  subSelect.disabled = false;
  subSelect.innerHTML = subs.map((sub) => `<option value="${sub}">${sub}</option>`).join('');
  subSelect.value = uploadState.sub || subs[0];
  uploadState.sub = subSelect.value;
}

function updateCategoryBadges() {
  const mainBadge = document.getElementById('uploadMainBadge');
  const subBadge = document.getElementById('uploadSubBadge');
  const board = uploadState.boards.find((b) => b.name === uploadState.main);
  mainBadge.textContent = board?.displayName || '대메뉴 선택 필요';
  subBadge.textContent = uploadState.sub || '세부 카테고리 선택 필요';
}

async function handleUploadFileSelect(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) return;
  const statusEl = document.getElementById('uploadStatus');
  for (const file of files) {
    if (uploadState.files.length >= 10) {
      setStatus(statusEl, '첨부는 최대 10개까지 가능합니다.', 'error');
      break;
    }
    try {
      setStatus(statusEl, `${file.name} 업로드 중...`, 'info');
      const contentType = file.type || 'application/octet-stream';
      const presigned = await fetchJson('/api/media/presigned', {
        method: 'POST',
        body: JSON.stringify({ directory: 'board', contentType })
      });
      await uploadFileToPresigned(presigned.uploadUrl, file, contentType);
      uploadState.files.push({ id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`, name: file.name, url: presigned.publicUrl });
      setStatus(statusEl, `${file.name} 업로드 완료`, 'success');
    } catch (err) {
      setStatus(statusEl, err.message || `${file.name} 업로드에 실패했습니다.`, 'error');
      break;
    }
  }
  event.target.value = '';
  renderUploadList();
}

function renderUploadList() {
  const list = document.getElementById('uploadFileList');
  if (!list) return;
  list.innerHTML = '';
  if (!uploadState.files.length) {
    list.hidden = true;
    return;
  }
  list.hidden = false;
  uploadState.files.forEach((entry) => {
    const item = document.createElement('li');
    item.innerHTML = `
      <span>${entry.name}</span>
      <button type="button" data-id="${entry.id}">삭제</button>
    `;
    item.querySelector('button')?.addEventListener('click', () => removeUploadEntry(entry.id));
    list.appendChild(item);
  });
}

function removeUploadEntry(id) {
  uploadState.files = uploadState.files.filter((file) => file.id !== id);
  renderUploadList();
}

async function submitBoardUpload(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const statusEl = document.getElementById('uploadStatus');
  if (!uploadState.main || !uploadState.sub) {
    setStatus(statusEl, '대메뉴와 세부 카테고리를 선택해 주세요.', 'error');
    return;
  }
  const title = form.title.value.trim();
  const content = form.content.value.trim();
  if (!title || !content) {
    setStatus(statusEl, '제목과 본문을 입력해 주세요.', 'error');
    return;
  }
  const payload = {
    title,
    content,
    mainBoardName: uploadState.main,
    subBoardName: uploadState.sub,
    tabItem: '',
    fileUrls: uploadState.files.map((file) => file.url)
  };
  setStatus(statusEl, '게시글을 등록하는 중입니다...', 'info');
  try {
    await fetchJson('/api/posts', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    setStatus(statusEl, '게시글이 등록되었습니다.', 'success');
    form.reset();
    uploadState.files = [];
    renderUploadList();
    const query = new URLSearchParams({ main: uploadState.main, sub: uploadState.sub });
    window.location.href = `board-list.html?${query.toString()}`;
  } catch (err) {
    setStatus(statusEl, err.message || '게시글 등록에 실패했습니다.', 'error');
  }
}

async function uploadFileToPresigned(url, file, contentType) {
  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      'Content-Type': contentType
    },
    body: file
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || '파일 업로드에 실패했습니다.');
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
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function setStatus(element, message, type = 'info') {
  if (!element) return;
  element.textContent = message;
  element.dataset.status = type;
}
