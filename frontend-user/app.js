const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const AUTH_STORAGE_KEY = 'cms:auth:user';
let authStorageAvailable = null;
const cognitoDomain = window.__COGNITO_DOMAIN__ || 'https://cms-community.auth.ap-northeast-1.amazoncognito.com';
const userClientId = window.__COGNITO_USER_CLIENT_ID__ || '';
const redirectUri = window.__COGNITO_REDIRECT_URI__ || window.location.origin;
const COGNITO_LOGIN_URL = `${cognitoDomain}/login?client_id=${encodeURIComponent(
  userClientId
)}&response_type=code&scope=openid+profile&redirect_uri=${encodeURIComponent(redirectUri)}`;

function supportsAuthStorage() {
  if (authStorageAvailable !== null) {
    return authStorageAvailable;
  }
  try {
    const testKey = '__cms_auth__';
    window.localStorage.setItem(testKey, '1');
    window.localStorage.removeItem(testKey);
    authStorageAvailable = true;
  } catch {
    authStorageAvailable = false;
  }
  return authStorageAvailable;
}

function persistSharedAuthUser(user) {
  if (!supportsAuthStorage()) {
    return;
  }
  try {
    if (user) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
    } else {
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  } catch {
    // ignore storage quota errors
  }
}

function hydrateSharedAuthUser() {
  if (!supportsAuthStorage()) {
    return null;
  }
  try {
    const stored = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!stored) {
      return null;
    }
    return JSON.parse(stored);
  } catch {
    return null;
  }
}

const headerLoginButton = document.getElementById('headerLoginButton');
const headerSignupButton = document.getElementById('headerSignupButton');
const headerLogoutButton = document.getElementById('headerLogoutButton');
const userBadge = document.getElementById('userBadge');
const userNameLabel = document.getElementById('userName');
const userRoleLabel = document.getElementById('userRole');
const userAvatar = document.getElementById('userAvatar');
const boardWrapper = document.querySelector('.board-swiper .swiper-wrapper');
const latestList = document.getElementById('latestPostList');
const popularList = document.getElementById('popularPostList');
const latestEmpty = document.getElementById('latestEmpty');
const popularEmpty = document.getElementById('popularEmpty');
const loginForm = document.getElementById('loginForm');
const signupForm = document.getElementById('signupForm');
const loginFeedback = document.getElementById('loginFeedback');
const loginSupportButton = document.getElementById('loginSupportButton');
const signupFeedback = document.getElementById('signupFeedback');
const welcomeMessage = document.getElementById('welcomeMessage');
const cognitoButton = document.getElementById('cognitoButton');
const navLinks = document.querySelectorAll('.site-nav__link');
const sections = document.querySelectorAll('[data-section]');
const authTabs = document.querySelectorAll('.auth-tab');
const authPanels = document.querySelectorAll('.auth-panel');
const verifyEmailForm = document.getElementById('verifyEmailForm');
const verifyFeedback = document.getElementById('verifyFeedback');
const resendCodeButton = document.getElementById('resendCodeButton');
const findIdForm = document.getElementById('findIdForm');
const findIdFeedback = document.getElementById('findIdFeedback');
const requestResetCodeForm = document.getElementById('requestResetCodeForm');
const verifyResetCodeForm = document.getElementById('verifyResetCodeForm');
const resetPasswordForm = document.getElementById('resetPasswordForm');
const forgotFeedback = document.getElementById('forgotFeedback');

// Board/Gallery DOM references
const mainBoardSelect = document.getElementById('mainBoardSelect');
const subBoardSelect = document.getElementById('subBoardSelect');
const tabSelect = document.getElementById('tabSelect');
const searchTypeSelect = document.getElementById('searchType');
const searchKeywordInput = document.getElementById('searchKeyword');
const searchButton = document.getElementById('searchButton');
const communityPostList = document.getElementById('communityPostList');
const boardEmpty = document.getElementById('boardEmpty');
const detailTitle = document.getElementById('detailTitle');
const postMetaEl = document.getElementById('postMeta');
const postContentEl = document.getElementById('postContent');
const attachmentListEl = document.getElementById('attachmentList');
const commentListEl = document.getElementById('commentList');
const commentCountEl = document.getElementById('commentCount');
const commentForm = document.getElementById('commentForm');
const commentParentInput = document.getElementById('commentParentId');
const commentReplyTarget = document.getElementById('commentReplyTarget');
const commentReplyLabel = document.getElementById('commentReplyLabel');
const cancelReplyButton = document.getElementById('cancelReplyButton');
const loadMoreCommentsButton = document.getElementById('loadMoreComments');
const voteButton = document.getElementById('voteButton');
const deletePostButton = document.getElementById('deletePostButton');
const editPostButton = document.getElementById('editPostButton');
const postForm = document.getElementById('postForm');
const resetPostFormBtn = document.getElementById('resetPostForm');
const formMainBoard = document.getElementById('formMainBoard');
const formSubBoard = document.getElementById('formSubBoard');
const galleryList = document.getElementById('galleryList');
const galleryForm = document.getElementById('galleryForm');
const galleryFeedback = document.getElementById('galleryFeedback');
const galleryMainBoardSelect = document.getElementById('galleryMainBoard');
const gallerySubBoardSelect = document.getElementById('gallerySubBoard');
const refreshGalleryBtn = document.getElementById('refreshGallery');
const galleryUploadInput = document.getElementById('galleryUploadInput');
const galleryUploadList = document.getElementById('galleryUploadList');
const galleryModal = document.getElementById('galleryModal');
const closeGalleryModalButton = document.getElementById('closeGalleryModal');
const galleryModalMedia = document.getElementById('galleryModalMedia');
const galleryModalTitle = document.getElementById('galleryModalTitle');
const galleryModalBoard = document.getElementById('galleryModalBoard');
const galleryModalMeta = document.getElementById('galleryModalMeta');
const galleryModalDescription = document.getElementById('galleryModalDescription');
const galleryModalAttachments = document.getElementById('galleryModalAttachments');
const galleryModalStats = document.getElementById('galleryModalStats');
const galleryModalComments = document.getElementById('galleryModalComments');
const galleryModalStatus = document.getElementById('galleryModalStatus');
const galleryModalReportButton = document.getElementById('galleryModalReport');
const galleryCommentForm = document.getElementById('galleryCommentForm');
const galleryLoadMoreCommentsButton = document.getElementById('galleryLoadMoreComments');
const galleryCommentParentInput = document.getElementById('galleryCommentParentId');
const galleryCommentReplyTarget = document.getElementById('galleryCommentReplyTarget');
const galleryCommentReplyLabel = document.getElementById('galleryCommentReplyLabel');
const galleryCancelReplyButton = document.getElementById('galleryCancelReplyButton');
const reportModal = document.getElementById('reportModal');
const reportModalTarget = document.getElementById('reportModalTarget');
const reportModalStatus = document.getElementById('reportModalStatus');
const reportForm = document.getElementById('reportForm');
const reportReasonInput = document.getElementById('reportReason');
const closeReportModalButton = document.getElementById('closeReportModal');
const cancelReportButton = document.getElementById('cancelReportButton');
const reportPostButton = document.getElementById('reportPostButton');

const heroLogoutButton = document.getElementById('logoutButton');
const accountPanel = document.querySelector('[data-section="account"]');
const accountAccessMessage = document.getElementById('accountAccessMessage');
const profileNicknameLabel = document.getElementById('profileNickname');
const profileUsernameLabel = document.getElementById('profileUsername');
const profileEmailLabel = document.getElementById('profileEmail');
const profileRoleLabel = document.getElementById('profileRole');
const profileStatusLabel = document.getElementById('profileStatusLabel');
const profileWarnCountLabel = document.getElementById('profileWarnCount');
const profileCreatedAtLabel = document.getElementById('profileCreatedAt');
const profileAvatarLarge = document.getElementById('profileAvatarLarge');
const profileForm = document.getElementById('profileForm');
const profileFeedback = document.getElementById('profileFeedback');
const passwordForm = document.getElementById('passwordForm');
const passwordFeedback = document.getElementById('passwordFeedback');
const deleteAccountButton = document.getElementById('deleteAccountButton');
const deleteFeedback = document.getElementById('deleteFeedback');
const noticeTableBody = document.getElementById('noticeTableBody');
const noticeStatus = document.getElementById('noticeStatus');
const noticeSearchForm = document.getElementById('noticeSearchForm');
const noticeSearchInput = document.getElementById('noticeSearchInput');
const refreshNoticesBtn = document.getElementById('refreshNotices');
const openAccountSupportButton = document.getElementById('openAccountSupport');
let boardSwiper;
let boardDisplayNames = {};
let boardSections = [];
let boardInitialized = false;
let currentPostId = null;
let currentPostDetail = null;
let cachedUser = null;
let profileCache = null;
const galleryUploadedEntries = [];
let currentGalleryId = null;
let reportTarget = null;
let replyTargetComment = null;
let galleryCommentState = { page: 0, size: 20, hasNext: true, items: [] };
let galleryReplyTarget = null;
let commentPageState = { page: 0, size: 20, hasNext: true, items: [] };
let noticeCache = [];

function toPlainText(str) {
  if (!str) return '';
  return str.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}

const storedUser = hydrateSharedAuthUser();
if (storedUser) {
  cachedUser = storedUser;
  updateAuthUI(cachedUser);
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

async function fetchFormData(path, formData, method = 'POST') {
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    body: formData,
    credentials: 'include'
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || '요청에 실패했습니다.');
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

function setStatus(element, message, type = 'info') {
  if (!element) return;
  element.textContent = message;
  element.dataset.status = type;
}

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function renderPostList(posts, container, emptyEl) {
  if (!container || !emptyEl) {
    return;
  }
  container.innerHTML = '';
  if (!posts || posts.length === 0) {
    emptyEl.hidden = false;
    return;
  }
  emptyEl.hidden = true;
  const preferGalleryDetail = container.dataset.detail === 'gallery';
  posts.forEach((post) => {
    const author = post.authorNickname || post.authorUsername || '익명';
    const boardName = boardDisplayNames[post.mainBoardName] || post.mainBoardName || '커뮤니티';
    const detailId = post.galleryId || post.id;
    const linkHref = preferGalleryDetail ? `gallery-detail.html?id=${encodeURIComponent(detailId)}` : `https://api.cms-community.com/post/detail/${post.id}`;
    const linkAttrs = preferGalleryDetail ? '' : ' target="_blank" rel="noopener"';
    const listItem = document.createElement('li');
    listItem.className = 'post-item';
    listItem.innerHTML = `
      <div class="post-item__top">
        <span class="board-chip">${boardName}</span>
        <a class="post-link" href="${linkHref}"${linkAttrs}>
          ${post.title}
        </a>
      </div>
      <div class="post-meta">
        <span>${author}</span>
        <span>${formatDate(post.createdAt)}</span>
        <span>추천 ${post.voteCount ?? 0}</span>
        <span>조회 ${post.viewCount ?? 0}</span>
      </div>
    `;
    container.appendChild(listItem);
  });
}

function renderNotices(notices) {
  noticeCache = notices || [];
  applyNoticeFilter();
}

function applyNoticeFilter() {
  if (!noticeTableBody) {
    return;
  }
  const keyword = (noticeSearchInput?.value || '').trim().toLowerCase();
  const filtered = keyword
    ? noticeCache.filter((notice) => {
        const text = `${notice.title || ''} ${notice.summary || ''} ${notice.content || ''}`.toLowerCase();
        const attachments = (notice.attachmentUrls || []).map((url) => url.toLowerCase()).join(' ');
        return text.includes(keyword) || attachments.includes(keyword);
      })
    : noticeCache;

  noticeTableBody.innerHTML = '';
  if (!filtered.length) {
    if (noticeStatus) {
      noticeStatus.textContent = keyword ? '검색 결과가 없습니다.' : '등록된 공지사항이 없습니다.';
    }
    return;
  }
  if (noticeStatus) {
    noticeStatus.textContent = '';
  }

  filtered.forEach((notice) => {
    const row = document.createElement('div');
    row.className = `notice-row${notice.pinned ? ' notice-row--pinned' : ''}`;
    const attachments = Array.isArray(notice.attachmentUrls) ? notice.attachmentUrls : [];
    const author = notice.authorNickname || notice.authorUsername || '관리자';
    const createdAt = notice.createdAt || notice.createDate || notice.updateDate;
    row.dataset.noticeId = notice.id;
    row.innerHTML = `
      <div class="notice-cell notice-cell--title">
        <span class="notice-title-wrap">
          ${notice.pinned ? '<span class="pin-icon" title="상단 고정">📌</span>' : ''}
          <span class="notice-title-text">${notice.title || '제목 없음'}</span>
          ${attachments.length ? '<span class="clip-icon" aria-label="첨부파일 있음" title="첨부파일 있음">📎</span>' : ''}
        </span>
      </div>
      <div class="notice-cell">${author}</div>
      <div class="notice-cell">${createdAt ? formatDate(createdAt) : '-'}</div>
    `;
    row.addEventListener('click', () => {
      if (!notice.id) return;
      window.location.href = `notice-detail.html?id=${encodeURIComponent(notice.id)}`;
    });
    noticeTableBody.appendChild(row);
  });
}

function renderBoards(boards) {
  if (!boardWrapper) {
    return;
  }
  boardWrapper.innerHTML = '';
  if (!boards || boards.length === 0) {
    boardWrapper.innerHTML = '<p class="empty">카테고리 정보를 불러오지 못했습니다.</p>';
    return;
  }

  boards.forEach((board) => {
    boardDisplayNames[board.name] = board.displayName;
    const slide = document.createElement('div');
    slide.className = 'swiper-slide';
    const imageSrc = `img/${board.name}.jpg`;
    const subItems = board.subBoards && board.subBoards.length
      ? board.subBoards.map((item) => `<li>${item}</li>`).join('')
      : '<li>등록된 세부 카테고리가 없습니다.</li>';
    slide.innerHTML = `
      <div class="board-card">
        <img class="board-card__image" src="${imageSrc}" alt="${board.displayName}" loading="lazy" />
        <div class="board-card__body">
          <span class="board-chip">${board.displayName}</span>
          <ul class="sub-board-list">${subItems}</ul>
        </div>
      </div>
    `;
    boardWrapper.appendChild(slide);
  });

  if (boardSwiper) {
    boardSwiper.update();
  } else {
    boardSwiper = new Swiper('.board-swiper', {
      slidesPerView: 1.2,
      spaceBetween: 16,
      breakpoints: {
        640: { slidesPerView: 2, spaceBetween: 18 },
        960: { slidesPerView: 3, spaceBetween: 20 },
        1200: { slidesPerView: 4, spaceBetween: 24 }
      },
      navigation: {
        nextEl: '.swiper-button-next',
        prevEl: '.swiper-button-prev'
      }
    });
  }
}

function mapBoards(boards) {
  boardSections = boards || [];
  if (!boardSections.length) {
    return;
  }
  if (!boardInitialized) {
    initializeBoardSelectors();
    boardInitialized = true;
  } else {
    syncBoardSelectors();
  }
  loadBoardPosts();
  loadGallery();
}

function renderProfileSummary(profile) {
  const nickname = profile?.nickname || '로그인이 필요합니다';
  if (profileNicknameLabel) {
    profileNicknameLabel.textContent = nickname;
  }
  if (profileUsernameLabel) {
    profileUsernameLabel.textContent = profile ? `@${profile.username}` : '@guest';
  }
  if (profileEmailLabel) {
    profileEmailLabel.textContent = profile?.email || '-';
  }
  if (profileRoleLabel) {
    profileRoleLabel.textContent = profile?.role || '-';
  }
  if (profileStatusLabel) {
    profileStatusLabel.textContent = profile?.status || '-';
  }
  if (profileWarnCountLabel) {
    const warnText = profile?.warnCount != null ? `${profile.warnCount}회` : '-';
    profileWarnCountLabel.textContent = warnText;
  }
  if (profileCreatedAtLabel) {
    profileCreatedAtLabel.textContent = profile?.createdAt ? formatDate(profile.createdAt) : '-';
  }
  if (profileAvatarLarge) {
    const initial = nickname.charAt(0).toUpperCase();
    profileAvatarLarge.textContent = initial || 'U';
    if (profile?.profilePictureUrl) {
      profileAvatarLarge.style.backgroundImage = `url(${profile.profilePictureUrl})`;
      profileAvatarLarge.classList.add('has-image');
    } else {
      profileAvatarLarge.style.backgroundImage = 'none';
      profileAvatarLarge.classList.remove('has-image');
    }
  }
  if (profileForm) {
    const nicknameInput = profileForm.querySelector('[name="nickname"]');
    if (nicknameInput) {
      nicknameInput.value = profile?.nickname || '';
    }
  }
}

function setAccountInteractivity(enabled) {
  document.querySelectorAll('[data-requires-auth]').forEach((card) => {
    card.classList.toggle('is-disabled', !enabled);
    card.querySelectorAll('input, textarea, button, select').forEach((control) => {
      control.disabled = !enabled;
    });
  });
}

function isAccountSectionVisible() {
  if (!accountPanel) return false;
  return !accountPanel.classList.contains('hidden');
}

function updateAccountAccessState(messageOverride, statusOverride) {
  const loggedIn = Boolean(cachedUser);
  if (accountAccessMessage) {
    const defaultMessage = loggedIn ? '프로필을 수정하고 계정 설정을 관리하세요.' : '로그인 후 이용할 수 있습니다.';
    const statusMessage = messageOverride ?? defaultMessage;
    const defaultStatus = loggedIn ? 'info' : 'error';
    const messageStatus = statusOverride ?? defaultStatus;
    setStatus(accountAccessMessage, statusMessage, messageStatus);
  }
  setAccountInteractivity(loggedIn);
  if (loggedIn) {
    if (isAccountSectionVisible()) {
      loadProfile();
    }
  } else {
    profileCache = null;
    renderProfileSummary(null);
  }
}

function handleUnauthorized(message) {
  cachedUser = null;
  profileCache = null;
  updateAuthUI(null);
  updateAccountAccessState(message || '로그인이 필요합니다.', 'error');
}

async function loadProfile(force = false) {
  if (!force && !isAccountSectionVisible()) {
    return;
  }
  if (!cachedUser) {
    profileCache = null;
    renderProfileSummary(null);
    return;
  }
  try {
    const response = await fetch(`${API_BASE}/api/auth/profile`, { credentials: 'include' });
    if (response.status === 401) {
      handleUnauthorized('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
      return;
    }
    if (!response.ok) {
      throw new Error('프로필 정보를 불러오지 못했습니다.');
    }
    profileCache = await response.json();
    renderProfileSummary(profileCache);
  } catch (err) {
    console.error(err);
    if (accountAccessMessage) {
      setStatus(accountAccessMessage, err.message || '프로필 정보를 불러오지 못했습니다.', 'error');
    }
  }
}

function initializeBoardSelectors() {
  setMainBoardOptions(mainBoardSelect, true);
  setMainBoardOptions(formMainBoard);
  setMainBoardOptions(galleryMainBoardSelect);
  const defaultMain = boardSections[0]?.name || '';
  if (mainBoardSelect && !mainBoardSelect.value && defaultMain) {
    mainBoardSelect.value = defaultMain;
  }
  if (formMainBoard && !formMainBoard.value && defaultMain) {
    formMainBoard.value = defaultMain;
  }
  if (galleryMainBoardSelect && !galleryMainBoardSelect.value && defaultMain) {
    galleryMainBoardSelect.value = defaultMain;
  }
  updateLinkedSubBoards();
}

function syncBoardSelectors() {
  setMainBoardOptions(mainBoardSelect, true);
  setMainBoardOptions(formMainBoard);
  setMainBoardOptions(galleryMainBoardSelect);
  updateLinkedSubBoards();
}

function updateLinkedSubBoards() {
  updateSubBoardOptions(mainBoardSelect, subBoardSelect);
  updateSubBoardOptions(formMainBoard, formSubBoard);
  updateSubBoardOptions(galleryMainBoardSelect, gallerySubBoardSelect);
  setDefaultOption(subBoardSelect);
  setDefaultOption(formSubBoard);
  setDefaultOption(gallerySubBoardSelect);
}

function setMainBoardOptions(select, includePlaceholder = false) {
  if (!select) return;
  const current = select.value;
  select.innerHTML = '';
  if (includePlaceholder) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = '카테고리 선택';
    select.appendChild(option);
  }
  boardSections.forEach((board) => {
    const option = document.createElement('option');
    option.value = board.name;
    option.textContent = board.displayName;
    select.appendChild(option);
  });
  if (current) {
    select.value = current;
  }
}

function updateSubBoardOptions(mainSelect, targetSelect) {
  if (!targetSelect) return;
  const mainValue = mainSelect?.value;
  const board = boardSections.find((b) => b.name === mainValue);
  const subs = board ? board.subBoards : [];
  const previous = targetSelect.value;
  targetSelect.innerHTML = '';
  subs.forEach((sub) => {
    const option = document.createElement('option');
    option.value = sub;
    option.textContent = sub;
    targetSelect.appendChild(option);
  });
  if (previous && subs.includes(previous)) {
    targetSelect.value = previous;
  }
}

function setDefaultOption(select) {
  if (select && !select.value && select.options.length) {
    select.value = select.options[0].value;
  }
}

function parseFileInputs(rawValue) {
  if (!rawValue) return [];
  return rawValue
    .split(/\n|,/)
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
}

function renderGalleryUploads() {
  if (!galleryUploadList) return;
  galleryUploadList.innerHTML = '';
  if (!galleryUploadedEntries.length) {
    return;
  }
  galleryUploadedEntries.forEach((entry, index) => {
    const item = document.createElement('li');
    item.innerHTML = `
      <span>${entry.name || entry.url}</span>
      <button type="button" data-index="${index}">삭제</button>
    `;
    galleryUploadList.appendChild(item);
  });
}

function resetGalleryUploads() {
  galleryUploadedEntries.length = 0;
  if (galleryUploadList) {
    galleryUploadList.innerHTML = '';
  }
}

function removeGalleryUpload(index) {
  galleryUploadedEntries.splice(index, 1);
  renderGalleryUploads();
}

async function uploadFileToPresigned(url, file, contentType) {
  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      'Content-Type': contentType || 'application/octet-stream'
    },
    body: file
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'S3 업로드에 실패했습니다.');
  }
}

async function handleGalleryFileSelect(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) {
    return;
  }
  if (!cachedUser) {
    setStatus(galleryFeedback, '로그인 후 업로드할 수 있습니다.', 'error');
    event.target.value = '';
    return;
  }
  setStatus(galleryFeedback, '파일을 업로드하는 중입니다...', 'info');
  for (const file of files) {
    try {
      const contentType = file.type || 'application/octet-stream';
      const presigned = await fetchJson('/api/media/presigned', {
        method: 'POST',
        body: JSON.stringify({ directory: 'gallery', contentType })
      });
      await uploadFileToPresigned(presigned.uploadUrl, file, contentType);
      galleryUploadedEntries.push({ url: presigned.publicUrl, name: file.name });
      renderGalleryUploads();
    } catch (err) {
      setStatus(galleryFeedback, err.message || `${file.name} 업로드에 실패했습니다.`, 'error');
      break;
    }
  }
  event.target.value = '';
  if (galleryUploadedEntries.length) {
    setStatus(galleryFeedback, '업로드가 완료되었습니다. 게시를 진행하세요.', 'success');
  }
}

function renderBoardPosts(posts) {
  if (!communityPostList) return;
  communityPostList.innerHTML = '';
  if (!posts || posts.length === 0) {
    if (boardEmpty) {
      boardEmpty.hidden = false;
    }
    return;
  }
  if (boardEmpty) {
    boardEmpty.hidden = true;
  }

  posts.forEach((post) => {
    const item = document.createElement('li');
    item.className = 'post-item';
    item.dataset.postId = post.id;
    item.innerHTML = `
      <div class="post-item__top">
        <span class="board-chip">${post.mainBoardName || ''}</span>
        <span class="post-link">${post.title}</span>
      </div>
      <div class="post-meta">
        <span>${post.authorNickname || post.authorUsername || '익명'}</span>
        <span>${formatDate(post.createdAt)}</span>
        <span>추천 ${post.voteCount ?? 0}</span>
      </div>
    `;
    item.addEventListener('click', () => showPostDetail(post.id));
    communityPostList.appendChild(item);
  });
}

async function loadBoardPosts() {
  if (!subBoardSelect || !boardSections.length) return;
  setDefaultOption(subBoardSelect);
  const params = new URLSearchParams({ page: 0, searchType: searchTypeSelect?.value || 'all' });
  const keyword = searchKeywordInput?.value?.trim();
  if (keyword) params.set('kw', keyword);
  if (subBoardSelect.value) params.set('subBoard', subBoardSelect.value);
  const tabValue = tabSelect?.value;
  if (tabValue) params.set('tab', tabValue);
  try {
    const page = await fetchJson(`/api/posts?${params.toString()}`);
    renderBoardPosts(page?.content || []);
  } catch (err) {
    console.error(err);
    if (boardEmpty) {
      boardEmpty.textContent = err.message || '게시글을 불러오지 못했습니다.';
      boardEmpty.hidden = false;
    }
  }
}

async function showPostDetail(postId) {
  if (!postId) return;
  try {
    const detail = await fetchJson(`/api/posts/${postId}`);
    currentPostId = detail.id;
    currentPostDetail = detail;
    clearReplyTarget();
    await loadComments(true);
    renderPostDetail(detail);
  } catch (err) {
    console.error(err);
    setStatus(loginFeedback, err.message || '게시글을 불러오지 못했습니다.', 'error');
  }
}

async function loadComments(reset = false) {
  if (!currentPostId) return;
  const page = reset ? 0 : commentPageState.page + 1;
  const params = new URLSearchParams({
    postId: currentPostId,
    page,
    size: commentPageState.size,
    sort: 'oldest'
  });
  try {
    const response = await fetchJson(`/api/comments?${params.toString()}`);
    commentPageState = {
      page,
      size: response.size ?? commentPageState.size,
      hasNext: !response.last,
      items: reset ? response.content : commentPageState.items.concat(response.content),
      total: response.totalElements ?? response.content.length
    };
    renderCommentList(commentPageState.items, commentPageState.total);
    if (loadMoreCommentsButton) {
      loadMoreCommentsButton.disabled = !commentPageState.hasNext;
      loadMoreCommentsButton.textContent = commentPageState.hasNext ? '더 보기' : '마지막 댓글입니다';
    }
  } catch (err) {
    console.error(err);
    setStatus(loginFeedback, err.message || '댓글을 불러오지 못했습니다.', 'error');
  }
}

function renderCommentList(comments, total) {
  if (!commentListEl) return;
  commentListEl.innerHTML = '';
  const commentMap = new Map();
  comments.forEach((comment) => {
    commentMap.set(comment.id, { ...comment, children: [] });
  });
  const roots = [];
  commentMap.forEach((comment) => {
    if (comment.parentId && commentMap.has(comment.parentId)) {
      commentMap.get(comment.parentId).children.push(comment);
    } else {
      roots.push(comment);
    }
  });
  roots.forEach((comment) => commentListEl.appendChild(renderCommentItem(comment, 0)));
  if (commentCountEl) {
    commentCountEl.textContent = `${total ?? comments.length}개의 댓글`;
  }
}

function renderCommentItem(comment, depth) {
  const item = document.createElement('li');
  item.className = 'comment-item';
  if (depth > 0) {
    item.classList.add('comment-child');
  }
  const author = comment.authorNickname || comment.authorUsername || '익명';
  const parentBadge = comment.parentId
    ? `<span class="comment-parent">↳ ${comment.parentId}번 댓글</span>`
    : '';
  const actions = [];
  actions.push(`<button type="button" class="btn btn-text btn-reply" data-comment-id="${comment.id}" data-comment-author="${author}">답글</button>`);
  if (cachedUser && comment.authorUsername === cachedUser.username) {
    actions.push(`<button type="button" class="btn btn-text btn-delete-comment" data-comment-id="${comment.id}">삭제</button>`);
  }
  actions.push(`<button type="button" class="btn btn-text btn-report" data-report-type="COMMENT" data-report-id="${comment.id}" data-report-label="댓글 #${comment.id}">신고</button>`);
  item.innerHTML = `
    <strong>${author}</strong>
    <small>${formatDate(comment.createdAt)}</small>
    ${parentBadge}
    <p>${comment.content}</p>
    <div class="comment-actions">
      ${actions.join('')}
    </div>
  `;
  const children = comment.children || [];
  if (children.length) {
    const childList = document.createElement('ul');
    childList.className = 'comment-children';
    children.forEach((child) => childList.appendChild(renderCommentItem(child, depth + 1)));
    item.appendChild(childList);
  }
  return item;
}

function renderPostDetail(detail) {
  if (!detail) return;
  if (detailTitle) {
    detailTitle.textContent = detail.title;
  }
  if (postMetaEl) {
    const author = detail.authorNickname || detail.authorUsername || '익명';
    postMetaEl.textContent = `${author} · ${formatDate(detail.createdAt)} · ${detail.mainBoardName}/${detail.subBoardName}`;
  }
  if (reportPostButton) {
    reportPostButton.disabled = false;
    reportPostButton.dataset.targetId = detail.id;
    reportPostButton.dataset.targetLabel = detail.title || `게시글 #${detail.id}`;
  }
  if (postContentEl) {
    postContentEl.textContent = detail.content || '';
  }
  if (attachmentListEl) {
    attachmentListEl.innerHTML = '';
    if (detail.fileUrls && detail.fileUrls.length) {
      detail.fileUrls.forEach((url) => {
        const chip = document.createElement('a');
        chip.href = url;
        chip.target = '_blank';
        chip.rel = 'noopener';
        chip.className = 'attachment-chip';
        chip.textContent = url;
        attachmentListEl.appendChild(chip);
      });
    }
  }
  const comments = commentsPage?.content || detail.comments || [];
  if (commentListEl) {
    commentListEl.innerHTML = '';
    comments.forEach((comment) => {
      commentListEl.appendChild(renderCommentItem(comment));
    });
  }
  if (commentCountEl) {
    const total = commentPageState.total ?? comments.length;
    commentCountEl.textContent = `${total}개의 댓글`;
  }
  if (voteButton) {
    voteButton.disabled = false;
  }
  if (deletePostButton) {
    deletePostButton.disabled = false;
  }
  if (editPostButton) {
    editPostButton.disabled = false;
  }
}

function setReplyTarget(commentId, authorLabel) {
  replyTargetComment = { id: commentId };
  if (commentParentInput) {
    commentParentInput.value = commentId;
  }
  if (commentReplyLabel) {
    commentReplyLabel.textContent = `${authorLabel} 님에게 답글 작성`;
  }
  commentReplyTarget?.classList.remove('hidden');
  commentForm?.querySelector('textarea')?.focus();
}

function clearReplyTarget() {
  replyTargetComment = null;
  if (commentParentInput) {
    commentParentInput.value = '';
  }
  commentReplyTarget?.classList.add('hidden');
  if (commentReplyLabel) {
    commentReplyLabel.textContent = '';
  }
}

function setGalleryReplyTarget(commentId, authorLabel) {
  galleryReplyTarget = { id: commentId };
  if (galleryCommentParentInput) {
    galleryCommentParentInput.value = commentId;
  }
  if (galleryCommentReplyLabel) {
    galleryCommentReplyLabel.textContent = `${authorLabel} 님에게 답글 작성`;
  }
  galleryCommentReplyTarget?.classList.remove('hidden');
  galleryCommentForm?.querySelector('textarea')?.focus();
}

function clearGalleryReplyTarget() {
  galleryReplyTarget = null;
  if (galleryCommentParentInput) {
    galleryCommentParentInput.value = '';
  }
  galleryCommentReplyTarget?.classList.add('hidden');
  if (galleryCommentReplyLabel) {
    galleryCommentReplyLabel.textContent = '';
  }
}

async function deleteComment(commentId) {
  if (!commentId) return;
  if (!confirm('선택한 댓글을 삭제할까요?')) {
    return;
  }
  try {
    await fetchJson(`/api/comments/${commentId}`, { method: 'DELETE' });
    if (currentPostId) {
      await showPostDetail(currentPostId);
    }
  } catch (err) {
    setStatus(loginFeedback, err.message || '댓글 삭제에 실패했습니다.', 'error');
  }
}

async function submitGalleryComment(event) {
  event.preventDefault();
  if (!currentGalleryId || !galleryCommentForm) return;
  const formData = new FormData(galleryCommentForm);
  const content = formData.get('content');
  if (!content || !content.trim()) {
    return;
  }
  try {
    await fetchJson('/api/comments', {
      method: 'POST',
      body: JSON.stringify({
        postId: currentGalleryId,
        parentId: galleryReplyTarget ? galleryReplyTarget.id : null,
        content: content.trim()
      })
    });
    galleryCommentForm.reset();
    clearGalleryReplyTarget();
    await loadGalleryComments(true);
    setStatus(galleryModalStatus, '댓글이 등록되었습니다.', 'success');
  } catch (err) {
    setStatus(galleryModalStatus, err.message || '댓글 등록에 실패했습니다.', 'error');
  }
}

async function deleteGalleryComment(commentId) {
  if (!commentId) return;
  if (!confirm('선택한 댓글을 삭제할까요?')) {
    return;
  }
  try {
    await fetchJson(`/api/comments/${commentId}`, { method: 'DELETE' });
    await loadGalleryComments(true);
    setStatus(galleryModalStatus, '댓글이 삭제되었습니다.', 'success');
  } catch (err) {
    setStatus(galleryModalStatus, err.message || '댓글 삭제에 실패했습니다.', 'error');
  }
}

function resetPostForm() {
  if (!postForm) return;
  postForm.reset();
  const postIdInput = postForm.querySelector('[name="postId"]');
  if (postIdInput) {
    postIdInput.value = '';
  }
}

function populatePostForm(detail) {
  if (!postForm || !detail) return;
  postForm.querySelector('[name="postId"]').value = detail.id;
  postForm.querySelector('[name="title"]').value = detail.title;
  formMainBoard.value = detail.mainBoardName;
  updateSubBoardOptions(formMainBoard, formSubBoard);
  formSubBoard.value = detail.subBoardName;
  postForm.querySelector('[name="tabItem"]').value = detail.tabItem || '';
  postForm.querySelector('[name="content"]').value = detail.content;
  postForm.querySelector('[name="fileUrls"]').value = (detail.fileUrls || []).join('\n');
}

async function submitPostForm(event) {
  event.preventDefault();
  if (!postForm) return;
  const formData = new FormData(postForm);
  const payload = {
    title: formData.get('title'),
    content: formData.get('content'),
    mainBoardName: formData.get('mainBoardName'),
    subBoardName: formData.get('subBoardName'),
    tabItem: formData.get('tabItem') || '',
    fileUrls: parseFileInputs(formData.get('fileUrls'))
  };
  const postId = formData.get('postId');
  const method = postId ? 'PUT' : 'POST';
  const url = postId ? `/api/posts/${postId}` : '/api/posts';
  try {
    await fetchJson(url, { method, body: JSON.stringify(payload) });
    setStatus(signupFeedback, '', 'info');
    setStatus(loginFeedback, '게시글이 저장되었습니다.', 'success');
    resetPostForm();
    await loadBoardPosts();
  } catch (err) {
    setStatus(loginFeedback, err.message || '게시글 저장에 실패했습니다.', 'error');
  }
}

async function deleteCurrentPost() {
  if (!currentPostId) return;
  if (!confirm('선택한 게시글을 삭제할까요?')) {
    return;
  }
  try {
    await fetchJson(`/api/posts/${currentPostId}`, { method: 'DELETE' });
    setStatus(loginFeedback, '게시글이 삭제되었습니다.', 'success');
    currentPostId = null;
    currentPostDetail = null;
    if (postContentEl) postContentEl.innerHTML = '<p class="empty">게시글을 선택하면 상세 내용이 나타납니다.</p>';
    await loadBoardPosts();
  } catch (err) {
    setStatus(loginFeedback, err.message || '게시글 삭제에 실패했습니다.', 'error');
  }
}

async function voteCurrentPost() {
  if (!currentPostId) return;
  try {
    await fetchJson(`/api/posts/${currentPostId}/vote`, { method: 'POST' });
    setStatus(loginFeedback, '추천했습니다.', 'success');
    await showPostDetail(currentPostId);
    await loadBoardPosts();
  } catch (err) {
    setStatus(loginFeedback, err.message || '추천에 실패했습니다.', 'error');
  }
}

async function submitComment(event) {
  event.preventDefault();
  if (!currentPostId || !commentForm) return;
  const formData = new FormData(commentForm);
  const content = formData.get('content');
  if (!content || !content.trim()) {
    return;
  }
  try {
    await fetchJson('/api/comments', {
      method: 'POST',
      body: JSON.stringify({
        postId: currentPostId,
        parentId: replyTargetComment ? replyTargetComment.id : null,
        content: content.trim()
      })
    });
    commentForm.reset();
    clearReplyTarget();
    await showPostDetail(currentPostId);
  } catch (err) {
    setStatus(loginFeedback, err.message || '댓글 등록에 실패했습니다.', 'error');
  }
}

async function loadGallery() {
  if (!galleryList || !boardSections.length) {
    return;
  }
  setDefaultOption(gallerySubBoardSelect);
  const params = new URLSearchParams({ page: 0, size: 9, sort: 'latest' });
  const mainBoard = galleryMainBoardSelect?.value;
  if (mainBoard) {
    params.set('mainBoard', mainBoard);
  }
  const subBoard = gallerySubBoardSelect?.value;
  if (subBoard) {
    params.set('subBoard', subBoard);
  }
  try {
    const page = await fetchJson(`/api/gallery?${params.toString()}`);
    renderGallery(page?.content || []);
  } catch (err) {
    console.error(err);
    if (galleryFeedback) {
      setStatus(galleryFeedback, err.message || '갤러리를 불러오지 못했습니다.', 'error');
    }
  }
}

function renderGallery(posts) {
  if (!galleryList) return;
  galleryList.innerHTML = '';
  if (!posts.length) {
    galleryList.innerHTML = '<p class="empty">갤러리 게시물이 없습니다.</p>';
    return;
  }
  posts.forEach((post) => {
    const media = post.thumbnailUrls && post.thumbnailUrls.length ? post.thumbnailUrls[0] : null;
    const card = document.createElement('article');
    card.className = 'gallery-card';
    const meta = `${post.likeCount ?? 0} 추천 · ${post.commentCount ?? 0} 댓글`;
    if (media && media.match(/\.(mp4|webm|ogg)$/i)) {
      card.innerHTML = `
        <video src="${media}" controls preload="metadata"></video>
        <div class="gallery-card__body">
          <strong>${post.title}</strong>
          <p>${post.mainBoardName || ''} / ${post.subBoardName || ''}</p>
          <p class="gallery-card__meta">${post.authorNickname || post.authorUsername || '익명'} · ${formatDate(post.createdAt)} · ${meta}</p>
        </div>
      `;
    } else if (media) {
      card.innerHTML = `
        <img src="${media}" alt="${post.title}" loading="lazy" />
        <div class="gallery-card__body">
          <strong>${post.title}</strong>
          <p>${post.mainBoardName || ''} / ${post.subBoardName || ''}</p>
          <p class="gallery-card__meta">${post.authorNickname || post.authorUsername || '익명'} · ${formatDate(post.createdAt)} · ${meta}</p>
        </div>
      `;
    } else {
      card.innerHTML = `
        <div class="gallery-card__body">
          <strong>${post.title}</strong>
          <p>${post.mainBoardName || ''} / ${post.subBoardName || ''}</p>
          <p class="gallery-card__meta">${post.authorNickname || post.authorUsername || '익명'} · ${formatDate(post.createdAt)} · ${meta}</p>
        </div>
      `;
    }
    card.addEventListener('click', () => openGalleryModal(post.id));
    galleryList.appendChild(card);
  });
}

function renderGalleryModal(detail) {
  if (!detail) {
    return;
  }
  galleryModalTitle.textContent = detail.title || '제목 없음';
  galleryModalBoard.textContent = `${detail.mainBoardName || ''} / ${detail.subBoardName || ''}`;
  galleryModalMeta.textContent = `${detail.authorNickname || detail.authorUsername || '익명'} · ${formatDate(detail.createdAt)}`;
  galleryModalDescription.textContent = detail.content || '';
  const attachments = detail.attachments || [];
  galleryModalMedia.innerHTML = '';
  if (attachments.length) {
    const first = attachments[0];
    if (first.match(/\.(mp4|webm|ogg)$/i)) {
      const video = document.createElement('video');
      video.src = first;
      video.controls = true;
      video.preload = 'metadata';
      galleryModalMedia.appendChild(video);
    } else {
      const img = document.createElement('img');
      img.src = first;
      img.alt = detail.title || 'gallery media';
      galleryModalMedia.appendChild(img);
    }
  } else {
    const placeholder = document.createElement('div');
    placeholder.className = 'modal__placeholder';
    placeholder.textContent = '등록된 미디어가 없습니다.';
    galleryModalMedia.appendChild(placeholder);
  }
  galleryModalAttachments.innerHTML = '';
  attachments.forEach((url) => {
    const link = document.createElement('a');
    link.href = url;
    link.target = '_blank';
    link.rel = 'noopener';
    link.textContent = '첨부 보기';
    galleryModalAttachments.appendChild(link);
  });
  galleryModalStats.textContent = `추천 ${detail.likeCount ?? 0} · 댓글 ${detail.commentCount ?? 0} · 조회 ${detail.viewCount ?? 0}`;
  if (galleryModalReportButton) {
    galleryModalReportButton.dataset.targetId = detail.id;
    galleryModalReportButton.dataset.targetLabel = detail.title || `갤러리 #${detail.id}`;
  }
  galleryCommentState = { page: 0, size: 20, hasNext: true, items: [], total: detail.commentCount ?? 0 };
  renderGalleryComments(detail.comments || []);
}

async function openGalleryModal(postId) {
  if (!galleryModal) {
    window.open(`https://api.cms-community.com/post/detail/${postId}`, '_blank', 'noopener');
    return;
  }
  currentGalleryId = postId;
  galleryModal.classList.remove('hidden');
  document.body.classList.add('modal-open');
  setStatus(galleryModalStatus, '갤러리를 불러오는 중입니다...', 'info');
  try {
    const detail = await fetchJson(`/api/gallery/${postId}`);
    renderGalleryModal(detail);
    setStatus(galleryModalStatus, '', 'info');
    await loadGalleryComments(true);
  } catch (err) {
    setStatus(galleryModalStatus, err.message || '갤러리를 불러오지 못했습니다.', 'error');
  }
}

function closeGalleryModal() {
  if (!galleryModal) return;
  galleryModal.classList.add('hidden');
  document.body.classList.remove('modal-open');
  galleryModalMedia.innerHTML = '';
  galleryModalAttachments.innerHTML = '';
  galleryModalComments.innerHTML = '';
  currentGalleryId = null;
  clearGalleryReplyTarget();
}

function openReportModal(type, targetId, label) {
  if (!cachedUser) {
    setStatus(loginFeedback, '로그인이 필요합니다.', 'error');
    return;
  }
  if (!reportModal || !reportReasonInput) {
    setStatus(loginFeedback, '신고 기능을 사용할 수 없습니다.', 'error');
    return;
  }
  reportTarget = { type, targetId };
  reportModalTarget.textContent = label || '선택된 대상';
  reportReasonInput.value = '';
  setStatus(reportModalStatus, '신고 사유를 입력하세요.', 'info');
  reportModal.classList.remove('hidden');
  document.body.classList.add('modal-open');
  reportReasonInput.focus();
}

function closeReportModal() {
  if (!reportModal || !reportReasonInput) {
    return;
  }
  reportModal.classList.add('hidden');
  document.body.classList.remove('modal-open');
  reportTarget = null;
  reportReasonInput.value = '';
  setStatus(reportModalStatus, '', 'info');
}

async function submitReport(event) {
  event.preventDefault();
  if (!reportTarget) {
    setStatus(reportModalStatus, '신고 대상을 선택하지 못했습니다.', 'error');
    return;
  }
  if (!reportReasonInput) {
    setStatus(reportModalStatus, '신고 폼을 초기화하지 못했습니다.', 'error');
    return;
  }
  const reason = reportReasonInput.value?.trim();
  if (!reason) {
    setStatus(reportModalStatus, '신고 사유를 입력하세요.', 'error');
    return;
  }
  setStatus(reportModalStatus, '신고를 접수하는 중입니다...', 'info');
  try {
    await fetchJson('/api/reports', {
      method: 'POST',
      body: JSON.stringify({
        targetId: reportTarget.targetId,
        type: reportTarget.type,
        reason
      })
    });
    setStatus(reportModalStatus, '신고가 접수되었습니다.', 'success');
    setTimeout(closeReportModal, 1000);
  } catch (err) {
    setStatus(reportModalStatus, err.message || '신고 접수에 실패했습니다.', 'error');
  }
}

async function submitGalleryForm(event) {
  event.preventDefault();
  if (!galleryForm) return;
  const formData = new FormData(galleryForm);
  const manualUrls = parseFileInputs(formData.get('mediaUrl'));
  const attachments = [...galleryUploadedEntries.map((entry) => entry.url), ...manualUrls];
  if (!attachments.length) {
    setStatus(galleryFeedback, '최소 한 개 이상의 미디어를 업로드하거나 URL을 입력하세요.', 'error');
    return;
  }
  const payload = {
    title: formData.get('title'),
    content: formData.get('content'),
    mainBoardName: formData.get('mainBoardName'),
    subBoardName: formData.get('subBoardName'),
    tabItem: formData.get('tabItem') || '',
    attachmentUrls: attachments
  };
  try {
    await fetchJson('/api/gallery', { method: 'POST', body: JSON.stringify(payload) });
    setStatus(galleryFeedback, '갤러리에 게시되었습니다.', 'success');
    galleryForm.reset();
    resetGalleryUploads();
    loadGallery();
  } catch (err) {
    setStatus(galleryFeedback, err.message || '갤러리 게시에 실패했습니다.', 'error');
  }
}

function showSection(target) {
  sections.forEach((section) => {
    section.classList.toggle('hidden', section.dataset.section !== target);
  });
  navLinks.forEach((link) => {
    link.classList.toggle('active', link.dataset.target === target);
  });
  if (target === 'auth') {
    switchAuthTab('find-id');
  }
  if (target === 'board') {
    loadBoardPosts();
  }
  if (target === 'gallery') {
    loadGallery();
  }
  if (target === 'notice') {
    loadNotices();
  }
  if (target === 'verify') {
    // handled by openVerifySection
  }
  if (target === 'account' && cachedUser) {
    loadProfile(true);
  }
}

async function loadHomeData() {
  try {
    const response = await fetch(`${API_BASE}/api/home`, { credentials: 'include' });
    if (!response.ok) {
      throw new Error('홈 데이터를 불러오지 못했습니다.');
    }
    const data = await response.json();
    boardDisplayNames = {};
    renderBoards(data.boards);
    mapBoards(data.boards);
    renderPostList(data.recentPosts, latestList, latestEmpty);
    const filteredPopular = (data.popularPosts || []).filter((post) => (post.voteCount ?? 0) >= 10);
    if (popularEmpty) {
      popularEmpty.textContent = '추천 10개 이상인 갤러리만 표시됩니다.';
    }
    renderPostList(filteredPopular, popularList, popularEmpty);
    if (!boardInitialized) {
      boardInitialized = true;
    }
  } catch (err) {
    console.error(err);
    latestEmpty.textContent = err.message;
    latestEmpty.hidden = false;
    popularEmpty.textContent = err.message;
    popularEmpty.hidden = false;
  }
}

async function loadNotices() {
  if (!noticeTableBody) {
    return;
  }
  noticeStatus && (noticeStatus.textContent = '공지사항을 불러오는 중입니다...');
  noticeTableBody.innerHTML = '';
  try {
    const response = await fetchJson('/api/notices?size=12', { method: 'GET' });
    renderNotices(response?.data || response?.items || response || []);
    noticeStatus && (noticeStatus.textContent = noticeCache.length ? '' : '등록된 공지사항이 없습니다.');
  } catch (err) {
    noticeTableBody.innerHTML = '';
    noticeStatus && (noticeStatus.textContent = err.message || '공지사항을 불러오지 못했습니다.');
  }
}

async function loadCurrentUser() {
  try {
    const response = await fetch(`${API_BASE}/api/auth/me`, { credentials: 'include' });
    if (!response.ok) {
      throw new Error();
    }
    const user = await response.json();
    cachedUser = user || null;
    persistSharedAuthUser(cachedUser);
    updateAuthUI(cachedUser);
  } catch {
    cachedUser = null;
    persistSharedAuthUser(null);
    updateAuthUI(null);
  }
  updateAccountAccessState();
}

function handleLogin() {
  if (!loginForm) return;
  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const formData = new FormData(loginForm);
    const payload = {
      username: formData.get('username'),
      password: formData.get('password')
    };
    setStatus(loginFeedback, '로그인 중입니다...');
    try {
      const result = await fetchJson('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) });
      setStatus(loginFeedback, '로그인에 성공했습니다.', 'success');
      loginForm.reset();
      cachedUser = result || null;
      updateAuthUI(cachedUser);
      updateAccountAccessState();
      persistSharedAuthUser(cachedUser);
      await loadCurrentUser();
      await loadHomeData();
      showSection('home');
    } catch (err) {
      const msg = (err && err.message) || '';
      const lowered = msg.toLowerCase();
      if (lowered.includes('disabled') || lowered.includes('blocked') || lowered.includes('정지') || lowered.includes('차단')) {
        alert('계정이 차단되어 로그인할 수 없습니다. 관리자에게 문의하세요.');
        setStatus(loginFeedback, '차단된 계정입니다. 문의하세요.', 'error');
      } else {
        setStatus(loginFeedback, msg || '로그인에 실패했습니다.', 'error');
      }
    }
  });
}

function handleSignup() {
  if (!signupForm) return;
  signupForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const formData = new FormData(signupForm);
    const usernameValue = formData.get('username');
    setStatus(signupFeedback, '회원가입 처리 중입니다...');
    try {
      await fetchFormData('/api/auth/signup', formData);
      setStatus(signupFeedback, '회원가입이 완료되었습니다. 이메일 인증을 진행해 주세요.', 'success');
      signupForm.reset();
      openVerifySection(usernameValue || '');
    } catch (err) {
      const message = err.message?.includes('Username') ? '이미 사용 중인 아이디입니다.' : err.message;
      setStatus(signupFeedback, message || '회원가입에 실패했습니다.', 'error');
    }
  });
}

function handleLogout() {
  const buttons = [heroLogoutButton, headerLogoutButton].filter(Boolean);
  if (!buttons.length) return;
  buttons.forEach((button) => {
    button.addEventListener('click', async () => {
      try {
        await fetchJson('/api/auth/logout', { method: 'POST' });
        cachedUser = null;
        updateAuthUI(null);
        setStatus(loginFeedback, '로그아웃되었습니다.', 'success');
        updateAccountAccessState();
        persistSharedAuthUser(null);
        await loadHomeData();
      } catch (err) {
        setStatus(loginFeedback, err.message || '로그아웃에 실패했습니다.', 'error');
      }
    });
  });
}

async function submitProfileUpdate(event) {
  event.preventDefault();
  if (!cachedUser) {
    setStatus(profileFeedback, '로그인이 필요합니다.', 'error');
    return;
  }
  const formData = new FormData(profileForm);
  setStatus(profileFeedback, '프로필을 저장하는 중입니다...');
  try {
    await fetchFormData('/api/auth/profile', formData, 'PUT');
    setStatus(profileFeedback, '프로필이 업데이트되었습니다.', 'success');
    await loadCurrentUser();
  } catch (err) {
    setStatus(profileFeedback, err.message || '프로필 수정에 실패했습니다.', 'error');
  }
}

async function submitPasswordChange(event) {
  event.preventDefault();
  if (!cachedUser) {
    setStatus(passwordFeedback, '로그인이 필요합니다.', 'error');
    return;
  }
  const formData = new FormData(passwordForm);
  const payload = {
    currentPassword: formData.get('currentPassword'),
    newPassword: formData.get('newPassword'),
    confirmNewPassword: formData.get('confirmNewPassword')
  };
  setStatus(passwordFeedback, '비밀번호를 변경하는 중입니다...');
  try {
    await fetchJson('/api/auth/password/change', { method: 'POST', body: JSON.stringify(payload) });
    setStatus(passwordFeedback, '비밀번호가 변경되었습니다.', 'success');
    passwordForm.reset();
  } catch (err) {
    setStatus(passwordFeedback, err.message || '비밀번호 변경에 실패했습니다.', 'error');
  }
}

async function handleAccountDeletion() {
  if (!cachedUser) {
    setStatus(deleteFeedback, '로그인이 필요합니다.', 'error');
    return;
  }
  const confirmed = window.confirm('정말로 회원 탈퇴를 진행하시겠습니까? 이 작업은 되돌릴 수 없습니다.');
  if (!confirmed) {
    return;
  }
  setStatus(deleteFeedback, '회원 탈퇴를 진행 중입니다...');
  try {
    await fetchJson('/api/auth/me', { method: 'DELETE' });
    setStatus(deleteFeedback, '회원 탈퇴가 완료되었습니다.', 'success');
    cachedUser = null;
    updateAuthUI(null);
    updateAccountAccessState();
    persistSharedAuthUser(null);
    await loadHomeData();
  } catch (err) {
    setStatus(deleteFeedback, err.message || '회원 탈퇴에 실패했습니다.', 'error');
  }
}

function updateAuthUI(user) {
  const loggedIn = Boolean(user);
  const displayName = user ? user.nickname || user.username || '사용자' : '';
  const roleLabel = user && user.role ? user.role : '회원';
  if (welcomeMessage) {
    welcomeMessage.textContent = loggedIn ? `${displayName}님, 환영합니다!` : '';
  }
  if (heroLogoutButton) {
    heroLogoutButton.hidden = !loggedIn;
  }
  if (headerLoginButton) {
    headerLoginButton.classList.toggle('hidden', loggedIn);
  }
  if (headerSignupButton) {
    headerSignupButton.classList.toggle('hidden', loggedIn);
  }
  if (headerLogoutButton) {
    headerLogoutButton.hidden = !loggedIn;
  }
  if (userBadge) {
    userBadge.classList.toggle('hidden', !loggedIn);
  }
  if (userNameLabel) {
    userNameLabel.textContent = loggedIn ? displayName : '';
  }
  if (userRoleLabel) {
    userRoleLabel.textContent = loggedIn ? roleLabel : '';
  }
  if (userAvatar) {
    if (loggedIn) {
      const initial = displayName?.charAt(0)?.toUpperCase() || 'U';
      userAvatar.textContent = initial;
      if (user?.profilePictureUrl) {
        userAvatar.style.backgroundImage = `url(${user.profilePictureUrl})`;
        userAvatar.classList.add('has-image');
      } else {
        userAvatar.style.backgroundImage = 'none';
        userAvatar.classList.remove('has-image');
      }
    } else {
      userAvatar.textContent = '';
      userAvatar.style.backgroundImage = 'none';
      userAvatar.classList.remove('has-image');
    }
  }
}

function scrollToLoginForm() {
  showSection('login');
  if (!loginForm) return;
  loginForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
  try {
    loginForm.querySelector('input[name="username"]')?.focus();
  } catch {
    // ignore
  }
}

function scrollToSignupForm() {
  showSection('signup');
  if (!signupForm) return;
  signupForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
  try {
    signupForm.querySelector('input[name="username"]')?.focus();
  } catch {
    // ignore
  }
}

function openVerifySection(username = '') {
  showSection('verify');
  if (!verifyEmailForm) return;
  const usernameInput = verifyEmailForm.querySelector('input[name="username"]');
  if (usernameInput) {
    usernameInput.value = username;
  }
  const codeInput = verifyEmailForm.querySelector('input[name="code"]');
  if (codeInput) {
    codeInput.value = '';
  }
  verifyEmailForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
  setStatus(verifyFeedback, '가입 시 받은 인증 코드를 입력하세요.', 'info');
}

function openAccountSection() {
  showSection('account');
  accountPanel?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  if (cachedUser) {
    loadProfile(true);
  } else {
    scrollToLoginForm();
  }
}

function openAccountSupport() {
  showSection('auth');
  switchAuthTab('find-id');
  document.querySelector('[data-section="auth"]')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function switchAuthSubsection() {}

function switchAuthTab(target) {
  authTabs.forEach((tab) => {
    tab.classList.toggle('active', tab.dataset.auth === target);
  });
  authPanels.forEach((panel) => {
    panel.classList.toggle('hidden', panel.dataset.authPanel !== target);
  });
}

async function submitVerificationCode(event) {
  event.preventDefault();
  const formData = new FormData(verifyEmailForm);
  const username = formData.get('username');
  const code = formData.get('code');
  if (!username || !code) {
    setStatus(verifyFeedback, '아이디와 인증 코드를 입력하세요.', 'error');
    return;
  }
  setStatus(verifyFeedback, '인증 중입니다...');
  try {
    await fetchJson('/api/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ username, code })
    });
    setStatus(verifyFeedback, '이메일 인증이 완료되었습니다. 로그인해 주세요.', 'success');
    verifyEmailForm.reset();
  } catch (err) {
    setStatus(verifyFeedback, err.message || '인증에 실패했습니다.', 'error');
  }
}

async function resendVerificationCode() {
  if (!verifyEmailForm) return;
  const username = new FormData(verifyEmailForm).get('username');
  if (!username) {
    setStatus(verifyFeedback, '아이디를 먼저 입력하세요.', 'error');
    return;
  }
  setStatus(verifyFeedback, '인증 코드를 다시 전송 중입니다...');
  try {
    await fetchJson('/api/auth/resend-verification-code', {
      method: 'POST',
      body: JSON.stringify({ username })
    });
    setStatus(verifyFeedback, '인증 코드를 다시 보냈습니다. 이메일을 확인하세요.', 'success');
  } catch (err) {
    setStatus(verifyFeedback, err.message || '재전송에 실패했습니다.', 'error');
  }
}

async function submitFindId(event) {
  event.preventDefault();
  const email = new FormData(findIdForm).get('email');
  setStatus(findIdFeedback, '아이디를 찾는 중입니다...');
  try {
    const response = await fetchJson('/api/auth/find-id', {
      method: 'POST',
      body: JSON.stringify({ email })
    });
    const username = response?.username;
    setStatus(findIdFeedback, username ? `아이디: ${username}` : '해당 이메일로 등록된 아이디가 없습니다.', username ? 'success' : 'error');
  } catch (err) {
    setStatus(findIdFeedback, err.message || '아이디 찾기에 실패했습니다.', 'error');
  }
}

async function requestPasswordResetCode(event) {
  event.preventDefault();
  const formData = new FormData(requestResetCodeForm);
  const payload = {
    username: formData.get('username'),
    email: formData.get('email')
  };
  setStatus(forgotFeedback, '인증 코드를 전송 중입니다...');
  try {
    await fetchJson('/api/auth/find-password/request-code', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    setStatus(forgotFeedback, '인증 코드가 이메일로 전송되었습니다.', 'success');
    requestResetCodeForm.classList.add('hidden');
    verifyResetCodeForm.classList.remove('hidden');
    resetPasswordForm.dataset.username = payload.username;
  } catch (err) {
    setStatus(forgotFeedback, err.message || '코드 요청에 실패했습니다.', 'error');
  }
}

async function verifyResetCode(event) {
  event.preventDefault();
  const code = new FormData(verifyResetCodeForm).get('code');
  const username = resetPasswordForm.dataset.username;
  setStatus(forgotFeedback, '코드를 확인 중입니다...');
  try {
    await fetchJson('/api/auth/find-password/verify-code', {
      method: 'POST',
      body: JSON.stringify({ username, code })
    });
    setStatus(forgotFeedback, '코드가 확인되었습니다. 새 비밀번호를 입력하세요.', 'success');
    verifyResetCodeForm.classList.add('hidden');
    resetPasswordForm.classList.remove('hidden');
    resetPasswordForm.dataset.code = code;
  } catch (err) {
    setStatus(forgotFeedback, err.message || '코드 검증에 실패했습니다.', 'error');
  }
}

async function submitPasswordReset(event) {
  event.preventDefault();
  const formData = new FormData(resetPasswordForm);
  const newPassword = formData.get('newPassword');
  const confirmPassword = formData.get('confirmPassword');
  if (newPassword !== confirmPassword) {
    setStatus(forgotFeedback, '새 비밀번호가 일치하지 않습니다.', 'error');
    return;
  }
  const payload = {
    username: resetPasswordForm.dataset.username,
    verificationCode: resetPasswordForm.dataset.code,
    newPassword,
    confirmPassword
  };
  setStatus(forgotFeedback, '비밀번호를 재설정 중입니다...');
  try {
    await fetchJson('/api/auth/find-password/reset', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    setStatus(forgotFeedback, '비밀번호가 재설정되었습니다. 로그인해 주세요.', 'success');
    requestResetCodeForm.classList.remove('hidden');
    verifyResetCodeForm.classList.add('hidden');
    resetPasswordForm.classList.add('hidden');
    resetPasswordForm.reset();
  } catch (err) {
    setStatus(forgotFeedback, err.message || '비밀번호 재설정에 실패했습니다.', 'error');
  }
}
function registerEventHandlers() {
  if (searchButton) {
    searchButton.addEventListener('click', loadBoardPosts);
  }
  mainBoardSelect?.addEventListener('change', () => {
    updateSubBoardOptions(mainBoardSelect, subBoardSelect);
    loadBoardPosts();
  });
  subBoardSelect?.addEventListener('change', loadBoardPosts);
  tabSelect?.addEventListener('change', loadBoardPosts);
  searchKeywordInput?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadBoardPosts();
    }
  });
  formMainBoard?.addEventListener('change', () => updateSubBoardOptions(formMainBoard, formSubBoard));
  galleryMainBoardSelect?.addEventListener('change', () => {
    updateSubBoardOptions(galleryMainBoardSelect, gallerySubBoardSelect);
    loadGallery();
  });
  gallerySubBoardSelect?.addEventListener('change', loadGallery);
  postForm?.addEventListener('submit', submitPostForm);
  resetPostFormBtn?.addEventListener('click', resetPostForm);
  voteButton?.addEventListener('click', voteCurrentPost);
  deletePostButton?.addEventListener('click', deleteCurrentPost);
  editPostButton?.addEventListener('click', () => populatePostForm(currentPostDetail));
  commentForm?.addEventListener('submit', submitComment);
  loadMoreCommentsButton?.addEventListener('click', () => loadComments(false));
  galleryForm?.addEventListener('submit', submitGalleryForm);
  galleryUploadInput?.addEventListener('change', handleGalleryFileSelect);
  galleryUploadList?.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const button = target?.closest('button[data-index]');
    if (button && button.dataset.index) {
      removeGalleryUpload(Number(button.dataset.index));
    }
  });
  commentListEl?.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    const reportBtn = target.closest('[data-report-type="COMMENT"]');
    if (reportBtn) {
      const id = reportBtn.dataset.reportId;
      const label = reportBtn.dataset.reportLabel || `댓글 #${id}`;
      openReportModal('COMMENT', Number(id), label);
      return;
    }
    const replyBtn = target.closest('.btn-reply');
    if (replyBtn) {
      const id = Number(replyBtn.dataset.commentId);
      const author = replyBtn.dataset.commentAuthor || '댓글';
      setReplyTarget(id, author);
      return;
    }
    const deleteBtn = target.closest('.btn-delete-comment');
    if (deleteBtn) {
      deleteComment(Number(deleteBtn.dataset.commentId));
    }
  });
  galleryModalComments?.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const button = target?.closest('[data-report-type="COMMENT"]');
    if (button) {
      const id = button.dataset.reportId;
      openReportModal('COMMENT', Number(id), button.dataset.reportLabel || `댓글 #${id}`);
    }
  });
  reportPostButton?.addEventListener('click', () => {
    const id = reportPostButton.dataset.targetId;
    if (!id) return;
    const label = reportPostButton.dataset.targetLabel || `게시글 #${id}`;
    openReportModal('POST', Number(id), label);
  });
  galleryModalReportButton?.addEventListener('click', () => {
    if (!currentGalleryId) return;
    const label = galleryModalReportButton.dataset.targetLabel || `갤러리 #${currentGalleryId}`;
    openReportModal('POST', currentGalleryId, label);
  });
  closeGalleryModalButton?.addEventListener('click', closeGalleryModal);
  galleryModal?.addEventListener('click', (event) => {
    if (event.target === galleryModal) {
      closeGalleryModal();
    }
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && galleryModal && !galleryModal.classList.contains('hidden')) {
      closeGalleryModal();
    }
    if (event.key === 'Escape' && reportModal && !reportModal.classList.contains('hidden')) {
      closeReportModal();
    }
  });
  reportForm?.addEventListener('submit', submitReport);
  closeReportModalButton?.addEventListener('click', closeReportModal);
  cancelReportButton?.addEventListener('click', closeReportModal);
  reportModal?.addEventListener('click', (event) => {
    if (event.target === reportModal) {
      closeReportModal();
    }
  });
  cancelReplyButton?.addEventListener('click', clearReplyTarget);
  refreshGalleryBtn?.addEventListener('click', loadGallery);
  navLinks.forEach((link) => {
    link.addEventListener('click', () => showSection(link.dataset.target));
  });
  headerLoginButton?.addEventListener('click', scrollToLoginForm);
  headerSignupButton?.addEventListener('click', scrollToSignupForm);

  authTabs.forEach((tab) => {
    tab.addEventListener('click', () => switchAuthTab(tab.dataset.auth));
  });
  verifyEmailForm?.addEventListener('submit', submitVerificationCode);
  resendCodeButton?.addEventListener('click', resendVerificationCode);
  findIdForm?.addEventListener('submit', submitFindId);
  requestResetCodeForm?.addEventListener('submit', requestPasswordResetCode);
  verifyResetCodeForm?.addEventListener('submit', verifyResetCode);
  resetPasswordForm?.addEventListener('submit', submitPasswordReset);
  profileForm?.addEventListener('submit', submitProfileUpdate);
  passwordForm?.addEventListener('submit', submitPasswordChange);
  deleteAccountButton?.addEventListener('click', handleAccountDeletion);
  refreshNoticesBtn?.addEventListener('click', loadNotices);
  noticeSearchForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    applyNoticeFilter();
  });
  openAccountSupportButton?.addEventListener('click', openAccountSupport);
  loginSupportButton?.addEventListener('click', openAccountSupport);
  userBadge?.addEventListener('click', openAccountSection);
  userBadge?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openAccountSection();
    }
  });
  document.querySelectorAll('.chip[data-target]').forEach((chip) => {
    chip.addEventListener('click', () => showSection(chip.dataset.target));
  });
}

function initNavigation() {
  showSection('home');
}

function renderGalleryInitialState() {
  if (galleryList) {
    galleryList.innerHTML = '<p class="empty">갤러리 게시물이 없습니다.</p>';
  }
}

function renderPostInitialState() {
  if (postContentEl) {
    postContentEl.innerHTML = '<p class="empty">게시글을 선택하면 상세 내용이 나타납니다.</p>';
  }
}

renderGalleryInitialState();
renderPostInitialState();
registerEventHandlers();
initNavigation();
updateAccountAccessState();

loadHomeData();
loadCurrentUser();
handleLogin();
handleSignup();
handleLogout();
loadNotices();

if (cognitoButton) {
  cognitoButton.addEventListener('click', () => window.location.assign(COGNITO_LOGIN_URL));
}
document.querySelectorAll('.cognito-login').forEach((btn) => {
  btn.addEventListener('click', () => window.location.assign(COGNITO_LOGIN_URL));
});
async function loadGalleryComments(reset = false) {
  if (!currentGalleryId) {
    return;
  }
  const page = reset ? 0 : galleryCommentState.page + 1;
  const params = new URLSearchParams({
    postId: currentGalleryId,
    page,
    size: galleryCommentState.size,
    sort: 'oldest'
  });
  try {
    const response = await fetchJson(`/api/comments?${params.toString()}`);
    galleryCommentState = {
      page,
      size: response.size ?? galleryCommentState.size,
      hasNext: !response.last,
      items: reset ? response.content : galleryCommentState.items.concat(response.content),
      total: response.totalElements ?? response.content.length
    };
    renderGalleryComments(galleryCommentState.items);
    if (galleryLoadMoreCommentsButton) {
      galleryLoadMoreCommentsButton.disabled = !galleryCommentState.hasNext;
      galleryLoadMoreCommentsButton.textContent = galleryCommentState.hasNext ? '더 보기' : '마지막 댓글입니다';
    }
  } catch (err) {
    setStatus(galleryModalStatus, err.message || '댓글을 불러오지 못했습니다.', 'error');
  }
}

function renderGalleryComments(comments) {
  if (!galleryModalComments) return;
  galleryModalComments.innerHTML = '';
  const commentMap = new Map();
  comments.forEach((comment) => {
    commentMap.set(comment.id, { ...comment, children: [] });
  });
  const roots = [];
  commentMap.forEach((comment) => {
    if (comment.parentId && commentMap.has(comment.parentId)) {
      commentMap.get(comment.parentId).children.push(comment);
    } else {
      roots.push(comment);
    }
  });
  roots.forEach((comment) => galleryModalComments.appendChild(renderGalleryCommentItem(comment, 0)));
}

function renderGalleryCommentItem(comment, depth) {
  const item = document.createElement('li');
  item.className = 'comment-item';
  if (depth > 0) {
    item.classList.add('comment-child');
  }
  const author = comment.authorNickname || comment.authorUsername || '익명';
  const parentBadge = comment.parentId
    ? `<span class="comment-parent">↳ ${comment.parentId}번 댓글</span>`
    : '';
  const actions = [];
  actions.push(`<button type="button" class="btn btn-text btn-gallery-reply" data-comment-id="${comment.id}" data-comment-author="${author}">답글</button>`);
  if (cachedUser && comment.authorUsername === cachedUser.username) {
    actions.push(`<button type="button" class="btn btn-text btn-gallery-delete" data-comment-id="${comment.id}">삭제</button>`);
  }
  actions.push(`<button type="button" class="btn btn-text btn-report" data-report-type="COMMENT" data-report-id="${comment.id}" data-report-label="댓글 #${comment.id}">신고</button>`);
  item.innerHTML = `
    <strong>${author}</strong>
    <small>${formatDate(comment.createdAt)}</small>
    ${parentBadge}
    <p>${comment.content}</p>
    <div class="comment-actions">
      ${actions.join('')}
    </div>
  `;
  const children = comment.children || [];
  if (children.length) {
    const childList = document.createElement('ul');
    childList.className = 'comment-children';
    children.forEach((child) => childList.appendChild(renderGalleryCommentItem(child, depth + 1)));
    item.appendChild(childList);
  }
  return item;
}
