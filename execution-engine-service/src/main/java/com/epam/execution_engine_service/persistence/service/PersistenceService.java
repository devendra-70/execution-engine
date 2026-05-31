package com.epam.execution_engine_service.persistence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRP: Handles only transaction management and persistence.
 * DIP: Delegates entity mapping to {@link SubmissionMapper}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceService implements SubmissionPersistencePort {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;

    @Override
    @Transactional
    public void saveSubmission(ExecutionTaskEvent taskEvent, ExecutionResultEvent resultEvent) {
        SubmissionEntity submission = submissionMapper.toEntity(taskEvent, resultEvent);
        submissionRepository.save(submission);
        log.info("Saved submission {} for user {}", taskEvent.getExecutionId(), taskEvent.getUserId());
    }
}
