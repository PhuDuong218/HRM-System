package com.hrm.util;

import java.util.UUID;

public final class AuditCorrelation {
    private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>();
    private AuditCorrelation(){}
    public static UUID current(){UUID id=CURRENT.get();return id==null?UUID.randomUUID():id;}
    public static void set(UUID id){CURRENT.set(id);}
    public static void clear(){CURRENT.remove();}
}
