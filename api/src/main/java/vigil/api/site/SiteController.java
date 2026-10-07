package vigil.api.site;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


@RestController
@RequestMapping("/api/sites")
public class SiteController{
  private final SiteRepository siteRepository;
  private final SiteQueries siteQueries;
  public SiteController(SiteRepository siteRepository,SiteQueries siteQueries){
    this.siteRepository = siteRepository;
    this.siteQueries = siteQueries;
  }

  @PostMapping()
  @ResponseStatus(HttpStatus.CREATED)
  public SiteStatusResponse createSite(@Valid @RequestBody CreateSiteRequest request){
    if (siteRepository.existsByUrl(request.url())){
      throw new ResponseStatusException(HttpStatus.CONFLICT, "site already exists");
    }
    Site site = siteRepository.save(new Site(request.name(),request.url()));
    return new SiteStatusResponse(site.getId(),site.getName(),site.getUrl(),SiteStatus.PENDING,null,null);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSite(@PathVariable Integer id){
    if (!siteRepository.existsById(id)){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"site not found");
    }
    siteRepository.deleteById(id);
  }

  @GetMapping()
  @ResponseStatus(HttpStatus.OK)
  public List<SiteStatusResponse> listStatus() {return siteQueries.getStatus();}

  @GetMapping("/{id}/checks")
  @ResponseStatus(HttpStatus.OK)
  public List<CheckResponse> checkSite(@PathVariable Integer id,@RequestParam(defaultValue="60") @Min(1) @Max(200) Integer limit){
    if (!siteRepository.existsById(id)){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"site not found");
    }
    return siteQueries.checkSite(id, limit); 
  }

  @GetMapping("/{id}/history")
  @ResponseStatus(HttpStatus.OK)
  public List<CheckSummaryResponse> checkSiteSummary(@PathVariable Integer id,@RequestParam(defaultValue ="24") @Min(1) @Max(168) Integer hours){
    if (!siteRepository.existsById(id)){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"site not found");
    }
    return siteQueries.checkSummary(id,hours);  
  }

}
