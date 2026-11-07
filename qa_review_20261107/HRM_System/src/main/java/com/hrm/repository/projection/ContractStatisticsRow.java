package com.hrm.repository.projection;

public interface ContractStatisticsRow {

    Long getContractCount();

    Long getEffectiveCount();

    Long getExpiringCount();
}
