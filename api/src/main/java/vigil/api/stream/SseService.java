package vigil.api.stream;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
class SseService{
  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  public SseEmitter subscribe(){
    SseEmitter emitter = new SseEmitter(300_000L);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> {
        emitter.complete();
        emitters.remove(emitter);
    });
    emitter.onError((ex) -> emitters.remove(emitter));
    emitters.add(emitter);
    try { 
      emitter.send(SseEmitter.event()
              .name("INIT")
              .data("Connection established successfully"));
    } catch (IOException e) {
      emitters.remove(emitter);
    }
    return emitter;
    }
  public void broadcast(String json){
    for (SseEmitter emitter : emitters){
      try {
        emitter.send(SseEmitter.event().name("check").data(json));
      }
      catch (IOException e){
        emitters.remove(emitter);
      }
    }
  }
}

