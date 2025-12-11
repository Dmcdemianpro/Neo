package cl.hec.neo.fhir.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PatientResourceProvider implements IResourceProvider {

    private Map<String, Patient> patients = new HashMap<>();
    private long idCounter = 1;

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType theId) {
        Patient patient = patients.get(theId.getIdPart());
        if (patient == null) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(theId);
        }
        return patient;
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient thePatient) {
        String id = String.valueOf(idCounter++);
        thePatient.setId(id);
        patients.put(id, thePatient);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", id));
        outcome.setResource(thePatient);
        return outcome;
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient) {
        String id = theId.getIdPart();
        thePatient.setId(id);
        patients.put(id, thePatient);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(theId);
        outcome.setResource(thePatient);
        return outcome;
    }

    @Delete
    public void delete(@IdParam IdType theId) {
        patients.remove(theId.getIdPart());
    }

    @Search
    public List<Patient> search() {
        return new ArrayList<>(patients.values());
    }
}
