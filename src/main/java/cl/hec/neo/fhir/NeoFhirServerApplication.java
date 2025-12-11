package cl.hec.neo.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;

@SpringBootApplication(exclude = {
    ElasticsearchRestClientAutoConfiguration.class,
    OAuth2ClientAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
public class NeoFhirServerApplication {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          🏥  NEO FHIR SERVER - CHILE  🇨🇱                ║");
        System.out.println("║          Servidor FHIR R4 con Perfiles CL-CORE          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        
        SpringApplication.run(NeoFhirServerApplication.class, args);
        
        System.out.println("✅ Servidor FHIR iniciado correctamente");
        System.out.println("📍 FHIR Base: http://localhost:8080/fhir");
    }
}
