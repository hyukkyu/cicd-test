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

const listState = {
  main: null,
  sub: null,
  tab: '전체',
  page: 0,
  size: 12,
  hasNext: true,
  items: []
};
let hasGalleryListSection = false;

runWhenReady(initGalleryPage);

function initGalleryPage() {
  const params = new URLSearchParams(window.location.search);
  listState.main = params.get('main');
  listState.sub = params.get('sub');
  listState.tab = params.get('tab') || '전체';
  hasGalleryListSection = Boolean(document.getElementById('galleryList'));

  renderMainCategories();
  if (listState.main) {
    renderCategoryIntro(listState.main);
    if (hasGalleryListSection) {
      renderTabs(listState.main);
    }
  } else {
    renderCategoryIntro(null);
  }
  if (hasGalleryListSection) {
    document.getElementById('loadMoreGallery')?.addEventListener('click', () => loadGallery(false));
    loadGallery(true);
  }
}

function renderMainCategories() {
  const grid = document.getElementById('mainCategoryGrid');
  if (!grid) return;
  grid.innerHTML = '';
  GALLERY_CONFIG.forEach((category) => {
    const card = document.createElement('article');
    card.className = `category-card ${listState.main === category.key ? 'is-active' : ''}`;
    const submenuTemplate = category.subBoards?.length
      ? `
      <div class="category-card__submenu">
        <p>서브 메뉴</p>
        <div class="category-card__submenu-icons">
          ${category.subBoards
            .map(
              (sub) => `
            <button type="button" class="category-card__submenu-item ${
              listState.main === category.key && listState.sub === sub.value ? 'is-active' : ''
            }" data-sub-value="${sub.value}" aria-label="${sub.text}" title="${sub.text}">
              <span class="category-card__submenu-icon">
                <img src="${sub.icon}" alt="${sub.text}" />
              </span>
              <span class="category-card__submenu-label">${sub.text}</span>
            </button>`
            )
            .join('')}
        </div>
      </div>`
      : '';
    card.innerHTML = `
      <div class="category-card__body">
        <h3>${category.title}</h3>
        <p>${category.description}</p>
      </div>
      ${submenuTemplate}
    `;
    card.addEventListener('click', () => {
      if (!hasGalleryListSection) {
        const firstSub = category.subBoards?.[0]?.value;
        const search = new URLSearchParams({ main: category.key });
        if (firstSub) {
          search.set('sub', firstSub);
        }
        window.location.href = `gallery-list.html?${search.toString()}`;
        return;
      }
      listState.main = category.key;
      listState.sub = null;
      listState.tab = '전체';
      renderCategoryIntro(category.key);
      renderMainCategories();
      renderTabs(category.key);
      loadGallery(true);
    });
    card.querySelectorAll('.category-card__submenu-item').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        const subValue = button.dataset.subValue;
        const search = new URLSearchParams({
          main: category.key,
          sub: subValue
        });
        if (listState.tab && listState.tab !== '전체') {
          search.set('tab', listState.tab);
        }
        window.location.href = `gallery-list.html?${search.toString()}`;
      });
    });
    grid.appendChild(card);
  });
}

function renderCategoryIntro(mainKey) {
  const titleEl = document.getElementById('categoryHeading');
  const descriptionEl = document.getElementById('categoryDescription');
  const category = GALLERY_CONFIG.find((c) => c.key === mainKey);
  if (category) {
    if (titleEl) titleEl.textContent = category.title;
    if (descriptionEl) descriptionEl.textContent = category.description;
  } else {
    if (titleEl) titleEl.textContent = '카테고리';
    if (descriptionEl) descriptionEl.textContent = '대메뉴를 선택해 작품을 탐색하세요.';
  }
}

function renderTabs(mainKey) {
  const container = document.getElementById('tabList');
  if (!container) return;
  const category = GALLERY_CONFIG.find((c) => c.key === mainKey);
  if (!category) {
    container.innerHTML = '';
    return;
  }
  container.innerHTML = '';
  category.tabs.forEach((tab) => {
    const button = document.createElement('button');
    button.className = `chip ${listState.tab === tab ? 'active' : ''}`;
    button.textContent = tab;
    button.addEventListener('click', () => {
      listState.tab = tab;
      renderTabs(mainKey);
      loadGallery(true);
    });
    container.appendChild(button);
  });
}

async function loadGallery(reset) {
  if (!listState.main) {
    renderGalleryCards([]);
    setStatus(document.getElementById('galleryStatus'), '대메뉴를 먼저 선택하세요.', 'info');
    return;
  }
  const statusEl = document.getElementById('galleryStatus');
  setStatus(statusEl, '갤러리를 불러오는 중입니다...', 'info');
  const page = reset ? 0 : listState.page + 1;
  const params = new URLSearchParams({
    page,
    size: listState.size,
    sort: listState.tab === '인기' ? 'popular' : 'latest',
    mainBoard: listState.main
  });
  if (listState.sub) {
    params.set('subBoard', listState.sub);
  }
  if (listState.tab && listState.tab !== '전체') {
    params.set('tab', listState.tab);
  }
  try {
    const response = await fetchJson(`/api/gallery?${params.toString()}`);
    listState.page = page;
    listState.hasNext = !response.last;
    listState.items = reset ? response.content : listState.items.concat(response.content);
    renderGalleryCards(listState.items);
    const loadMore = document.getElementById('loadMoreGallery');
    if (loadMore) {
      loadMore.disabled = !listState.hasNext;
      loadMore.textContent = listState.hasNext ? '더 보기' : '마지막 페이지입니다';
    }
    setStatus(statusEl, listState.items.length ? '' : '등록된 작품이 없습니다.', listState.items.length ? 'info' : 'error');
  } catch (error) {
    setStatus(statusEl, error.message || '갤러리를 불러오지 못했습니다.', 'error');
  }
}

function renderGalleryCards(items) {
  const container = document.getElementById('galleryList');
  if (!container) return;
  container.innerHTML = '';
  if (!items.length) {
    container.innerHTML = '<p class="empty">등록된 작품이 없습니다.</p>';
    return;
  }
  items.forEach((item) => {
    const hasAttachment = Boolean(
      (item.thumbnailUrls && item.thumbnailUrls.length) || (item.attachmentUrls && item.attachmentUrls.length)
    );
    const attachmentLabel = hasAttachment ? '첨부 파일이 있습니다.' : '첨부된 파일이 없습니다.';
    const attachmentIcon = hasAttachment ? '📎' : '🗂️';
    const author = item.authorNickname || item.authorUsername || '익명';
    const tabLabel = item.tabItem ? `<span class="gallery-card__tag">${item.tabItem}</span>` : '';
    const card = document.createElement('article');
    card.className = 'gallery-card';
    card.innerHTML = `
      <div class="gallery-card__media${hasAttachment ? ' has-attachment' : ''}" aria-label="${attachmentLabel}">
        <span class="gallery-card__media-icon" aria-hidden="true">${attachmentIcon}</span>
        <span class="gallery-card__media-text">${hasAttachment ? '첨부 있음' : '첨부 없음'}</span>
      </div>
      <div class="gallery-card__body">
        <div class="gallery-card__meta">
          <span>${item.mainBoardName} / ${item.subBoardName || '-'}</span>
          <small>${new Date(item.createdAt).toLocaleDateString('ko-KR')}</small>
        </div>
        <h3>${item.title}</h3>
        ${tabLabel}
        <div class="gallery-card__footer">
          <span class="gallery-card__author">${author}</span>
          <div class="gallery-card__stats">
            <span>추천 ${item.likeCount ?? 0}</span>
            <span>댓글 ${item.commentCount ?? 0}</span>
          </div>
        </div>
      </div>
    `;
    card.addEventListener('click', () => {
      window.location.href = `gallery-detail.html?id=${item.id}`;
    });
    container.appendChild(card);
  });
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
