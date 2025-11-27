package com.example.authapp.post;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Locale;

/**
 * Normalizes DB 문자열을 {@link PostStatus} enum으로 안전하게 변환.
 * 잘못된 값은 기본 게시 상태(PUBLISHED)로 처리한다.
 */
@Converter(autoApply = true)
public class PostStatusConverter implements AttributeConverter<PostStatus, String> {

    @Override
    public String convertToDatabaseColumn(PostStatus attribute) {
        PostStatus value = attribute != null ? attribute : PostStatus.PUBLISHED;
        return value.name();
    }

    @Override
    public PostStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return PostStatus.PUBLISHED;
        }

        String normalized = dbData.trim();
        if (normalized.isEmpty()) {
            return PostStatus.PUBLISHED;
        }

        try {
            return PostStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PostStatus.PUBLISHED;
        }
    }
}
