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

const boardState = {
  boards: [],
  main: null,
  sub: null,
  searchType: 'all',
  keyword: '',
  page: 0,
  size: 12,
  hasNext: true,
  items: []
};

runWhenReady(initBoardListPage);

function initBoardListPage() {
  const params = new URLSearchParams(window.location.search);
  boardState.searchType = params.get('searchType') || 'all';
  boardState.keyword = params.get('kw') || '';
  loadBoardConfig()
    .then(() => {
      applyInitialSelection(params);
      renderSelectors();
      bindBoardControls();
      updateHero();
      if (boardState.keyword) {
        const keywordInput = document.getElementById('boardSearchKeyword');
        if (keywordInput) keywordInput.value = boardState.keyword;
      }
      loadBoardPosts(true);
    })
    .catch((error) => {
      setStatus(document.getElementById('boardStatus'), error.message || '게시판 정보를 불러오지 못했습니다.', 'error');
    });
}

async function loadBoardConfig() {
  const statusEl = document.getElementById('boardStatus');
  setStatus(statusEl, '게시판 정보를 불러오는 중입니다...', 'info');
  const response = await fetchJson('/api/home');
  boardState.boards = response?.boards || [];
  if (!boardState.boards.length) {
    throw new Error('등록된 게시판이 없습니다. 관리자에게 문의하세요.');
  }
  setStatus(statusEl, '', 'info');
}

function applyInitialSelection(params) {
  const desiredMain = params.get('main');
  const boards = boardState.boards;
  const mainBoard = boards.find((board) => board.name === desiredMain) || boards[0];
  boardState.main = mainBoard?.name || null;
  const desiredSub = params.get('sub');
  const subBoards = mainBoard?.subBoards || [];
  boardState.sub = subBoards.includes(desiredSub) ? desiredSub : subBoards[0] || null;
}

function bindBoardControls() {
  const mainSelect = document.getElementById('boardMainSelect');
  const subSelect = document.getElementById('boardSubSelect');
  const searchTypeSelect = document.getElementById('boardSearchType');
  const keywordInput = document.getElementById('boardSearchKeyword');
  const searchForm = document.getElementById('boardSearchForm');
  const loadMoreBtn = document.getElementById('boardLoadMore');
  const writeBtn = document.getElementById('boardWriteButton');
  const backBtn = document.getElementById('boardBackButton');

  if (mainSelect) {
    mainSelect.value = boardState.main || '';
    mainSelect.addEventListener('change', () => {
      boardState.main = mainSelect.value || null;
      const board = findBoard(boardState.main);
      const subs = board?.subBoards || [];
      boardState.sub = subs[0] || null;
      renderSubSelect();
      updateHero();
      loadBoardPosts(true);
    });
  }

  if (subSelect) {
    subSelect.value = boardState.sub || '';
    subSelect.addEventListener('change', () => {
      boardState.sub = subSelect.value || null;
      updateHero();
      loadBoardPosts(true);
    });
  }

  if (searchTypeSelect) {
    searchTypeSelect.value = boardState.searchType;
  }

  if (keywordInput && boardState.keyword) {
    keywordInput.value = boardState.keyword;
  }

  searchForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    boardState.searchType = searchTypeSelect?.value || 'all';
    boardState.keyword = keywordInput?.value?.trim() || '';
    loadBoardPosts(true);
  });

  loadMoreBtn?.addEventListener('click', () => loadBoardPosts(false));
  writeBtn?.addEventListener('click', () => {
    const query = new URLSearchParams();
    if (boardState.main) query.set('main', boardState.main);
    if (boardState.sub) query.set('sub', boardState.sub);
    window.location.href = `board-upload.html?${query.toString()}`;
  });
  backBtn?.addEventListener('click', () => {
    window.location.href = 'index.html#board';
  });
}

function renderSelectors() {
  const mainSelect = document.getElementById('boardMainSelect');
  const subSelect = document.getElementById('boardSubSelect');
  if (mainSelect) {
    mainSelect.innerHTML = boardState.boards
      .map((board) => `<option value="${board.name}">${board.displayName}</option>`)
      .join('');
    mainSelect.value = boardState.main || '';
  }
  renderSubSelect();
  if (subSelect) {
    subSelect.value = boardState.sub || '';
  }
}

function renderSubSelect() {
  const subSelect = document.getElementById('boardSubSelect');
  if (!subSelect) return;
  const board = findBoard(boardState.main);
  const subs = board?.subBoards || [];
  if (!subs.length) {
    subSelect.innerHTML = '<option value="">세부 카테고리가 없습니다.</option>';
    subSelect.disabled = true;
    boardState.sub = null;
    return;
  }
  subSelect.disabled = false;
  subSelect.innerHTML = subs.map((sub) => `<option value="${sub}">${sub}</option>`).join('');
  subSelect.value = boardState.sub || subs[0];
  boardState.sub = subSelect.value || subs[0];
}

function updateHero() {
  const heroTitle = document.getElementById('boardHeroTitle');
  const heroDescription = document.getElementById('boardHeroDescription');
  const panelEyebrow = document.getElementById('boardPanelEyebrow');
  const panelTitle = document.getElementById('boardPanelTitle');
  const board = findBoard(boardState.main);
  const displayName = board?.displayName || '자유게시판';
  const subName = boardState.sub || '전체';
  if (heroTitle) heroTitle.textContent = `${displayName} 게시판`;
  if (heroDescription) heroDescription.textContent = `${subName} 카테고리의 최신 글을 확인해 보세요.`;
  if (panelEyebrow) panelEyebrow.textContent = displayName;
  if (panelTitle) panelTitle.textContent = `${subName} 게시글 목록`;
}

async function loadBoardPosts(reset) {
  const statusEl = document.getElementById('boardStatus');
  const listEl = document.getElementById('boardList');
  if (!boardState.sub) {
    setStatus(statusEl, '세부 카테고리를 선택해 주세요.', 'error');
    if (listEl) listEl.innerHTML = '<div class="gallery-table__empty">카테고리가 없습니다.</div>';
    return;
  }
  setStatus(statusEl, '게시글을 불러오는 중입니다...', 'info');
  const page = reset ? 0 : boardState.page + 1;
  const params = new URLSearchParams({
    page,
    size: boardState.size,
    searchType: boardState.searchType,
    subBoard: boardState.sub
  });
  if (boardState.keyword) {
    params.set('kw', boardState.keyword);
  }
  try {
    const response = await fetchJson(`/api/posts?${params.toString()}`);
    boardState.page = page;
    boardState.hasNext = !response.last;
    boardState.items = reset ? response.content : boardState.items.concat(response.content);
    renderBoardRows(boardState.items, listEl);
    const loadMoreBtn = document.getElementById('boardLoadMore');
    if (loadMoreBtn) {
      loadMoreBtn.disabled = !boardState.hasNext;
      loadMoreBtn.textContent = boardState.hasNext ? '더 보기' : '마지막 페이지입니다';
    }
    setStatus(statusEl, boardState.items.length ? '' : '등록된 게시글이 없습니다.', boardState.items.length ? 'info' : 'error');
  } catch (error) {
    setStatus(statusEl, error.message || '게시글을 불러오지 못했습니다.', 'error');
    if (reset && listEl) {
      listEl.innerHTML = '<div class="gallery-table__empty">데이터를 불러오지 못했습니다.</div>';
    }
  }
}

function renderBoardRows(items, container) {
  if (!container) return;
  container.innerHTML = '';
  if (!items.length) {
    container.innerHTML = '<div class="gallery-table__empty">등록된 게시글이 없습니다.</div>';
    return;
  }
  const fragment = document.createDocumentFragment();
  items.forEach((item) => {
    const hasAttachment = Array.isArray(item.fileUrls) && item.fileUrls.length > 0;
    const boardName = findBoard(item.mainBoardName)?.displayName || item.mainBoardName || '게시판';
    const author = item.authorNickname || item.authorUsername || '익명';
    const tabLabel = item.tabItem ? `<span class="gallery-table__tag">${item.tabItem}</span>` : '';
    const excerpt = item.excerpt ? `<p class="gallery-table__excerpt">${item.excerpt}</p>` : '';
    const createdDate = item.createdAt ? new Date(item.createdAt).toLocaleDateString('ko-KR') : '-';
    const row = document.createElement('article');
    row.className = 'gallery-table__row';
    row.innerHTML = `
      <div class="gallery-table__cell gallery-table__cell--info">
        <p class="gallery-table__category">${boardName} / ${item.subBoardName || '-'}</p>
        <div class="gallery-table__title-row">
          <h3>${item.title}</h3>
          ${hasAttachment ? '<span class="gallery-table__attachment" aria-label="첨부 있음" role="img">📎</span>' : ''}
        </div>
        ${excerpt}
        ${tabLabel ? `<div class="gallery-table__meta">${tabLabel}</div>` : ''}
      </div>
      <div class="gallery-table__cell gallery-table__cell--author">
        <span class="gallery-table__author-name">${author}</span>
      </div>
      <div class="gallery-table__cell gallery-table__cell--stats">
        <span>추천 ${item.voteCount ?? 0}</span>
        <span>조회 ${item.viewCount ?? 0}</span>
      </div>
      <div class="gallery-table__cell gallery-table__cell--date">${createdDate}</div>
    `;
    row.addEventListener('click', () => {
      window.location.href = `board-detail.html?id=${item.id}`;
    });
    fragment.appendChild(row);
  });
  container.appendChild(fragment);
}

function findBoard(name) {
  if (!name) return null;
  return boardState.boards.find((board) => board.name === name) || null;
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
