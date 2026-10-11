package vigil.worker;

import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CheckWorker implements SmartLifecycle{
  private final StringRedisTemplate stringRedisTemplate;
  private final JsonMapper jsonMapper;
  private final SiteChecker siteChecker;
  private final Semaphore gatekeep;
  private final int concurrency;
  private final int staleThreshold;
  private final int requestTimeout;
  private final int connectTimeout;
  private final int redisTimeout;
  private volatile boolean running;
  private final ResultStore resultStore;
  private final Counter checksUp;
  private final Counter checksDown;
  private final Timer durationUp;
  private final Timer durationDown;
  private static final Logger log = LoggerFactory.getLogger(CheckWorker.class);
  private Thread workingThread;
  private final AtomicInteger threadCount = new AtomicInteger(0);
  private static final int POP_WAIT_MS = 1000; 


  public CheckWorker (StringRedisTemplate stringRedisTemplate
      ,JsonMapper jsonMapper
      ,SiteChecker siteChecker
      ,@Value("${vigil.concurrency}") int concurrency
      ,@Value("${vigil.stale-threshold}") int staleThreshold
      ,@Value("${vigil.request-timeout}") int requestTimeout
      ,@Value("${vigil.connect-timeout}") int connectTimeout
      ,@Value("${spring.data.redis.timeout}") int redisTimeout
      ,ResultStore resultStore
      ,MeterRegistry meterRegistry){
    this.stringRedisTemplate = stringRedisTemplate;
    this.jsonMapper = jsonMapper;
    this.siteChecker = siteChecker;
    this.concurrency = concurrency;
    this.gatekeep = new Semaphore(concurrency);
    this.staleThreshold = staleThreshold;
    this.resultStore = resultStore;
    this.requestTimeout = requestTimeout;
    this.connectTimeout = connectTimeout;
    this.redisTimeout = redisTimeout;
    this.checksUp = Counter.builder("vigil.checks")
      .tag("result", "up")
      .description("checks performed, by result")
      .register(meterRegistry);
    this.checksDown = Counter.builder("vigil.checks")
      .tag("result", "down")
      .description("checks performed, by result")
      .register(meterRegistry);
    this.durationUp = Timer.builder("vigil.checks.duration")
      .tag("result", "up")
      .publishPercentileHistogram()
      .register(meterRegistry);
    this.durationDown = Timer.builder("vigil.checks.duration")
      .tag("result", "down")
      .publishPercentileHistogram()
      .register(meterRegistry);
    Gauge.builder("vigil.checks.in.flight", threadCount,s -> s.get())
      .description("checks currently running")
      .register(meterRegistry);
  }

  public void start(){
    running = true;
    workingThread = Thread.ofPlatform().start(this::loop);
  }

  private int elapsedMs(long start){
    return (int)((System.nanoTime() - start) /1_000_000);
  }

  public void stop(){
    running = false;
    log.info("stopping the worker with {} in-flight checks remaining",threadCount.get());
    long start = System.nanoTime();
    try{
    workingThread.join(POP_WAIT_MS + 500);
      if (workingThread.isAlive()){
        log.warn("thread still running after timeout");
        workingThread.interrupt();  //the loss of data is fine because it's regenrated every minute
      }
    }
    catch( InterruptedException e){ Thread.currentThread().interrupt();}
    try {
      boolean finished = gatekeep.tryAcquire(concurrency,requestTimeout + redisTimeout + connectTimeout + 1000 ,TimeUnit.MILLISECONDS); // 1000 is the db margin
      if (!finished){
        log.warn("{} checks still running after shutdown",threadCount.get());
      }
    }
    catch( InterruptedException e){ Thread.currentThread().interrupt();}
    log.info("stopped after {} ms",elapsedMs(start));
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
        String json = stringRedisTemplate.opsForList().rightPop("vigil:checks",POP_WAIT_MS,TimeUnit.MILLISECONDS);
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
        threadCount.incrementAndGet();
        Thread.ofVirtual().start(() ->{
          try{
            CheckResult checkResult = siteChecker.checkSite(checkJob.url());
            (checkResult.up() ? checksUp : checksDown).increment();
            (checkResult.up() ? durationUp : durationDown)
              .record(Duration.ofMillis(checkResult.responseTimeMs()));
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
            threadCount.decrementAndGet();
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
