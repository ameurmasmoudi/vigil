package vigil.api.site;

import java.time.OffsetDateTime;

public record SiteStatusResponse(Integer id,
    String name,
    String url,
    SiteStatus status,
    Integer responseTimeMs,
    OffsetDateTime checkedAt){}
    
