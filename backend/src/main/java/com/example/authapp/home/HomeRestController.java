package com.example.authapp.home;

import com.example.authapp.post.PostService;
import com.example.authapp.post.dto.PostSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
public class HomeRestController {

    private static final List<String> MAIN_BOARD_ORDER = List.of(
            "game",
            "exercise",
            "movie",
            "music",
            "travel",
            "invest"
    );

    private static final Map<String, String> DISPLAY_NAMES;

    static {
        DISPLAY_NAMES = new LinkedHashMap<>();
        DISPLAY_NAMES.put("game", "게임");
        DISPLAY_NAMES.put("exercise", "운동");
        DISPLAY_NAMES.put("movie", "영화");
        DISPLAY_NAMES.put("music", "음악");
        DISPLAY_NAMES.put("travel", "여행");
        DISPLAY_NAMES.put("invest", "투자");
    }

    private final PostService postService;

    public HomeRestController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public HomeResponse home() {
        List<HomeResponse.BoardSection> boards = MAIN_BOARD_ORDER.stream()
                .map(name -> new HomeResponse.BoardSection(
                        name,
                        DISPLAY_NAMES.getOrDefault(name, name),
                        postService.getUniqueSubBoardNamesForMainBoard(name)
                ))
                .toList();

        List<PostSummaryResponse> recentPosts = postService.getRecentList()
                .stream()
                .map(PostSummaryResponse::from)
                .toList();

        List<PostSummaryResponse> popularPosts = postService.getPopularGalleryPosts(10)
                .stream()
                .map(PostSummaryResponse::from)
                .toList();

        return new HomeResponse(boards, recentPosts, popularPosts);
    }
}
