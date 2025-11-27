package com.example.authapp.home;

import com.example.authapp.post.dto.PostSummaryResponse;

import java.util.List;

public record HomeResponse(
        List<BoardSection> boards,
        List<PostSummaryResponse> recentPosts,
        List<PostSummaryResponse> popularPosts
) {

    public record BoardSection(String name,
                               String displayName,
                               List<String> subBoards) {
    }
}
