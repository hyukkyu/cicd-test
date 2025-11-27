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
  main: null,
  sub: null,
  tab: '',
  pendingFiles: []
};

let tabSelect;
let statusEl;
let cancelButton;
let mainBadge;
let subBadge;
let mainHiddenInput;
let subHiddenInput;

function initGalleryUploadPage() {
  tabSelect = document.getElementById('uploadTabSelect');
  statusEl = document.getElementById('uploadStatus');
  cancelButton = document.getElementById('uploadCancelButton');
  mainBadge = document.getElementById('uploadMainBadge');
  subBadge = document.getElementById('uploadSubBadge');
  mainHiddenInput = document.getElementById('uploadMainHidden');
  subHiddenInput = document.getElementById('uploadSubHidden');
  initUploadForm();
  document.getElementById('uploadFileInput')?.addEventListener('change', handleUploadFileSelect);
  document.getElementById('galleryUploadForm')?.addEventListener('submit', submitGalleryUpload);
  renderUploadList();
}

runWhenReady(initGalleryUploadPage);

function initUploadForm() {
  if (!tabSelect) return;
  const params = new URLSearchParams(window.location.search);
  const initialMain = params.get('main');
  const initialSub = params.get('sub');
  const initialTab = params.get('tab');
  uploadState.main = initialMain;
  uploadState.sub = initialSub;
  uploadState.tab = initialTab && initialTab !== '전체' ? initialTab : '';
  const category = GALLERY_CONFIG.find((c) => c.key === initialMain);
  const subCategory = category?.subBoards?.find((s) => s.value === initialSub);
  if (!category || !subCategory) {
    handleMissingCategoryContext();
    return;
  }
  updateCategoryDisplay(category, subCategory);
  setHiddenCategoryValues();
  populateTabSelect(category, initialTab);
  tabSelect?.addEventListener('change', () => {
    uploadState.tab = tabSelect.value || '';
  });
  cancelButton?.addEventListener('click', () => {
    uploadState.pendingFiles = [];
    renderUploadList();
    if (document.referrer && document.referrer.includes('gallery')) {
      window.history.back();
    } else {
      window.location.href = 'gallery.html';
    }
  });
}

function handleMissingCategoryContext() {
  setStatus(statusEl, '대메뉴에서 서브 카테고리를 선택한 뒤 업로드 페이지로 이동해 주세요.', 'error');
  tabSelect.disabled = true;
  const form = document.getElementById('galleryUploadForm');
  form?.classList.add('gallery-upload-form--disabled');
  form?.querySelectorAll('input, textarea, button, select').forEach((element) => {
    if (element.id !== 'uploadCancelButton') {
      element.disabled = true;
    }
  });
}

function updateCategoryDisplay(category, subCategory) {
  if (mainBadge) {
    mainBadge.textContent = category?.title || '대메뉴 선택 필요';
  }
  if (subBadge) {
    subBadge.textContent = subCategory?.text || '세부 카테고리 선택 필요';
  }
}

function setHiddenCategoryValues() {
  if (mainHiddenInput) {
    mainHiddenInput.value = uploadState.main || '';
  }
  if (subHiddenInput) {
    subHiddenInput.value = uploadState.sub || '';
  }
}

function populateTabSelect(category, initialTab) {
  tabSelect.innerHTML = '';
  const tabs = category?.tabs || [];
  const deduped = Array.from(new Set(tabs.map((tab) => tab.trim()).filter(Boolean)));
  const options = ['전체', ...deduped.filter((tab) => tab !== '전체')];
  options.forEach((tab) => {
    const option = document.createElement('option');
    option.value = tab === '전체' ? '' : tab;
    option.textContent = tab;
    tabSelect.appendChild(option);
  });
  const desiredValue = initialTab && initialTab !== '전체' ? initialTab : '';
  const hasDesiredValue = options.some((tab) => (tab === '전체' ? desiredValue === '' : tab === desiredValue));
  tabSelect.value = hasDesiredValue ? desiredValue : '';
  uploadState.tab = tabSelect.value || '';
}

function handleUploadFileSelect(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) return;
  files.forEach((file) => {
    uploadState.pendingFiles.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file
    });
  });
  event.target.value = '';
  renderUploadList();
  setStatus(statusEl, '파일이 대기열에 추가되었습니다. 등록 시 업로드됩니다.', 'info');
}

function renderUploadList() {
  const list = document.getElementById('uploadFileList');
  if (!list) return;
  if (!uploadState.pendingFiles.length) {
    list.innerHTML = '';
    list.hidden = true;
    return;
  }
  list.hidden = false;
  list.innerHTML = '';
  uploadState.pendingFiles.forEach((entry) => {
    const item = document.createElement('li');
    item.innerHTML = `
      <span>${entry.file.name} (대기)</span>
      <button type="button" data-id="${entry.id}">삭제</button>
    `;
    item.querySelector('button')?.addEventListener('click', () => {
      removePendingFile(entry.id);
    });
    list.appendChild(item);
  });
}

function removePendingFile(id) {
  uploadState.pendingFiles = uploadState.pendingFiles.filter((entry) => entry.id !== id);
  renderUploadList();
}

function validateUploadForm(form) {
  if (!uploadState.main) {
    return '대메뉴를 선택하세요.';
  }
  if (!uploadState.sub) {
    return '세부 카테고리를 선택하세요.';
  }
  const titleValue = (form.title?.value || '').trim();
  if (!titleValue) {
    return '제목을 입력하세요.';
  }
  const contentValue = (form.content?.value || '').trim();
  if (!contentValue) {
    return '본문을 입력하세요.';
  }
  return '';
}

async function submitGalleryUpload(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const validationError = validateUploadForm(form);
  if (validationError) {
    setStatus(statusEl, validationError, 'error');
    return;
  }
  const formData = new FormData();
  formData.append('title', form.title.value.trim());
  formData.append('content', form.content.value.trim());
  formData.append('mainBoardName', uploadState.main);
  formData.append('subBoardName', uploadState.sub);
  formData.append('tabItem', uploadState.tab || '');
  uploadState.pendingFiles.forEach((entry) => {
    formData.append('files', entry.file, entry.file.name);
  });
  setStatus(statusEl, '첨부 파일과 작품을 등록하는 중입니다...', 'info');
  try {
    await postMultipart('/api/gallery', formData);
    setStatus(statusEl, '등록이 완료되었습니다.', 'success');
    form.reset();
    uploadState.pendingFiles = [];
    uploadState.tab = '';
    renderUploadList();
    window.location.href = 'gallery.html';
  } catch (err) {
    setStatus(statusEl, err.message || '등록에 실패했습니다.', 'error');
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

async function postMultipart(path, formData) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    body: formData
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || '요청에 실패했습니다.');
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return null;
}

function setStatus(element, message, type = 'info') {
  if (!element) return;
  element.textContent = message;
  element.dataset.status = type;
}
