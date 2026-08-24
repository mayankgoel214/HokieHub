package edu.vt.hokiehub.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The schema constrains these columns with CHECK (... IN ('like_new', ...)), so the
 * stored form is lower_snake_case and cannot be derived from the Java constant name.
 * These converters keep the database contract and idiomatic Java names both intact.
 */
public final class EnumConverters {

    private EnumConverters() {}

    @Converter(autoApply = true)
    public static class ListingTypeConverter implements AttributeConverter<ListingType, String> {
        @Override
        public String convertToDatabaseColumn(ListingType attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public ListingType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ListingType.from(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class ListingStatusConverter implements AttributeConverter<ListingStatus, String> {
        @Override
        public String convertToDatabaseColumn(ListingStatus attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public ListingStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ListingStatus.from(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class ItemConditionConverter implements AttributeConverter<ItemCondition, String> {
        @Override
        public String convertToDatabaseColumn(ItemCondition attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public ItemCondition convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ItemCondition.from(dbData);
        }
    }
}
