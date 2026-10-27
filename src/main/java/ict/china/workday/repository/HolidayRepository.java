package ict.china.workday.repository;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 节假日数据访问接口。
 */
public interface HolidayRepository {

    /**
     * 按 holiday_date 幂等 upsert 单条记录（SEC-06 参数化查询）。
     */
    void upsert(HolidayRecord record);

    /**
     * 批量幂等 upsert。
     */
    int upsertAll(List<HolidayRecord> records);

    /**
     * 查询某日期的存在记录。
     */
    Optional<HolidayRecord> findByDate(LocalDate date);

    /**
     * 查询某月全部记录（含上下月边界，便于区间展示）。
     */
    List<HolidayRecord> findByMonth(int year, int month);

    /**
     * 查询某年全部记录。
     */
    List<HolidayRecord> findByYear(int year);

    /**
     * 查询已入库的抓取来源记录数（用于调试）。
     */
    long countBySource(String source);

    /**
     * 删除某年数据（重新抓取前可选清理）。
     */
    int deleteByYear(int year);

    /**
     * 清空 holiday 表全部数据。
     */
    void truncate();
}
