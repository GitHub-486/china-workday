package ict.china.workday.service;

import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 查询聚合服务（默认按时间倒序或正序列出）。
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    private final HolidayRepository repository;
    private final WorkdayJudgeService workdayJudgeService;

    public HolidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        this.repository = repository;
        this.workdayJudgeService = workdayJudgeService;
    }

    public Optional<HolidayRecord> findByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<HolidayRecord> findByMonth(int year, int month) {
        return repository.findByMonth(year, month);
    }

    public List<HolidayRecord> findByYear(int year) {
        return repository.findByYear(year);
    }

    public long countBySource(String source) {
        return repository.countBySource(source);
    }
}
