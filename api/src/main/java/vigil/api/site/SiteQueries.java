package vigil.api.site;

import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

@Repository
public class SiteQueries {
  private final JdbcClient jdbcClient;
  public SiteQueries(JdbcClient jdbcClient){
    this.jdbcClient= jdbcClient;
  }

  public List<SiteStatusResponse> getStatus(){
    return jdbcClient.sql("""
    select S.id,S.name,S.url,C.up,C.response_time_ms,C.checked_at
    from sites S left join lateral 
    ( select * from check_results C 
    where S.id = C.site_id 
    order by C.checked_at DESC limit 1 )C on true order by S.id ASC""").query((rs,rowNum) -> new SiteStatusResponse(
    rs.getInt("id"),
    rs.getString("name"),
    rs.getString("url"),
    statusOf(rs.getBoolean("up"),rs.getObject("response_time_ms", Integer.class),rs.getObject("checked_at", OffsetDateTime.class )),
    rs.getObject("response_time_ms", Integer.class),
    rs.getObject("checked_at", OffsetDateTime.class ))).list();
    }

  private SiteStatus statusOf(Boolean up,Integer responseTimeMs,OffsetDateTime checkedAt){
    if (checkedAt==null) return SiteStatus.PENDING;
    if (!up) return SiteStatus.DOWN;
    if (responseTimeMs > 1000) return SiteStatus.SLOW;
    return SiteStatus.UP;
  }

  public List<CheckResponse> checkSite(Integer id, Integer limit){
    return jdbcClient.sql("select * from check_results where site_id= :id order by checked_at DESC Limit :limit ")
            .param("id",id).param("limit",limit).query((rs,rowNum)-> new CheckResponse(
            rs.getBoolean("up"),
            rs.getObject("status_code", Integer.class),
            rs.getObject("response_time_ms", Integer.class),
            rs.getObject("error",String.class),
            rs.getObject("checked_at",OffsetDateTime.class))).list();
  }

  public List<CheckSummaryResponse> checkSummary(Integer id,Integer hours){
    return jdbcClient.sql("""
        select date_bin('10 minutes', checked_at, timestamptz '2000-01-01') as time_bucket, round(avg(response_time_ms))::int as resp_avg,
        max(response_time_ms) as resp_max, count(*) as check_count, count(*) filter(where not up) as fail_check_count from check_results where site_id = :id and checked_at between now()- :hours * interval '1 hour' and now()
        group by time_bucket order by time_bucket ASC""").param("id",id).param("hours",hours).query((rs,rowNum) -> new CheckSummaryResponse(
        rs.getObject("time_bucket",OffsetDateTime.class),
        rs.getObject("resp_avg",Integer.class),
        rs.getObject("resp_max",Integer.class),
        rs.getInt("check_count"),
        rs.getInt("fail_check_count"))).list();
  }
}
