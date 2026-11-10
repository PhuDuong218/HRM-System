package com.hrm.service;

import com.hrm.enums.DecisionType;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** SQL Server sequences allocate distinct numbers across concurrent app instances.
 * Numbers are never reused, including after deletion or transaction rollback. */
@Service
public class DecisionNumberService {
    private final JdbcTemplate jdbc;
    public DecisionNumberService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String next(DecisionType type) {
        String prefix=switch(type){case KHEN_THUONG->"KT";case KY_LUAT->"KL";case BOI_THUONG->"BT";};
        Long number=jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.decision_number_"+prefix.toLowerCase(Locale.ROOT),Long.class);
        if(number==null||number<=0)throw new IllegalStateException("Không cấp được số quyết định");
        return prefix+"-"+String.format(Locale.ROOT,"%06d",number);
    }
}
