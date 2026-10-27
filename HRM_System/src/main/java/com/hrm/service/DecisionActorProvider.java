package com.hrm.service;
import com.hrm.dto.DecisionActor;
public interface DecisionActorProvider {
    // Adapter thật phải lấy từ SecurityContext và kiểm tra quyền quản lý đã được nhóm chốt.
    DecisionActor requireManager();
}