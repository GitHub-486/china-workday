package ict.china.workday.repository;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JdbcTemplate 的实现。
 * 全部使用参数化查询（SEC-06），holiday_date 唯一约束保证幂等 upsert。
 */
@Repository
public class HolidayJdbcRepository implements HolidayRepository {

    private static final Logger log = LoggerFactory.getLogger(HolidayJdbcRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<HolidayRecord> ROW_MAPPER = (rs, rowNum) -> {
        HolidayRecord r = new HolidayRecord();
        r.setId(rs.getLong("id"));
        r.setHolidayDate(rs.getObject("holiday_date", LocalDate.class));
        r.setHolidayName(rs.getString("holiday_name"));
        r.setDayType(DayType.valueOf(rs.getString("day_type")));
        r.setYear(rs.getInt("year"));
        r.setSource(rs.getString("source"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            r.setCreatedAt(ts.toLocalDateTime());
        }
        return r;
    };

    public HolidayJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(HolidayRecord record) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holiday WHERE holiday_date = ?", Integer.class, record.getHolidayDate());
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    """
                    UPDATE holiday SET
                        holiday_name = ?, day_type = ?, year = ?, source = ?, created_at = ?
                    WHERE holiday_date = ?
                    """,
                    record.getHolidayName(), record.getDayType().name(),
                    record.getYear(), record.getSource(), Timestamp.valueOf(record.getCreatedAt().withNano(0)),
                    record.getHolidayDate());
        } else {
            jdbcTemplate.update(
                    """
                    INSERT INTO holiday (holiday_date, holiday_name, day_type, year, source, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    record.getHolidayDate(), record.getHolidayName(), record.getDayType().name(),
                    record.getYear(), record.getSource(), Timestamp.valueOf(record.getCreatedAt().withNano(0)));
        }
    }

    @Override
    public int upsertAll(List<HolidayRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (HolidayRecord r : records) {
            upsert(r);
            count++;
        }
        return count;
    }

    @Override
    public Optional<HolidayRecord> findByDate(LocalDate date) {
        List<HolidayRecord> list = jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date = ?", ROW_MAPPER, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<HolidayRecord> findByMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate next = start.plusMonths(1);
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date >= ? AND holiday_date < ? ORDER BY holiday_date",
                ROW_MAPPER, start, next);
    }

    @Override
    public List<HolidayRecord> findByYear(int year) {
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE year = ? ORDER BY holiday_date", ROW_MAPPER, year);
    }

    @Override
    public long countBySource(String source) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holiday WHERE source = ?", Long.class, source);
        return c == null ? 0L : c;
    }

    @Override
    public int deleteByYear(int year) {
        return jdbcTemplate.update("DELETE FROM holiday WHERE year = ?", year);
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM holiday");
    }
}
