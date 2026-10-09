package vigil.api.stream;

import java.time.OffsetDateTime;

public record ResultEvent(Integer siteId,
    boolean up,
    Integer statusCode,
    Integer responseTimeMs,
    String error,
    OffsetDateTime checkedAt){}

