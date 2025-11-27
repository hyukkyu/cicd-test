const GALLERY_CONFIG = [
  {
    key: 'game',
    title: '게임 갤러리',
    description: '발로란트부터 스타크래프트까지, 게임 매니아들의 플레이를 공유하세요.',
    heroImage: 'img/gallery/game-hero.jpg',
    tabs: ['전체', '일반', '질문', '파티', '창작', '그림'],
    subBoards: [
      { value: '발로란트', text: '발로란트', icon: 'icon/valorant.png' },
      { value: '롤', text: '롤', icon: 'icon/lol.png' },
      { value: '롤토체스', text: '롤토체스', icon: 'icon/tft.png' },
      { value: '배틀그라운드', text: '배틀그라운드', icon: 'icon/pubg.png' },
      { value: '오버워치2', text: '오버워치2', icon: 'icon/overwatch.png' },
      { value: '메이플스토리', text: '메이플스토리', icon: 'icon/maplestory.png' },
      { value: '피파온라인', text: '피파온라인', icon: 'icon/fifa.jpg' },
      { value: '스팀게임', text: '스팀게임', icon: 'icon/steam.png' },
      { value: '스타크래프트', text: '스타크래프트', icon: 'icon/starcraft.jpg' }
    ]
  },
  {
    key: 'travel',
    title: '여행 갤러리',
    description: '국내/해외 여행의 추억을 사진과 글로 공유하세요.',
    heroImage: 'img/gallery/travel-hero.jpg',
    tabs: ['전체', '일반', '질문', '정보', '후기', '사진', '모집'],
    subBoards: [
      { value: '국내여행', text: '국내여행', icon: 'icon/domestic.jpg' },
      { value: '해외여행', text: '해외여행', icon: 'icon/abroad.png' }
    ]
  },
  {
    key: 'exercise',
    title: '운동 갤러리',
    description: '헬스, 구기 종목, 격투기까지 운동 일지를 담아보세요.',
    heroImage: 'img/gallery/exercise-hero.jpg',
    tabs: ['전체', '일반', '질문', '장비', '대회', '꿀팁', '모집'],
    subBoards: [
      { value: '헬스', text: '헬스', icon: 'icon/fitness.png' },
      { value: '축구', text: '축구', icon: 'icon/soccer.png' },
      { value: '야구', text: '야구', icon: 'icon/baseball.png' },
      { value: '농구', text: '농구', icon: 'icon/basketball.png' },
      { value: '격투기', text: '격투기', icon: 'icon/boxing.png' },
      { value: '필라테스/요가', text: '필라테스/요가', icon: 'icon/yoga.png' }
    ]
  },
  {
    key: 'movie',
    title: '영화 갤러리',
    description: '장르별 추천/감상 후기/스틸컷을 공유하세요.',
    heroImage: 'img/gallery/movie-hero.jpg',
    tabs: ['전체', '일반', '질문', '개봉정보', '후기', '스포', '모집'],
    subBoards: [
      { value: '액션', text: '액션', icon: 'icon/action.png' },
      { value: '판타지', text: '판타지', icon: 'icon/fantasy.png' },
      { value: 'SF', text: 'SF', icon: 'icon/sf.png' },
      { value: '로맨스', text: '로맨스', icon: 'icon/romance.png' },
      { value: '스릴러', text: '스릴러', icon: 'icon/thriller.png' },
      { value: '공포', text: '공포', icon: 'icon/horror.png' }
    ]
  },
  {
    key: 'music',
    title: '음악 갤러리',
    description: '장르별 공연 영상, 플레이 리스트를 공유하세요.',
    heroImage: 'img/gallery/music-hero.jpg',
    tabs: ['전체', '일반', '질문', '추천', '정보'],
    subBoards: [
      { value: 'K-POP', text: 'K-POP', icon: 'icon/kpop.png' },
      { value: '힙합', text: '힙합', icon: 'icon/hiphop.png' },
      { value: '팝송', text: '팝송', icon: 'icon/pop.png' },
      { value: 'J-POP', text: 'J-POP', icon: 'icon/jpop.jpg' }
    ]
  },
  {
    key: 'invest',
    title: '투자 갤러리',
    description: '주식/코인/뉴스를 그래픽과 함께 공유하세요.',
    heroImage: 'img/gallery/invest-hero.jpg',
    tabs: ['전체', '정보', '뉴스'],
    subBoards: [
      { value: '주식', text: '주식', icon: 'icon/stock.png' },
      { value: '코인', text: '코인', icon: 'icon/coin.png' }
    ]
  }
];

window.GALLERY_CONFIG = GALLERY_CONFIG;
