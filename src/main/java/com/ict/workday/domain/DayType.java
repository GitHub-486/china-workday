package com.ict.workday.domain;

/**
 * 日期类型。
 * HOLIDAY 放假休息；WORKDAY 调休上班。
 */
public enum DayType {
    HOLIDAY("放假"),
    WORKDAY("调休上班");

    private final String displayName;

    DayType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DayType from(String value) {
        if (value == null) {
            return null;
        }
        return DayType.valueOf(value.trim().toUpperCase());
    }
}
