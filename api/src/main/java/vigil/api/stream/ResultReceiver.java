package vigil.api.stream;

import org.slf4j.Logger;

import org.springframework.stereotype.Component;

import org.slf4j.LoggerFactory;

import tools.jackson.databind.json.JsonMapper;
import vigil.api.site.SiteStatus;

@Component
public class ResultReceiver{
  private final JsonMapper jsonMapper;
  private final SseService sseService;
  private static final Logger log = LoggerFactory.getLogger(ResultReceiver.class);
  public ResultReceiver(JsonMapper jsonMapper,SseService sseService){
    this.jsonMapper = jsonMapper;
    this.sseService = sseService;
  }
  public void handle(String json){
    try{
      ResultEvent resultEvent = jsonMapper.readValue(json,ResultEvent.class);
      SiteStatus status = SiteStatus.from(resultEvent.checkedAt(),resultEvent.up(),resultEvent.responseTimeMs());
      CheckEvent checkEvent = new CheckEvent (resultEvent.siteId(),status,resultEvent.statusCode(),
          resultEvent.responseTimeMs(),resultEvent.error(),resultEvent.checkedAt());
      String newJson = jsonMapper.writeValueAsString(checkEvent);
      sseService.broadcast(newJson);
    }
    catch(Exception e)  {log.warn("process failed", e);}
  }
}
