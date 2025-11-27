package com.example.authapp.post.dto;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class GalleryUploadForm {

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
    private String title;

    @NotBlank(message = "본문을 입력하세요.")
    private String content;

    @NotBlank(message = "메인 보드를 선택하세요.")
    private String mainBoardName;

    @NotBlank(message = "서브 보드를 선택하세요.")
    private String subBoardName;

    @Size(max = 50, message = "탭 이름은 50자를 넘을 수 없습니다.")
    private String tabItem;

        private List<MultipartFile> files = new ArrayList<>();

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

    public String getMainBoardName() {
        return mainBoardName;
    }

    public void setMainBoardName(String mainBoardName) {
        this.mainBoardName = mainBoardName;
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

    public List<MultipartFile> getFiles() {
        return files;
    }

    public void setFiles(List<MultipartFile> files) {
        this.files = files;
    }
}
