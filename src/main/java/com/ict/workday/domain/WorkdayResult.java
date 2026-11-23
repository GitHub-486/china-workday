package com.ict.workday.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkdayResult {

    private String date;
    private boolean isWorkday;
    private String dayType;
    private String holidayName;
    private String typeName;

    public WorkdayResult() {
    }

    public WorkdayResult(String date, boolean isWorkday, String dayType, String holidayName, String typeName) {
        this.date = date;
        this.isWorkday = isWorkday;
        this.dayType = dayType;
        this.holidayName = holidayName;
        this.typeName = typeName;
    }

    public String getDate() { return date; }
    public boolean isIsWorkday() { return isWorkday; }
    public String getDayType() { return dayType; }
    public String getHolidayName() { return holidayName; }
    public String getTypeName() { return typeName; }
}
