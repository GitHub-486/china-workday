package ict.china.workday.service;

import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.repository.HolidayRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 核心业务：上班日判断。
 *
 * <p>定义：日期 d 是上班日 ⟺
 * ( d ∈ 周一~周五 且 d ∉ HOLIDAY ) 或 ( d ∈ 周六/周日 且 d ∈ WORKDAY 调休上班 )。</p>
 */
@Service
public class WorkdayJudgeService {

    private final HolidayRepository repository;

    public WorkdayJudgeService(HolidayRepository repository) {
        this.repository = repository;
    }

    /**
     * 判断指定日期是否上班日。
     */
    public boolean isWorkday(LocalDate date) {
        Optional<HolidayRecord> found = repository.findByDate(date);
        if (found.isEmpty()) {
            DayOfWeek dow = date.getDayOfWeek();
            return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        }
        HolidayRecord record = found.get();
        switch (record.getDayType()) {
            case WORKDAY:
                return true;
            case HOLIDAY:
                return false;
            default:
                return true;
        }
    }
}
