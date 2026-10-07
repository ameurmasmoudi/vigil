package vigil.api.site;

import java.time.OffsetDateTime;


public record CheckResponse(boolean up,
    Integer statusCode,
    Integer responseTimeMs,
    String error,
    OffsetDateTime checkedAt){}
