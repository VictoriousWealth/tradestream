// persistence/RestingOrderRepository.java
package com.tradestream.matching_engine.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tradestream.matching_engine.domain.RestingOrder;

public interface RestingOrderRepository extends JpaRepository<RestingOrder, UUID> {
    @Query("select r from RestingOrder r where r.status in ('ACTIVE','PARTIALLY_FILLED')")
    List<RestingOrder> findAllActive();

    @Modifying
    @Query("update RestingOrder r set r.status=:status where r.id=:id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
