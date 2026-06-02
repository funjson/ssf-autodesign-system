package com.ssf.autodesign.repository;

import com.ssf.autodesign.domain.ChangeRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequestEntity, Long> {
    List<ChangeRequestEntity> findBySpiInstanceIdOrderByCreatedAtDesc(Long spiInstanceId);

    void deleteBySpiInstanceIdIn(Collection<Long> spiInstanceIds);
}
