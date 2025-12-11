package cl.hec.neo.fhir.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PractitionerResourceProvider implements IResourceProvider {

    private Map<String, Practitioner> practitioners = new HashMap<>();
    private long idCounter = 1;

    @Override
    public Class<Practitioner> getResourceType() {
        return Practitioner.class;
    }

    @Read
    public Practitioner read(@IdParam IdType theId) {
        Practitioner practitioner = practitioners.get(theId.getIdPart());
        if (practitioner == null) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(theId);
        }
        return practitioner;
    }

    @Create
    public MethodOutcome create(@ResourceParam Practitioner thePractitioner) {
        String id = String.valueOf(idCounter++);
        thePractitioner.setId(id);
        practitioners.put(id, thePractitioner);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Practitioner", id));
        outcome.setResource(thePractitioner);
        return outcome;
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Practitioner thePractitioner) {
        String id = theId.getIdPart();
        thePractitioner.setId(id);
        practitioners.put(id, thePractitioner);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(theId);
        outcome.setResource(thePractitioner);
        return outcome;
    }

    @Delete
    public void delete(@IdParam IdType theId) {
        practitioners.remove(theId.getIdPart());
    }

    @Search
    public List<Practitioner> search() {
        return new ArrayList<>(practitioners.values());
    }
}
