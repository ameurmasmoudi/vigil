package vigil.api.site;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Generated;

@Entity
@Table(name="sites")
public class Site {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  private String name;
  private String url;
  @Generated
  @Column(insertable = false, updatable = false)
  private Instant createdAt;
  protected Site(){}
  public Site(String name, String url){
    this.name = name;
    this.url = url;
  }
  public String getUrl(){return this.url;}
  public String getName(){return this.name;}
  public Integer getId(){return this.id;}
  public Instant getCreatedAt(){return this.createdAt;}
}
