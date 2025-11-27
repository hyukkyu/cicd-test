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

const DEFAULT_GALLERY_TAB = '전체';

const normalizeTabName = (tab) => {
  const normalized = (tab || '').trim();
  return normalized || DEFAULT_GALLERY_TAB;
};

const listState = {
  main: null,
  sub: null,
  searchType: 'all',
  keyword: '',
  sort: 'latest',
  page: 0,
  size: 12,
  hasNext: true,
  items: [],
  tab: DEFAULT_GALLERY_TAB
};

runWhenReady(initGalleryListPage);

function initGalleryListPage() {
  const params = new URLSearchParams(window.location.search);
  listState.main = params.get('main');
  listState.sub = params.get('sub');
  listState.tab = normalizeTabName(params.get('tab'));
  if (!listState.main || !listState.sub) {
    setStatus(document.getElementById('galleryStatus'), '대메뉴에서 서브 카테고리를 선택해 이동해 주세요.', 'error');
    document.getElementById('galleryList').innerHTML = '<p class="empty">선택된 카테고리가 없습니다.</p>';
    return;
  }
  updateHeroSection();
  const category = GALLERY_CONFIG.find((c) => c.key === listState.main);
  renderTabFilters(category);
  bindListControls();
  loadGallery(true);
}

function bindListControls() {
  const searchForm = document.getElementById('gallerySearchForm');
  const keywordInput = document.getElementById('searchKeyword');
  const searchTypeSelect = document.getElementById('searchType');
  const uploadBtn = document.getElementById('listUploadButton');
  const backBtn = document.getElementById('listBackButton');
  const loadMoreBtn = document.getElementById('loadMoreGallery');

  searchForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    listState.keyword = keywordInput?.value?.trim() || '';
    listState.searchType = searchTypeSelect?.value || 'all';
    loadGallery(true);
  });

  if (keywordInput) keywordInput.value = listState.keyword;
  if (searchTypeSelect) searchTypeSelect.value = listState.searchType;

  uploadBtn?.addEventListener('click', () => {
    const search = new URLSearchParams({ main: listState.main, sub: listState.sub });
    if (listState.tab && listState.tab !== DEFAULT_GALLERY_TAB) {
      search.set('tab', listState.tab);
    }
    window.location.href = `gallery-upload.html?${search.toString()}`;
  });
  backBtn?.addEventListener('click', () => {
    window.location.href = `gallery.html?main=${encodeURIComponent(listState.main)}`;
  });
  loadMoreBtn?.addEventListener('click', () => loadGallery(false));
}

function updateHeroSection() {
  const heroTitle = document.getElementById('listHeroTitle');
  const heroDescription = document.getElementById('listHeroDescription');
  const panelEyebrow = document.getElementById('listPanelEyebrow');
  const panelTitle = document.getElementById('listPanelTitle');
  const category = GALLERY_CONFIG.find((c) => c.key === listState.main);
  const sub = category?.subBoards?.find((s) => s.value === listState.sub);
  const mainTitle = category?.title || listState.main || '갤러리';
  const subTitle = sub?.text || listState.sub || '';
  heroTitle.textContent = subTitle ? `${mainTitle} - ${subTitle}` : mainTitle;
  heroDescription.textContent = category?.description || '관심 있는 작품을 탐색해 보세요.';
  panelEyebrow.textContent = `${mainTitle}`;
  panelTitle.textContent = subTitle ? `${subTitle} 작품 목록` : '작품 목록';
}

function renderTabFilters(category) {
  const container = document.getElementById('listTabChips');
  if (!container) return;
  const rawTabs = [DEFAULT_GALLERY_TAB, ...(category?.tabs || [])];
  const seen = new Set();
  const tabs = [];
  rawTabs.forEach((tab) => {
    const normalized = normalizeTabName(tab);
    if (!seen.has(normalized)) {
      seen.add(normalized);
      tabs.push(normalized);
    }
  });
  if (listState.tab && !tabs.includes(listState.tab)) {
    tabs.push(listState.tab);
  }
  container.innerHTML = '';
  tabs.forEach((tab) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `chip ${listState.tab === tab ? 'active' : ''}`;
    button.textContent = tab;
    button.addEventListener('click', () => {
      const nextTab = normalizeTabName(tab);
      if (listState.tab === nextTab) return;
      listState.tab = nextTab;
      container.querySelectorAll('.chip').forEach((chip) => chip.classList.toggle('active', chip === button));
      loadGallery(true);
    });
    container.appendChild(button);
  });
}

async function loadGallery(reset) {
  const statusEl = document.getElementById('galleryStatus');
  const listEl = document.getElementById('galleryList');
  if (!listState.main || !listState.sub) {
    setStatus(statusEl, '카테고리가 지정되지 않았습니다.', 'error');
    return;
  }
  setStatus(statusEl, '갤러리를 불러오는 중입니다...', 'info');
  const page = reset ? 0 : listState.page + 1;
  const sortParam = listState.tab === '인기' ? 'popular' : 'latest';
  const params = new URLSearchParams({
    page,
    size: listState.size,
    mainBoard: listState.main,
    subBoard: listState.sub,
    sort: sortParam,
    searchType: listState.searchType
  });
  if (listState.keyword) {
    params.set('kw', listState.keyword);
  }
  if (listState.tab && listState.tab !== DEFAULT_GALLERY_TAB) {
    params.set('tab', listState.tab);
  }
  try {
    const response = await fetchJson(`/api/gallery?${params.toString()}`);
    listState.page = page;
    listState.hasNext = !response.last;
    listState.items = reset ? response.content : listState.items.concat(response.content);
    renderGalleryCards(listState.items, listEl);
    const loadMore = document.getElementById('loadMoreGallery');
    if (loadMore) {
      loadMore.disabled = !listState.hasNext;
      loadMore.textContent = listState.hasNext ? '더 보기' : '마지막 페이지입니다';
    }
    setStatus(statusEl, listState.items.length ? '' : '등록된 작품이 없습니다.', listState.items.length ? 'info' : 'error');
  } catch (error) {
    setStatus(statusEl, error.message || '갤러리를 불러오지 못했습니다.', 'error');
    if (reset && listEl) {
      listEl.innerHTML = '<p class="empty">데이터를 불러오지 못했습니다.</p>';
    }
  }
}

function renderGalleryCards(items, container) {
  if (!container) return;
  container.innerHTML = '';
  if (!items.length) {
    container.innerHTML = '<div class="gallery-table__empty">등록된 작품이 없습니다.</div>';
    return;
  }
  const fragment = document.createDocumentFragment();
  items.forEach((item) => {
    const hasAttachment = Boolean(
      (item.thumbnailUrls && item.thumbnailUrls.length) || (item.attachmentUrls && item.attachmentUrls.length)
    );
    const attachmentMarkup = hasAttachment
      ? `<span class="gallery-table__attachment" aria-label="첨부 파일 있음" role="img">
          <img src="icon/icon-attachment.svg" alt="" aria-hidden="true" class="gallery-table__attachment-icon" />
        </span>`
      : '';
    const author = item.authorNickname || item.authorUsername || '익명';
    const tabLabel = item.tabItem ? `<span class="gallery-table__tag">${item.tabItem}</span>` : '';
    const createdDate = item.createdAt ? new Date(item.createdAt).toLocaleDateString('ko-KR') : '-';
    const row = document.createElement('article');
    row.className = 'gallery-table__row';
    row.innerHTML = `
      <div class="gallery-table__cell gallery-table__cell--info">
        <p class="gallery-table__category">${item.mainBoardName} / ${item.subBoardName || '-'}</p>
        <div class="gallery-table__title-row">
          <h3>${item.title}</h3>
          ${attachmentMarkup}
        </div>
        ${tabLabel ? `<div class="gallery-table__meta">${tabLabel}</div>` : ''}
      </div>
      <div class="gallery-table__cell gallery-table__cell--author">
        <span class="gallery-table__author-name">${author}</span>
      </div>
      <div class="gallery-table__cell gallery-table__cell--stats">
        <span>추천 ${item.likeCount ?? 0}</span>
        <span>댓글 ${item.commentCount ?? 0}</span>
        <span>조회 ${item.viewCount ?? 0}</span>
      </div>
      <div class="gallery-table__cell gallery-table__cell--date">
        ${createdDate}
      </div>
    `;
    row.addEventListener('click', () => {
      window.location.href = `gallery-detail.html?id=${item.id}`;
    });
    fragment.appendChild(row);
  });
  container.appendChild(fragment);
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
