package vigil.worker;

import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CheckWorker implements SmartLifecycle{
  private final StringRedisTemplate stringRedisTemplate;
  private final JsonMapper jsonMapper;
  private final SiteChecker siteChecker;
  private final Semaphore gatekeep;
  private final long staleThreshold;
  private volatile boolean running;
  private final ResultStore resultStore;
  private static final Logger log = LoggerFactory.getLogger(CheckWorker.class);

  public CheckWorker (StringRedisTemplate stringRedisTemplate
      ,JsonMapper jsonMapper
      ,SiteChecker siteChecker
      ,@Value("${vigil.concurrency}") Integer concurrency
      ,@Value("${vigil.stale-threshold}") int staleThreshold
      ,ResultStore resultStore){
    this.stringRedisTemplate = stringRedisTemplate;
    this.jsonMapper = jsonMapper;
    this.siteChecker = siteChecker;
    this.gatekeep = new Semaphore(concurrency);
    this.staleThreshold = staleThreshold;
    this.resultStore = resultStore;
  }

  public void start(){
    running = true;
    Thread.ofPlatform().start(this::loop);
  }

  public void stop(){
    running = false;
  }

  private void pause(){
  try{
        Thread.sleep(1000);
  }
  catch( InterruptedException e){ Thread.currentThread().interrupt();}
  }

  public boolean isRunning(){ return running;}
  private void loop(){
    while (isRunning()){
      boolean holdingPermit = false;
      try {
        gatekeep.acquire();
        holdingPermit = true;
        String json = stringRedisTemplate.opsForList().rightPop("vigil:checks",6,TimeUnit.SECONDS);
        if (json==null) {
          gatekeep.release();
          holdingPermit = false;
          continue;
        }
        CheckJob checkJob = jsonMapper.readValue(json, CheckJob.class);
        if ((Duration.between(checkJob.scheduledAt(),OffsetDateTime.now()).toMillis())>staleThreshold){
          log.warn("job is stale");
          gatekeep.release();
          holdingPermit = false;
          continue;
        }
        Thread.ofVirtual().start(() ->{
          try{
            CheckResult checkResult = siteChecker.checkSite(checkJob.url());
            if (resultStore.saveResult(checkJob.siteId(),checkResult)){
              String resultJson = jsonMapper.writeValueAsString(new ResultEvent(checkJob.siteId(),
                    checkResult.up(),checkResult.statusCode(),checkResult.responseTimeMs(),checkResult.error(),
                    checkResult.checkedAt()));
            stringRedisTemplate.convertAndSend("vigil:results", resultJson);

            }
          }
          catch(Exception e){
            log.warn("check failed for {}",checkJob.url(),e);
          }
          finally{
            gatekeep.release();
          }
        });
        holdingPermit = false;
      }
      catch(InterruptedException e){
        Thread.currentThread().interrupt();
        break;
      }

      catch(Exception e){
        log.warn("run failed",e);
        if (holdingPermit) gatekeep.release();
        pause();
      }
      
      }
  }
}
