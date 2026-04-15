package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.SchedulerJobLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulerJobLogRepository extends MongoRepository<SchedulerJobLog, String> {

    Optional<SchedulerJobLog> findByJobId(String jobId);

    List<SchedulerJobLog> findByJobNameOrderByStartedAtDesc(String jobName);

    List<SchedulerJobLog> findByRentMonth(LocalDate rentMonth);

    List<SchedulerJobLog> findByStatus(SchedulerJobLog.JobStatus status);

    List<SchedulerJobLog> findByStartedAtBetweenOrderByStartedAtDesc(LocalDateTime from, LocalDateTime to);

    List<SchedulerJobLog> findByJobNameAndRentMonth(String jobName, LocalDate rentMonth);

    List<SchedulerJobLog> findTop10ByOrderByStartedAtDesc();
}
