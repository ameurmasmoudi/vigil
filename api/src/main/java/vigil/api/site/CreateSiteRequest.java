package vigil.api.site;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSiteRequest(@NotBlank @Size(max =100) String name,
    @NotBlank @Pattern(regexp="^https?://.+") String url){}
