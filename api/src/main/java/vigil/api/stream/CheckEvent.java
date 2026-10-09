package vigil.api.stream;

import java.time.OffsetDateTime;

import vigil.api.site.SiteStatus;

public record CheckEvent(Integer siteId,
    SiteStatus status,
    Integer statusCode,
    Integer responseTimeMs,
    String error,
    OffsetDateTime checkedAt){}
