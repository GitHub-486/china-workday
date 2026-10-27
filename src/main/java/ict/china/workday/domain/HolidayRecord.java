package ict.china.workday.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 节假日/调休记录实体。
 */
public class HolidayRecord {

    private Long id;
    private LocalDate holidayDate;
    private String holidayName;
    private DayType dayType;
    private int year;
    private String source;
    private LocalDateTime createdAt;

    public HolidayRecord() {
    }

    public HolidayRecord(LocalDate holidayDate, String holidayName, DayType dayType, int year, String source) {
        this.holidayDate = holidayDate;
        this.holidayName = holidayName;
        this.dayType = dayType;
        this.year = year;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getHolidayName() {
        return holidayName;
    }

    public void setHolidayName(String holidayName) {
        this.holidayName = holidayName;
    }

    public DayType getDayType() {
        return dayType;
    }

    public void setDayType(DayType dayType) {
        this.dayType = dayType;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
