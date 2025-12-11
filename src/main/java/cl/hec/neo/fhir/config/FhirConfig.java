package cl.hec.neo.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import cl.hec.neo.fhir.provider.PatientResourceProvider;
import cl.hec.neo.fhir.provider.PractitionerResourceProvider;
import cl.hec.neo.fhir.provider.OrganizationResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class FhirConfig {

    @Autowired
    private PatientResourceProvider patientResourceProvider;

    @Autowired
    private PractitionerResourceProvider practitionerResourceProvider;

    @Autowired
    private OrganizationResourceProvider organizationResourceProvider;

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServletRegistration(FhirContext fhirContext) {
        RestfulServer servlet = new RestfulServer(fhirContext);

        // Configuración básica
        servlet.setServerName("Neo FHIR Server - Chile");
        servlet.setServerVersion("1.0.0");
        servlet.setDefaultPrettyPrint(true);
        servlet.setFhirContext(fhirContext);

        // Registrar Resource Providers
        servlet.registerProvider(patientResourceProvider);
        servlet.registerProvider(practitionerResourceProvider);
        servlet.registerProvider(organizationResourceProvider);

        // Interceptor para resaltar en navegador
        servlet.registerInterceptor(new ResponseHighlighterInterceptor());

        ServletRegistrationBean<RestfulServer> registration =
            new ServletRegistrationBean<>(servlet, "/fhir/*");
        registration.setLoadOnStartup(1);
        registration.setName("FhirServlet");

        return registration;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(Arrays.asList("Content-Location", "Location"));
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
