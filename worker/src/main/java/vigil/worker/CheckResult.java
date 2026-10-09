package vigil.worker;

import java.time.OffsetDateTime;


public record CheckResult(boolean up,
    Integer statusCode,
    Integer responseTimeMs,
    String error,
    OffsetDateTime checkedAt){}
