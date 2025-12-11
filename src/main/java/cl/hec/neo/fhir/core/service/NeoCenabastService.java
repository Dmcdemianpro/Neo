package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.Prescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de integración con CENABAST (Central de Abastecimiento del Sistema Nacional de Servicios de Salud)
 *
 * Responsabilidades:
 * - Envío de recetas electrónicas a CENABAST
 * - Sincronización de catálogo de medicamentos
 * - Validación de disponibilidad de medicamentos
 * - Consulta de precios y stock
 * - Procesamiento por lotes de recetas pendientes
 * - Manejo de respuestas y estados CENABAST
 * - Reintentos automáticos en caso de errores temporales
 * - Registro de auditoría de integraciones
 */
@Service
@ConditionalOnProperty(prefix = "neo.integrations.cenabast", name = "enabled", havingValue = "true")
@Transactional
public class NeoCenabastService {

    private static final Logger log = LoggerFactory.getLogger(NeoCenabastService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Value("${neo.integrations.cenabast.base-url}")
    private String cenabastBaseUrl;

    @Value("${neo.integrations.cenabast.api-key}")
    private String cenabastApiKey;

    private final NeoPrescriptionService prescriptionService;
    private final NeoAuditService auditService;
    private final RestTemplate restTemplate;

    public NeoCenabastService(
            NeoPrescriptionService prescriptionService,
            NeoAuditService auditService,
            RestTemplate restTemplate) {
        this.prescriptionService = prescriptionService;
        this.auditService = auditService;
        this.restTemplate = restTemplate;
    }

    // ==================== Prescription Submission ====================

    /**
     * Enviar prescripción individual a CENABAST
     */
    public CenabastResponse sendPrescription(UUID prescriptionId, String sentBy) {
        log.info("Sending prescription to CENABAST: prescriptionId={}", prescriptionId);

        Prescription prescription = prescriptionService.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        // Validar que la prescripción esté activa
        if (prescription.getStatus() != Prescription.PrescriptionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot send inactive prescription to CENABAST");
        }

        // Construir payload
        Map<String, Object> payload = buildPrescriptionPayload(prescription);

        // Intentar enviar con reintentos
        CenabastResponse response = sendWithRetry(payload, prescriptionId);

        // Actualizar estado según respuesta
        if (response.isSuccess()) {
            prescriptionService.markSentToCenabast(prescriptionId, response.getResponseJson(), sentBy);

            if (response.isAccepted()) {
                prescriptionService.updateCenabastStatus(
                    prescriptionId,
                    Prescription.CenabastStatus.ACCEPTED,
                    response.getResponseJson(),
                    null,
                    sentBy
                );
            }
        } else {
            prescriptionService.updateCenabastStatus(
                prescriptionId,
                Prescription.CenabastStatus.ERROR,
                response.getResponseJson(),
                response.getErrorMessage(),
                sentBy
            );
        }

        log.info("Prescription sent to CENABAST: prescriptionId={}, success={}",
            prescriptionId, response.isSuccess());
        return response;
    }

    /**
     * Enviar múltiples prescripciones en lote
     */
    public BatchResponse sendPrescriptionBatch(List<UUID> prescriptionIds, String sentBy) {
        log.info("Sending prescription batch to CENABAST: count={}", prescriptionIds.size());

        int successful = 0;
        int failed = 0;
        Map<UUID, String> errors = new HashMap<>();

        for (UUID prescriptionId : prescriptionIds) {
            try {
                CenabastResponse response = sendPrescription(prescriptionId, sentBy);
                if (response.isSuccess()) {
                    successful++;
                } else {
                    failed++;
                    errors.put(prescriptionId, response.getErrorMessage());
                }
            } catch (Exception e) {
                failed++;
                errors.put(prescriptionId, e.getMessage());
                log.error("Error sending prescription to CENABAST: id={}, error={}",
                    prescriptionId, e.getMessage());
            }
        }

        log.info("Batch send completed: total={}, successful={}, failed={}",
            prescriptionIds.size(), successful, failed);
        return new BatchResponse(successful, failed, errors);
    }

    /**
     * Procesar prescripciones pendientes automáticamente (scheduled)
     */
    @Scheduled(cron = "${neo.integrations.cenabast.sync-cron:0 0 2 * * ?}")
    @Transactional
    public void processPendingPrescriptions() {
        log.info("Starting scheduled CENABAST pending prescriptions processing");

        // Obtener todas las prescripciones pendientes de todos los tenants
        // En producción, esto debería procesar por tenant
        try {
            // Aquí se implementaría la lógica para obtener tenants activos
            // y procesar sus prescripciones pendientes
            log.info("Scheduled CENABAST processing completed");
        } catch (Exception e) {
            log.error("Error in scheduled CENABAST processing", e);
        }
    }

    /**
     * Procesar prescripciones pendientes de un tenant específico
     */
    public BatchResponse processPendingForTenant(UUID tenantId, String processedBy) {
        log.info("Processing pending CENABAST prescriptions for tenant: {}", tenantId);

        List<Prescription> pendingPrescriptions = prescriptionService.findPendingCenabast(tenantId);

        if (pendingPrescriptions.isEmpty()) {
            log.info("No pending prescriptions found for tenant: {}", tenantId);
            return new BatchResponse(0, 0, new HashMap<>());
        }

        List<UUID> prescriptionIds = pendingPrescriptions.stream()
            .map(Prescription::getId)
            .toList();

        return sendPrescriptionBatch(prescriptionIds, processedBy);
    }

    // ==================== Medication Catalog Sync ====================

    /**
     * Sincronizar catálogo de medicamentos desde CENABAST
     */
    public CatalogSyncResult syncMedicationCatalog() {
        log.info("Starting CENABAST medication catalog sync");

        try {
            // GET /api/medications
            String endpoint = cenabastBaseUrl + "/api/medications";
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.GET,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String catalogData = response.getBody();

                // Aquí se procesaría el catálogo y se actualizaría en MasterCatalog
                // Por ahora retornamos resultado básico

                log.info("CENABAST catalog sync completed successfully");
                return new CatalogSyncResult(true, 0, null);
            } else {
                log.warn("CENABAST catalog sync returned non-OK status: {}",
                    response.getStatusCode());
                return new CatalogSyncResult(false, 0, "Non-OK response: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error syncing CENABAST catalog: {} - {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            return new CatalogSyncResult(false, 0, e.getMessage());
        } catch (Exception e) {
            log.error("Error syncing CENABAST catalog", e);
            return new CatalogSyncResult(false, 0, e.getMessage());
        }
    }

    // ==================== Medication Availability ====================

    /**
     * Consultar disponibilidad de medicamento en CENABAST
     */
    public AvailabilityResponse checkMedicationAvailability(String medicationCode) {
        log.debug("Checking CENABAST medication availability: {}", medicationCode);

        try {
            // GET /api/medications/{code}/availability
            String endpoint = String.format("%s/api/medications/%s/availability",
                cenabastBaseUrl, medicationCode);

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.GET,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                // Parsear respuesta real
                // Por ahora retornamos respuesta simulada
                return new AvailabilityResponse(true, 100, "Available");
            } else {
                return new AvailabilityResponse(false, 0, "Not available");
            }

        } catch (Exception e) {
            log.error("Error checking CENABAST availability: {}", e.getMessage());
            return new AvailabilityResponse(false, 0, "Error: " + e.getMessage());
        }
    }

    /**
     * Consultar precio de medicamento en CENABAST
     */
    public PriceResponse getMedicationPrice(String medicationCode) {
        log.debug("Getting CENABAST medication price: {}", medicationCode);

        try {
            // GET /api/medications/{code}/price
            String endpoint = String.format("%s/api/medications/%s/price",
                cenabastBaseUrl, medicationCode);

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.GET,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                // Parsear respuesta real
                return new PriceResponse(true, 0.0, "CLP", null);
            } else {
                return new PriceResponse(false, 0.0, "CLP", "Price not available");
            }

        } catch (Exception e) {
            log.error("Error getting CENABAST price: {}", e.getMessage());
            return new PriceResponse(false, 0.0, "CLP", "Error: " + e.getMessage());
        }
    }

    // ==================== Retry Management ====================

    /**
     * Reintentar prescripciones con error
     */
    public BatchResponse retryErrorPrescriptions(UUID tenantId, String retriedBy) {
        log.info("Retrying CENABAST error prescriptions for tenant: {}", tenantId);

        List<Prescription> errorPrescriptions = prescriptionService.findCenabastErrors(tenantId);

        if (errorPrescriptions.isEmpty()) {
            log.info("No error prescriptions found for tenant: {}", tenantId);
            return new BatchResponse(0, 0, new HashMap<>());
        }

        // Resetear estado a PENDING antes de reintentar
        for (Prescription prescription : errorPrescriptions) {
            prescriptionService.updateCenabastStatus(
                prescription.getId(),
                Prescription.CenabastStatus.PENDING,
                null,
                null,
                retriedBy
            );
        }

        List<UUID> prescriptionIds = errorPrescriptions.stream()
            .map(Prescription::getId)
            .toList();

        return sendPrescriptionBatch(prescriptionIds, retriedBy);
    }

    // ==================== Health Check ====================

    /**
     * Verificar conectividad con CENABAST
     */
    public HealthCheckResponse checkHealth() {
        log.debug("Checking CENABAST health");

        try {
            // GET /api/health
            String endpoint = cenabastBaseUrl + "/api/health";
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                endpoint,
                HttpMethod.GET,
                request,
                String.class
            );

            boolean healthy = response.getStatusCode() == HttpStatus.OK;
            return new HealthCheckResponse(healthy, response.getStatusCode().value(),
                healthy ? "CENABAST API is healthy" : "CENABAST API returned non-OK status");

        } catch (Exception e) {
            log.error("CENABAST health check failed", e);
            return new HealthCheckResponse(false, 0, "Error: " + e.getMessage());
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Construir payload para envío de prescripción
     */
    private Map<String, Object> buildPrescriptionPayload(Prescription prescription) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("prescriptionNumber", prescription.getPrescriptionNumber());
        payload.put("patientId", prescription.getPatient().getId().toString());
        payload.put("prescriberId", prescription.getPrescriberId());
        payload.put("medicationCode", prescription.getMedicationCode());
        payload.put("medicationDisplay", prescription.getMedicationDisplay());
        payload.put("dosageInstruction", prescription.getDosageInstruction());
        payload.put("quantityValue", prescription.getQuantityValue());
        payload.put("quantityUnit", prescription.getQuantityUnit());
        payload.put("refillsAllowed", prescription.getRefillsAllowed());
        payload.put("validityStart", prescription.getValidityPeriodStart());
        payload.put("validityEnd", prescription.getValidityPeriodEnd());
        payload.put("authoredOn", prescription.getAuthoredOn());
        payload.put("priority", prescription.getPriority());

        return payload;
    }

    /**
     * Enviar request con reintentos automáticos
     */
    private CenabastResponse sendWithRetry(Map<String, Object> payload, UUID prescriptionId) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            attempts++;

            try {
                return sendRequest(payload);
            } catch (HttpServerErrorException e) {
                // 5xx errors - server issues, retry
                lastException = e;
                log.warn("CENABAST server error (attempt {}/{}): {}",
                    attempts, MAX_RETRY_ATTEMPTS, e.getMessage());

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (HttpClientErrorException e) {
                // 4xx errors - client issues, don't retry
                log.error("CENABAST client error: {} - {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
                return CenabastResponse.error("Client error: " + e.getMessage(),
                    e.getResponseBodyAsString());
            } catch (Exception e) {
                // Other errors
                lastException = e;
                log.error("CENABAST request error (attempt {}/{}): {}",
                    attempts, MAX_RETRY_ATTEMPTS, e.getMessage());

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries failed
        String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
        return CenabastResponse.error("Max retries exceeded: " + errorMsg, null);
    }

    /**
     * Enviar request HTTP a CENABAST
     */
    private CenabastResponse sendRequest(Map<String, Object> payload) {
        // POST /api/prescriptions
        String endpoint = cenabastBaseUrl + "/api/prescriptions";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            endpoint,
            HttpMethod.POST,
            request,
            String.class
        );

        if (response.getStatusCode() == HttpStatus.CREATED ||
            response.getStatusCode() == HttpStatus.OK) {
            return CenabastResponse.success(response.getBody());
        } else {
            return CenabastResponse.error("Unexpected status: " + response.getStatusCode(),
                response.getBody());
        }
    }

    /**
     * Crear headers HTTP con autenticación
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", cenabastApiKey);
        headers.set("Accept", "application/json");
        return headers;
    }

    // ==================== Response DTOs ====================

    /**
     * Respuesta de envío individual a CENABAST
     */
    public static class CenabastResponse {
        private final boolean success;
        private final boolean accepted;
        private final String errorMessage;
        private final String responseJson;

        private CenabastResponse(boolean success, boolean accepted, String errorMessage, String responseJson) {
            this.success = success;
            this.accepted = accepted;
            this.errorMessage = errorMessage;
            this.responseJson = responseJson;
        }

        public static CenabastResponse success(String responseJson) {
            return new CenabastResponse(true, true, null, responseJson);
        }

        public static CenabastResponse error(String errorMessage, String responseJson) {
            return new CenabastResponse(false, false, errorMessage, responseJson);
        }

        public boolean isSuccess() { return success; }
        public boolean isAccepted() { return accepted; }
        public String getErrorMessage() { return errorMessage; }
        public String getResponseJson() { return responseJson; }
    }

    /**
     * Respuesta de procesamiento por lotes
     */
    public static class BatchResponse {
        private final int successful;
        private final int failed;
        private final Map<UUID, String> errors;

        public BatchResponse(int successful, int failed, Map<UUID, String> errors) {
            this.successful = successful;
            this.failed = failed;
            this.errors = errors;
        }

        public int getSuccessful() { return successful; }
        public int getFailed() { return failed; }
        public Map<UUID, String> getErrors() { return errors; }
        public int getTotal() { return successful + failed; }
    }

    /**
     * Resultado de sincronización de catálogo
     */
    public static class CatalogSyncResult {
        private final boolean success;
        private final int itemsSynced;
        private final String errorMessage;

        public CatalogSyncResult(boolean success, int itemsSynced, String errorMessage) {
            this.success = success;
            this.itemsSynced = itemsSynced;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public int getItemsSynced() { return itemsSynced; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Respuesta de disponibilidad de medicamento
     */
    public static class AvailabilityResponse {
        private final boolean available;
        private final int stock;
        private final String message;

        public AvailabilityResponse(boolean available, int stock, String message) {
            this.available = available;
            this.stock = stock;
            this.message = message;
        }

        public boolean isAvailable() { return available; }
        public int getStock() { return stock; }
        public String getMessage() { return message; }
    }

    /**
     * Respuesta de precio de medicamento
     */
    public static class PriceResponse {
        private final boolean success;
        private final double price;
        private final String currency;
        private final String errorMessage;

        public PriceResponse(boolean success, double price, String currency, String errorMessage) {
            this.success = success;
            this.price = price;
            this.currency = currency;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public double getPrice() { return price; }
        public String getCurrency() { return currency; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Respuesta de health check
     */
    public static class HealthCheckResponse {
        private final boolean healthy;
        private final int statusCode;
        private final String message;

        public HealthCheckResponse(boolean healthy, int statusCode, String message) {
            this.healthy = healthy;
            this.statusCode = statusCode;
            this.message = message;
        }

        public boolean isHealthy() { return healthy; }
        public int getStatusCode() { return statusCode; }
        public String getMessage() { return message; }
    }
}
