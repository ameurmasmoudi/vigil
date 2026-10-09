package vigil.worker;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;

@Component
public class SiteChecker{
  private final HttpClient client;
  private final long requestTimeout;
  public SiteChecker(@Value("${vigil.connect-timeout}") long connectTimeoutMs ,@Value("${vigil.request-timeout}") long requestTimeoutMs){
    this.client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build();
    this.requestTimeout= requestTimeoutMs;
  }

  private boolean checkUp(Integer status){
    return((status >= 200) && (status <= 399));
  } 

  private int elapsedMs(long start){
    return (int)((System.nanoTime() - start) /1_000_000);
  }

  private String errorText(Exception e){

    String longerror = e.getMessage() != null ? e.getMessage(): e.getClass().getSimpleName();
    return longerror.substring(0, Math.min(500, longerror.length()));
  }

  public CheckResult checkSite(String url){
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
      .timeout(Duration.ofMillis(requestTimeout))
      .build();
    long start = System.nanoTime();
    try{
    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
    return new CheckResult(checkUp(response.statusCode()),response.statusCode(),elapsedMs(start),null,OffsetDateTime.now());
    }
    catch (HttpTimeoutException e) {
      return new CheckResult(false,null,(int)requestTimeout,"time out",OffsetDateTime.now());
    }
    catch (IOException e) {
      return new CheckResult(false,null,elapsedMs(start),errorText(e),OffsetDateTime.now());
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CheckResult(false,null,elapsedMs(start),errorText(e),OffsetDateTime.now());
    }
  } 
  
}
