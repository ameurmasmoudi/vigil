package vigil.api.site;

import java.time.OffsetDateTime;

public record CheckSummaryResponse(OffsetDateTime start,
    Integer avgResponseTimeMs,
    Integer maxResponseTimeMs,
    Integer checkCount,
    Integer failedCheckCount){}

