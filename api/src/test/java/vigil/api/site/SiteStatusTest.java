package vigil.api.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

// Plain unit test of the status rule: no Spring context, no database, no Redis.
class SiteStatusTest {
  private static final OffsetDateTime CHECKED_AT = OffsetDateTime.parse("2026-10-12T09:00:00Z");

  @Test
  void neverCheckedIsPending() {
    assertThat(SiteStatus.from(null, false, null)).isEqualTo(SiteStatus.PENDING);
  }

  @Test
  void failedCheckIsDown() {
    assertThat(SiteStatus.from(CHECKED_AT, false, 120)).isEqualTo(SiteStatus.DOWN);
  }

  @Test
  void downWinsOverSlow() {
    assertThat(SiteStatus.from(CHECKED_AT, false, 5000)).isEqualTo(SiteStatus.DOWN);
  }

  @Test
  void downWithoutResponseTimeIsDown() {
    // a check that never got a response (DNS failure, refused connection) can have no response time
    assertThat(SiteStatus.from(CHECKED_AT, false, null)).isEqualTo(SiteStatus.DOWN);
  }

  @Test
  void fastResponseIsUp() {
    assertThat(SiteStatus.from(CHECKED_AT, true, 120)).isEqualTo(SiteStatus.UP);
  }

  @Test
  void exactlyOneSecondIsStillUp() {
    assertThat(SiteStatus.from(CHECKED_AT, true, 1000)).isEqualTo(SiteStatus.UP);
  }

  @Test
  void overOneSecondIsSlow() {
    assertThat(SiteStatus.from(CHECKED_AT, true, 1001)).isEqualTo(SiteStatus.SLOW);
  }
}
