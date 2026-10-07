package vigil.api.scheduler;


import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class CheckScheduler{
  private final StringRedisTemplate stringRedisTemplate;
  private final JsonMapper jsonMapper;
  private final JdbcClient jdbcClient;
  public CheckScheduler(JsonMapper jsonMapper,StringRedisTemplate stringRedisTemplate,JdbcClient jdbcClient){
    this.jsonMapper = jsonMapper;
    this.stringRedisTemplate = stringRedisTemplate;
    this.jdbcClient = jdbcClient;
  }
  @Scheduled(fixedRateString = "${vigil.check-interval-ms}")
  public void schedule(){
    List<CheckJob> checkJobs = jdbcClient.sql("select id,url from sites").query((rs,rowNum)->new CheckJob(
      rs.getInt("id"),rs.getString("url"),OffsetDateTime.now())).list();
    if (checkJobs.isEmpty()) return;
    List<String> jsonCheckJobs = new ArrayList<>();
    for (CheckJob job : checkJobs){
         jsonCheckJobs.add(jsonMapper.writeValueAsString(job));
    }
    stringRedisTemplate.opsForList().leftPushAll("vigil:checks",jsonCheckJobs);
  }
}
