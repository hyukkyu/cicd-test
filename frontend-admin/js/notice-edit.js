const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';

const params = new URLSearchParams(window.location.search);
const noticeId = params.get('id');

const titleInput = document.getElementById('noticeTitle');
const contentInput = document.getElementById('noticeContent');
const pinnedInput = document.getElementById('noticePinned');
const attachmentInput = document.getElementById('attachmentInput');
const attachmentList = document.getElementById('attachmentList');
const saveBtn = document.getElementById('saveNotice');
const deleteBtn = document.getElementById('deleteNotice');
const backBtn = document.getElementById('backToNotices');
const pageTitle = document.getElementById('pageTitle');

let attachments = [];
let pendingFiles = [];

function isImageUrl(url = '') {
  const clean = url.split('?')[0].toLowerCase();
  // S3 키에 확장자가 없더라도 notices 디렉터리는 이미지 업로드 전용이므로 미리보기 시도
  if (/\.(jpe?g|png|gif|webp|avif|bmp|svg)$/.test(clean)) return true;
  return clean.includes('/notices/');
}

function attachPreviewUrl(file) {
  if (!file || !file.type || !file.type.startsWith('image/')) return null;
  if (!file.previewUrl) {
    file.previewUrl = URL.createObjectURL(file);
  }
  return file.previewUrl;
}

function revokePreview(file) {
  if (file && file.previewUrl) {
    URL.revokeObjectURL(file.previewUrl);
    delete file.previewUrl;
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

function renderAttachments() {
  if (!attachmentList) return;
  if (!attachments.length && !pendingFiles.length) {
    attachmentList.classList.add('hidden');
    attachmentList.innerHTML = '';
    return;
  }
  attachmentList.classList.remove('hidden');

  const existingMarkup = attachments
    .map(
      (url, idx) => `
        <li class="file-item">
          <div class="file-thumb-wrap">
            ${
              isImageUrl(url)
                ? `<img src="${url}" alt="첨부 이미지" class="file-thumb" loading="lazy" />`
                : `<div class="file-thumb file-thumb-fallback">파일</div>`
            }
          </div>
          <div class="file-meta">
            <a href="${url}" class="link" target="_blank" rel="noopener">${url.split('/').pop()}</a>
          </div>
          <div class="file-actions">
            <button class="btn btn-outline btn-sm" data-idx="${idx}">삭제</button>
          </div>
        </li>`
    )
    .join('');

  const pendingMarkup = pendingFiles
    .map(
      (file, idx) => `
        <li class="file-item text-muted">
          <div class="file-thumb-wrap">
            ${
              file.type && file.type.startsWith('image/')
                ? `<img src="${attachPreviewUrl(file)}" alt="${file.name}" class="file-thumb" />`
                : `<div class="file-thumb file-thumb-fallback">파일</div>`
            }
          </div>
          <div class="file-meta">
            <span>${file.name} (${Math.round(file.size / 1024)}KB) - 업로드 예정</span>
          </div>
          <div class="file-actions">
            <button class="btn btn-outline btn-sm" data-pending-idx="${idx}">취소</button>
          </div>
        </li>`
    )
    .join('');

  attachmentList.innerHTML = existingMarkup + pendingMarkup;

  attachmentList.querySelectorAll('button[data-idx]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const index = Number(btn.dataset.idx);
      attachments.splice(index, 1);
      renderAttachments();
    });
  });

  attachmentList.querySelectorAll('button[data-pending-idx]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const index = Number(btn.dataset['pendingIdx']);
      revokePreview(pendingFiles[index]);
      pendingFiles.splice(index, 1);
      renderAttachments();
    });
  });
}

async function loadNoticeDetail() {
  if (!noticeId) return;
  try {
    const detail = await fetchJson(`/api/admin/notices/${noticeId}`);
    pageTitle.textContent = `공지 수정 #${detail.id}`;
    titleInput.value = detail.title || '';
    contentInput.value = detail.content || '';
    pinnedInput.checked = !!detail.pinned;
    attachments = detail.attachmentUrls || [];
    if (deleteBtn) deleteBtn.classList.remove('hidden');
    renderAttachments();
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '공지 정보를 불러오지 못했습니다.');
  }
}

async function requestPresignedUpload(file) {
  const body = { directory: 'notices', contentType: file.type || 'application/octet-stream' };
  const res = await fetchJson('/api/media/presigned', {
    method: 'POST',
    body: JSON.stringify(body)
  });
  return res;
}

async function uploadFile(file) {
  const presigned = await requestPresignedUpload(file);
  await fetch(presigned.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file
  });
  return presigned.publicUrl;
}

async function handleUpload(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) return;
  files.forEach((file) => attachPreviewUrl(file));
  pendingFiles.push(...files);
  attachmentInput.value = '';
  renderAttachments();
}

async function saveNotice() {
  if (!titleInput.value.trim() || !contentInput.value.trim()) {
    alert('제목과 내용을 입력하세요.');
    return;
  }

  try {
    if (pendingFiles.length) {
      const uploaded = [];
      for (const file of pendingFiles) {
        const url = await uploadFile(file);
        uploaded.push(url);
      }
      attachments.push(...uploaded);
      pendingFiles.forEach(revokePreview);
      pendingFiles = [];
      renderAttachments();
    }
  } catch (err) {
    if (ensureAuth(err)) return;
    alert('파일 업로드 중 오류가 발생했습니다.');
    return;
  }

  const payload = {
    title: titleInput.value.trim(),
    content: contentInput.value.trim(),
    pinned: pinnedInput.checked,
    attachmentUrls: attachments
  };
  try {
    if (noticeId) {
      await fetchJson(`/api/admin/notices/${noticeId}`, { method: 'POST', body: JSON.stringify(payload) });
    } else {
      await fetchJson('/api/admin/notices', { method: 'POST', body: JSON.stringify(payload) });
    }
    alert('저장되었습니다.');
    window.location.href = 'dashboard.html';
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '저장에 실패했습니다.');
  }
}

async function deleteNotice() {
  if (!noticeId) return;
  if (!confirm('삭제하시겠습니까?')) return;
  try {
    await fetchJson(`/api/admin/notices/${noticeId}`, { method: 'DELETE' });
    alert('삭제되었습니다.');
    window.location.href = 'dashboard.html';
  } catch (err) {
    if (ensureAuth(err)) return;
    alert(err.message || '삭제에 실패했습니다.');
  }
}

if (attachmentInput) {
  attachmentInput.addEventListener('change', handleUpload);
}
if (saveBtn) {
  saveBtn.addEventListener('click', saveNotice);
}
if (deleteBtn) {
  deleteBtn.addEventListener('click', deleteNotice);
}
if (backBtn) {
  backBtn.addEventListener('click', () => {
    window.location.href = 'dashboard.html';
  });
}

renderAttachments();
loadNoticeDetail();
