package com.example.authapp.user;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Locale;

/**
 * Safely maps database string values to {@link UserStatus} enum constants while tolerating
 * null, 빈 문자열, 혹은 대소문자 차이 같은 잘못된 데이터 입력.
 */
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        UserStatus value = attribute != null ? attribute : UserStatus.ACTIVE;
        return value.name();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return UserStatus.ACTIVE;
        }

        String normalized = dbData.trim();
        if (normalized.isEmpty()) {
            return UserStatus.ACTIVE;
        }

        try {
            return UserStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UserStatus.ACTIVE;
        }
    }
}
