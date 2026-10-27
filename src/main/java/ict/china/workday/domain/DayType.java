package ict.china.workday.domain;

/**
 * 日期类型。
 * HOLIDAY 放假休息；WORKDAY 调休上班。
 */
public enum DayType {
    HOLIDAY,
    WORKDAY;

    public static DayType from(String value) {
        if (value == null) {
            return null;
        }
        return DayType.valueOf(value.trim().toUpperCase());
    }
}
