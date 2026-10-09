package vigil.worker;

import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Repository
public class ResultStore{
  private final JdbcClient jdbcClient;
  private static final Logger log = LoggerFactory.getLogger(ResultStore.class);
  public ResultStore(JdbcClient jdbcClient){
    this.jdbcClient = jdbcClient;
  }
  public boolean saveResult(int siteId, CheckResult result){
    try{
      jdbcClient.sql("""
        insert into check_results (site_id,up,status_code,response_time_ms,error,checked_at)
        values (:site_id,:up,:status_code,:response_time_ms,:error,:checked_at)""")
        .param("site_id",siteId)
        .param("up",result.up())
        .param("status_code",result.statusCode())
        .param("response_time_ms",result.responseTimeMs())
        .param("error",result.error())
        .param("checked_at",result.checkedAt())
        .update();
      return true;
    }
    catch(DataIntegrityViolationException e){
      log.warn("site {} no longer exists",siteId);
      return false;
    }
  }
}

