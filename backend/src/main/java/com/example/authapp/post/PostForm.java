package com.example.authapp.post;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class PostForm {
    @NotEmpty(message = "제목은 필수항목입니다.")
    @Size(max = 200)
    private String title;

    @NotEmpty(message = "내용은 필수항목입니다.")
    private String content;

    @NotEmpty(message = "게시판은 필수항목입니다.")
    private String subBoardName;

    private String tabItem;

    private String mainBoardName;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSubBoardName() {
        return subBoardName;
    }

    public void setSubBoardName(String subBoardName) {
        this.subBoardName = subBoardName;
    }

    public String getTabItem() {
        return tabItem;
    }

    public void setTabItem(String tabItem) {
        this.tabItem = tabItem;
    }

    public String getMainBoardName() {
        return mainBoardName;
    }

    public void setMainBoardName(String mainBoardName) {
        this.mainBoardName = mainBoardName;
    }
}
