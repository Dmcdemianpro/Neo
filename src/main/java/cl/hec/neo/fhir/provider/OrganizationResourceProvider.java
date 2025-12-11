package cl.hec.neo.fhir.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OrganizationResourceProvider implements IResourceProvider {

    private Map<String, Organization> organizations = new HashMap<>();
    private long idCounter = 1;

    @Override
    public Class<Organization> getResourceType() {
        return Organization.class;
    }

    @Read
    public Organization read(@IdParam IdType theId) {
        Organization organization = organizations.get(theId.getIdPart());
        if (organization == null) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(theId);
        }
        return organization;
    }

    @Create
    public MethodOutcome create(@ResourceParam Organization theOrganization) {
        String id = String.valueOf(idCounter++);
        theOrganization.setId(id);
        organizations.put(id, theOrganization);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Organization", id));
        outcome.setResource(theOrganization);
        return outcome;
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Organization theOrganization) {
        String id = theId.getIdPart();
        theOrganization.setId(id);
        organizations.put(id, theOrganization);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(theId);
        outcome.setResource(theOrganization);
        return outcome;
    }

    @Delete
    public void delete(@IdParam IdType theId) {
        organizations.remove(theId.getIdPart());
    }

    @Search
    public List<Organization> search() {
        return new ArrayList<>(organizations.values());
    }
}
