package vigil.api.site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Integer> {
  boolean existsByUrl(String url);
}
