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

    @Converter(autoApply = true)
    public static class BidStatusConverter implements AttributeConverter<BidStatus, String> {
        @Override
        public String convertToDatabaseColumn(BidStatus attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public BidStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : BidStatus.from(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class DefectSeverityConverter implements AttributeConverter<DefectSeverity, String> {
        @Override
        public String convertToDatabaseColumn(DefectSeverity attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public DefectSeverity convertToEntityAttribute(String dbData) {
            return dbData == null ? null : DefectSeverity.from(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class PriceCheckStatusConverter
            implements AttributeConverter<PriceCheck.Status, String> {
        @Override
        public String convertToDatabaseColumn(PriceCheck.Status a) {
            return a == null ? null : a.value();
        }
        @Override
        public PriceCheck.Status convertToEntityAttribute(String v) {
            return v == null ? null : PriceCheck.Status.from(v);
        }
    }

    @Converter(autoApply = true)
    public static class PriceCheckVerdictConverter
            implements AttributeConverter<PriceCheck.Verdict, String> {
        @Override
        public String convertToDatabaseColumn(PriceCheck.Verdict a) {
            return a == null ? null : a.value();
        }
        @Override
        public PriceCheck.Verdict convertToEntityAttribute(String v) {
            return v == null ? null : PriceCheck.Verdict.from(v);
        }
    }
}
