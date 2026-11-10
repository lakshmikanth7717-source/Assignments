package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.repository.ReconciliationRepository;
import com.airtribe.wallet.dto.ReconciliationModels.RunSummary;
import com.airtribe.wallet.dto.ReconciliationModels.ProblemReport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcReconciliationRepository implements ReconciliationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReconciliationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryLockJob(String jobName) {
        try {
            // A missing row is a configuration error, not a competing run.
            jdbc.queryForObject("""
                    SELECT job_name
                    FROM job_locks
                    WHERE job_name = :jobName
                    FOR UPDATE NOWAIT
                    """, Map.of("jobName", jobName), String.class);
            return true;
        } catch (DataAccessException failure) {
            if (failure.getMostSpecificCause() instanceof SQLException sql
                    && "55P03".equals(sql.getSQLState())) {
                // PostgreSQL lock_not_available. The caller must roll back this transaction.
                return false;
            }
            throw failure;
        }
    }

    @Override
    public long startRun(String jobName) {
        Long runId = jdbc.queryForObject("""
                INSERT INTO reconciliation_runs (job_name)
                VALUES (:jobName)
                RETURNING run_id
                """, Map.of("jobName", jobName), Long.class);
        if (runId == null) { throw new IllegalStateException("Reconciliation run ID was not returned"); }
        return runId;
    }

    @Override
    public void finishRun(long runId, RunStatus status, int checksCompleted,
                          List<Problem> problems, String errorMessage) {
        if (status == RunStatus.RUNNING || status == RunStatus.QUEUED) {
            throw new IllegalArgumentException("A finished run requires a final status");
        }
        if (!problems.isEmpty()) {
            MapSqlParameterSource[] batch = problems.stream().map(problem -> new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("checkName", problem.checkName().name())
                    .addValue("entityId", problem.entityId())
                    .addValue("expected", problem.expected())
                    .addValue("actual", problem.actual())).toArray(MapSqlParameterSource[]::new);
            int[] inserted = jdbc.batchUpdate("""
                    INSERT INTO reconciliation_problems
                        (run_id, check_name, entity_id, expected_value, actual_value)
                    VALUES (:runId, :checkName, :entityId, :expected, :actual)
                    """, batch);
            for (int count : inserted) {
                if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                    throw new IllegalStateException("A reconciliation problem was not inserted");
                }
            }
        }
        int updated = jdbc.update("""
                UPDATE reconciliation_runs
                SET finished_at = clock_timestamp(), status = :status,
                    checks_completed = :checksCompleted, problem_count = :problemCount,
                    error_message = :errorMessage
                WHERE run_id = :runId AND status = 'RUNNING'
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("status", status.name())
                .addValue("checksCompleted", checksCompleted)
                .addValue("problemCount", problems.size())
                .addValue("errorMessage", errorMessage, Types.VARCHAR));
        if (updated != 1) { throw new IllegalStateException("Expected to finish exactly one running reconciliation"); }
    }

    @Override
    public long enqueueRun(String jobName, long requestedByUserId) {
        return jdbc.queryForObject("""
                INSERT INTO reconciliation_runs (job_name, status, started_at, requested_by_user_id)
                VALUES (:jobName, 'QUEUED', NULL, :userId) RETURNING run_id
                """, Map.of("jobName", jobName, "userId", requestedByUserId), Long.class);
    }

    @Override
    public List<Long> findQueuedRuns(int limit) {
        return jdbc.query("""
                SELECT run_id FROM reconciliation_runs
                WHERE status = 'QUEUED' AND job_name = 'ledger_reconciliation'
                ORDER BY requested_at, run_id LIMIT :limit
                """, Map.of("limit", limit), (row, index) -> row.getLong("run_id"));
    }

    @Override
    public boolean startQueuedRun(long runId) {
        return jdbc.update("""
                UPDATE reconciliation_runs SET status = 'RUNNING', started_at = clock_timestamp()
                WHERE run_id = :id AND status = 'QUEUED' AND job_name = 'ledger_reconciliation'
                """, Map.of("id", runId)) == 1;
    }

    @Override
    public Optional<RunSummary> findRun(long runId) {
        return jdbc.query("""
                SELECT run_id, job_name, requested_at, started_at, finished_at, status,
                       checks_completed, problem_count, error_message
                FROM reconciliation_runs WHERE run_id = :id
                """, Map.of("id", runId), (row, index) -> new RunSummary(row.getLong("run_id"),
                row.getString("job_name"), row.getTimestamp("requested_at").toInstant(),
                row.getTimestamp("started_at") == null ? null : row.getTimestamp("started_at").toInstant(),
                row.getTimestamp("finished_at") == null ? null : row.getTimestamp("finished_at").toInstant(),
                row.getString("status"), row.getInt("checks_completed"), row.getLong("problem_count"),
                row.getString("error_message"))).stream().findFirst();
    }

    @Override
    public List<ProblemReport> findProblems(long runId, long offset, int limit) {
        return jdbc.query("""
                SELECT problem_id, check_name, entity_id, expected_value, actual_value
                FROM reconciliation_problems WHERE run_id = :id
                ORDER BY problem_id LIMIT :limit OFFSET :offset
                """, Map.of("id", runId, "offset", offset, "limit", limit), (row, index) ->
                new ProblemReport(row.getLong("problem_id"), row.getString("check_name"),
                        row.getString("entity_id"), row.getString("expected_value"), row.getString("actual_value")));
    }
}
