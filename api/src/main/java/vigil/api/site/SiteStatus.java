package vigil.api.site;

import java.time.OffsetDateTime;

public enum SiteStatus {
  UP, DOWN,SLOW, PENDING;
  public static SiteStatus from(OffsetDateTime checkedAt,boolean up, Integer responseTimeMs){
    if (checkedAt==null) return PENDING;
    if (!up) return DOWN;
    if (responseTimeMs > 1000) return SLOW;
    return UP;
  }
}
