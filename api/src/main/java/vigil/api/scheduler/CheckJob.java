package vigil.api.scheduler;

import java.time.OffsetDateTime;

public record CheckJob(Integer siteId,
    String url,
    OffsetDateTime scheduledAt){}
