-- ============================================================================
-- NEO CORE SCHEMA - V1 Initial Migration
-- Modelo de datos completo para CDR/DDR (Clinical/Demographic Data Repository)
-- ============================================================================

-- ============================================================================
-- MULTI-TENANCY
-- ============================================================================

CREATE TABLE IF NOT EXISTS tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50), -- 'hospital', 'clinic', 'lab', 'pharmacy'
    status VARCHAR(20) DEFAULT 'active', -- 'active', 'suspended', 'inactive'
    config_json JSONB, -- tenant-specific configs
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_tenant_code ON tenant(code);
CREATE INDEX idx_tenant_status ON tenant(status);

COMMENT ON TABLE tenant IS 'Multi-tenancy: Organizaciones/Hospitales independientes en la plataforma';
COMMENT ON COLUMN tenant.config_json IS 'Configuraciones específicas por tenant (límites, features habilitados, etc.)';

-- ============================================================================
-- MASTER PATIENT INDEX (MPI)
-- ============================================================================

CREATE TABLE IF NOT EXISTS person (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    birth_date DATE,
    gender VARCHAR(20), -- 'male', 'female', 'other', 'unknown'
    deceased_boolean BOOLEAN DEFAULT FALSE,
    deceased_datetime TIMESTAMP,
    marital_status VARCHAR(50),
    active BOOLEAN DEFAULT TRUE,
    merged_into_id UUID REFERENCES person(id), -- if this person was merged
    match_score DECIMAL(3,2), -- similarity score if duplicate candidate
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP, -- soft delete
    CONSTRAINT chk_match_score CHECK (match_score >= 0 AND match_score <= 1)
);

CREATE INDEX idx_person_tenant ON person(tenant_id);
CREATE INDEX idx_person_birth_date ON person(birth_date);
CREATE INDEX idx_person_active ON person(active);
CREATE INDEX idx_person_merged ON person(merged_into_id) WHERE merged_into_id IS NOT NULL;
CREATE INDEX idx_person_deleted ON person(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE person IS 'Master Patient Index - Entidad central de pacientes con soporte para deduplicación';
COMMENT ON COLUMN person.merged_into_id IS 'Si este registro fue fusionado, apunta al registro maestro';
COMMENT ON COLUMN person.match_score IS 'Score de coincidencia (0.0-1.0) para candidatos duplicados';

CREATE TABLE IF NOT EXISTS person_identifier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    system VARCHAR(255) NOT NULL, -- e.g., 'https://api.cl/system/run'
    value VARCHAR(100) NOT NULL,
    use VARCHAR(20), -- 'official', 'temp', 'secondary', 'old'
    type_code VARCHAR(50), -- 'RUN', 'PASSPORT', 'FONASA', 'HIS_LOCAL'
    type_text VARCHAR(100),
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    assigner VARCHAR(255), -- organization that assigned the ID
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(system, value, person_id)
);

CREATE INDEX idx_person_id_person ON person_identifier(person_id);
CREATE INDEX idx_person_id_system_value ON person_identifier(system, value);
CREATE INDEX idx_person_id_value ON person_identifier(value);
CREATE INDEX idx_person_id_type ON person_identifier(type_code);

COMMENT ON TABLE person_identifier IS 'Identificadores de pacientes (RUN, Pasaporte, FONASA, IDs locales)';

CREATE TABLE IF NOT EXISTS person_name (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    use VARCHAR(20), -- 'official', 'usual', 'nickname', 'maiden'
    text VARCHAR(500), -- full formatted name
    family VARCHAR(255), -- apellidos
    given TEXT[], -- nombres (array)
    prefix TEXT[], -- 'Dr.', 'Sr.', etc.
    suffix TEXT[], -- 'Jr.', 'III', etc.
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_person_name_person ON person_name(person_id);
CREATE INDEX idx_person_name_family ON person_name(family);
CREATE INDEX idx_person_name_given ON person_name USING gin(given);

COMMENT ON TABLE person_name IS 'Nombres de pacientes (múltiples nombres por persona, histórico)';

CREATE TABLE IF NOT EXISTS person_telecom (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    system VARCHAR(20) NOT NULL, -- 'phone', 'email', 'fax', 'url', 'sms'
    value VARCHAR(255) NOT NULL,
    use VARCHAR(20), -- 'home', 'work', 'mobile', 'temp', 'old'
    rank INTEGER, -- preference order (1 = highest)
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_person_telecom_person ON person_telecom(person_id);
CREATE INDEX idx_person_telecom_value ON person_telecom(value);

COMMENT ON TABLE person_telecom IS 'Contactos telefónicos y electrónicos de pacientes';

CREATE TABLE IF NOT EXISTS person_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    use VARCHAR(20), -- 'home', 'work', 'temp', 'old', 'billing'
    type VARCHAR(20), -- 'postal', 'physical', 'both'
    text VARCHAR(500), -- full formatted address
    line TEXT[], -- street address (array)
    city VARCHAR(100),
    district VARCHAR(100), -- comuna en Chile
    state VARCHAR(100), -- región en Chile
    postal_code VARCHAR(20),
    country VARCHAR(2), -- ISO 3166-1 alpha-2
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    lat DECIMAL(10, 8), -- geolocation
    lon DECIMAL(11, 8),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_person_address_person ON person_address(person_id);
CREATE INDEX idx_person_address_city ON person_address(city);
CREATE INDEX idx_person_address_district ON person_address(district);

COMMENT ON TABLE person_address IS 'Direcciones de pacientes con soporte para geolocalización';

CREATE TABLE IF NOT EXISTS person_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    relationship_code VARCHAR(50), -- 'emergency', 'family', 'guardian', etc.
    relationship_text VARCHAR(255),
    name_text VARCHAR(500),
    name_family VARCHAR(255),
    name_given TEXT[],
    telecom_system VARCHAR(20),
    telecom_value VARCHAR(255),
    address_text VARCHAR(500),
    gender VARCHAR(20),
    organization VARCHAR(255),
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_person_contact_person ON person_contact(person_id);

COMMENT ON TABLE person_contact IS 'Contactos de emergencia y familiares de pacientes';

CREATE TABLE IF NOT EXISTS person_merge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_person_id UUID NOT NULL REFERENCES person(id),
    target_person_id UUID NOT NULL REFERENCES person(id),
    merged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    merged_by VARCHAR(100) NOT NULL,
    reason TEXT,
    merge_strategy VARCHAR(50), -- 'keep_target', 'manual', 'automatic'
    rollback_data JSONB, -- to support un-merge if needed
    UNIQUE(source_person_id)
);

CREATE INDEX idx_person_merge_source ON person_merge(source_person_id);
CREATE INDEX idx_person_merge_target ON person_merge(target_person_id);
CREATE INDEX idx_person_merge_date ON person_merge(merged_at);

COMMENT ON TABLE person_merge IS 'Historial de fusiones de pacientes duplicados';

-- ============================================================================
-- EHR (Electronic Health Record)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ehr (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES person(id),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    ehr_id VARCHAR(100) NOT NULL UNIQUE, -- external EHR identifier
    status VARCHAR(20) DEFAULT 'active', -- 'active', 'inactive'
    time_created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    system_id VARCHAR(100), -- originating system
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ehr_person ON ehr(person_id);
CREATE INDEX idx_ehr_tenant ON ehr(tenant_id);
CREATE UNIQUE INDEX idx_ehr_ehr_id ON ehr(ehr_id);

COMMENT ON TABLE ehr IS 'Electronic Health Record - Historia clínica electrónica por paciente';

-- ============================================================================
-- CLINICAL EPISODES
-- ============================================================================

CREATE TABLE IF NOT EXISTS episode (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ehr_id UUID NOT NULL REFERENCES ehr(id),
    episode_number VARCHAR(50) NOT NULL, -- human-readable episode ID
    type VARCHAR(50) NOT NULL, -- 'emergency', 'inpatient', 'outpatient', 'chronic'
    status VARCHAR(30) NOT NULL, -- 'planned', 'active', 'onhold', 'finished', 'cancelled'
    status_history JSONB, -- track status changes
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP,
    diagnosis_primary VARCHAR(20), -- ICD-10 code
    diagnosis_primary_text TEXT,
    diagnosis_secondary JSONB, -- array of {code, text}
    care_manager_id UUID, -- reference to Practitioner
    managing_organization_id UUID, -- reference to Organization
    reason_code VARCHAR(50),
    reason_text TEXT,
    referral_source VARCHAR(255),
    priority VARCHAR(20), -- 'routine', 'urgent', 'asap', 'stat'
    metadata JSONB, -- flexible additional data
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_episode_ehr ON episode(ehr_id);
CREATE INDEX idx_episode_number ON episode(episode_number);
CREATE INDEX idx_episode_type ON episode(type);
CREATE INDEX idx_episode_status ON episode(status);
CREATE INDEX idx_episode_period ON episode(period_start, period_end);
CREATE INDEX idx_episode_care_manager ON episode(care_manager_id);

COMMENT ON TABLE episode IS 'Episodios clínicos (urgencias, hospitalizaciones, consultas ambulatorias)';

CREATE TABLE IF NOT EXISTS episode_resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id UUID NOT NULL REFERENCES episode(id) ON DELETE CASCADE,
    resource_type VARCHAR(50) NOT NULL, -- 'Encounter', 'Observation', 'MedicationRequest', etc.
    resource_id VARCHAR(100) NOT NULL, -- FHIR resource ID
    resource_version_id VARCHAR(50),
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    linked_by VARCHAR(100),
    metadata JSONB
);

CREATE INDEX idx_episode_resource_episode ON episode_resource(episode_id);
CREATE INDEX idx_episode_resource_type ON episode_resource(resource_type);
CREATE INDEX idx_episode_resource_id ON episode_resource(resource_id);
CREATE INDEX idx_episode_resource_type_id ON episode_resource(resource_type, resource_id);

COMMENT ON TABLE episode_resource IS 'Vinculación de recursos FHIR a episodios clínicos';

-- ============================================================================
-- CLINICAL DOCUMENTS & VERSIONING
-- ============================================================================

CREATE TABLE IF NOT EXISTS clinical_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ehr_id UUID NOT NULL REFERENCES ehr(id),
    fhir_composition_id VARCHAR(100) NOT NULL UNIQUE, -- reference to FHIR Composition
    document_type VARCHAR(50) NOT NULL, -- 'discharge', 'operative', 'progress', 'consultation'
    status VARCHAR(20) NOT NULL, -- 'preliminary', 'final', 'amended', 'entered-in-error'
    current_version INTEGER NOT NULL DEFAULT 1,
    title VARCHAR(500),
    confidentiality VARCHAR(20), -- 'N', 'R', 'V' (normal, restricted, very restricted)
    author_id VARCHAR(100), -- Practitioner ID
    encounter_id VARCHAR(100), -- related Encounter
    date_created TIMESTAMP NOT NULL,
    date_attested TIMESTAMP,
    custodian VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clinical_doc_ehr ON clinical_document(ehr_id);
CREATE INDEX idx_clinical_doc_type ON clinical_document(document_type);
CREATE INDEX idx_clinical_doc_status ON clinical_document(status);
CREATE INDEX idx_clinical_doc_author ON clinical_document(author_id);

COMMENT ON TABLE clinical_document IS 'Documentos clínicos con versionado (epicrisis, informes, consentimientos)';

CREATE TABLE IF NOT EXISTS clinical_document_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES clinical_document(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL, -- full FHIR Composition JSON
    snapshot_hash VARCHAR(64), -- SHA-256 hash for integrity
    change_reason TEXT,
    changed_sections TEXT[], -- which sections changed
    versioned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    versioned_by VARCHAR(100) NOT NULL,
    UNIQUE(document_id, version_number)
);

CREATE INDEX idx_clinical_doc_ver_document ON clinical_document_version(document_id);
CREATE INDEX idx_clinical_doc_ver_number ON clinical_document_version(version_number);
CREATE INDEX idx_clinical_doc_ver_date ON clinical_document_version(versioned_at);

COMMENT ON TABLE clinical_document_version IS 'Versionado completo de documentos clínicos';

-- ============================================================================
-- AUDIT & COMPLIANCE
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    action VARCHAR(50) NOT NULL, -- 'create', 'read', 'update', 'delete', 'search', 'execute'
    outcome VARCHAR(10) NOT NULL, -- 'success', 'failure'
    entity_type VARCHAR(100) NOT NULL, -- 'Patient', 'Observation', 'MedicationRequest', etc.
    entity_id VARCHAR(100),
    entity_version VARCHAR(50),
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(255),
    user_role VARCHAR(100),
    source_ip VARCHAR(45), -- IPv6 compatible
    user_agent TEXT,
    request_uri TEXT,
    http_method VARCHAR(10),
    http_status_code INTEGER,
    purpose_of_use VARCHAR(100), -- 'TREATMENT', 'PAYMENT', 'OPERATIONS', 'RESEARCH'
    patient_id UUID, -- if action involves a patient
    session_id VARCHAR(100),
    correlation_id VARCHAR(100), -- for tracing across services
    details_json JSONB, -- flexible additional details
    before_snapshot JSONB, -- state before change
    after_snapshot JSONB -- state after change
);

CREATE INDEX idx_audit_tenant ON audit_event(tenant_id);
CREATE INDEX idx_audit_recorded ON audit_event(recorded_at DESC);
CREATE INDEX idx_audit_user ON audit_event(user_id);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_patient ON audit_event(patient_id);
CREATE INDEX idx_audit_action ON audit_event(action);
CREATE INDEX idx_audit_correlation ON audit_event(correlation_id);

COMMENT ON TABLE audit_event IS 'Auditoría exhaustiva de todas las operaciones (compliance Ley 19.628)';

-- ============================================================================
-- STORED QUERIES (Query Engine)
-- ============================================================================

CREATE TABLE IF NOT EXISTS stored_query (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(100), -- 'clinical', 'administrative', 'analytics', 'reporting'
    query_type VARCHAR(50) NOT NULL, -- 'fhir_search', 'sql', 'hybrid', 'cql'
    query_definition_json JSONB NOT NULL,
    parameters_schema JSONB, -- JSON schema for expected parameters
    result_schema JSONB, -- expected result structure
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) DEFAULT 'active', -- 'active', 'deprecated', 'testing'
    is_public BOOLEAN DEFAULT FALSE,
    tenant_id UUID REFERENCES tenant(id), -- NULL = global, otherwise tenant-specific
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    last_executed_at TIMESTAMP,
    execution_count INTEGER DEFAULT 0,
    avg_execution_time_ms INTEGER
);

CREATE INDEX idx_stored_query_name ON stored_query(name);
CREATE INDEX idx_stored_query_category ON stored_query(category);
CREATE INDEX idx_stored_query_status ON stored_query(status);
CREATE INDEX idx_stored_query_tenant ON stored_query(tenant_id);

COMMENT ON TABLE stored_query IS 'Motor de queries reutilizables para reportes y analytics';

CREATE TABLE IF NOT EXISTS stored_query_execution_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id UUID NOT NULL REFERENCES stored_query(id),
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_by VARCHAR(100),
    parameters_json JSONB,
    execution_time_ms INTEGER,
    result_count INTEGER,
    status VARCHAR(20), -- 'success', 'error', 'timeout'
    error_message TEXT
);

CREATE INDEX idx_query_log_query ON stored_query_execution_log(query_id);
CREATE INDEX idx_query_log_executed ON stored_query_execution_log(executed_at DESC);

COMMENT ON TABLE stored_query_execution_log IS 'Log de ejecuciones de queries para optimización';

-- ============================================================================
-- MASTER CATALOGS (Chilean Healthcare Context)
-- ============================================================================

CREATE TABLE IF NOT EXISTS master_catalog_medication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cenabast_code VARCHAR(50) UNIQUE,
    cenabast_name TEXT,
    active_ingredient TEXT,
    presentation VARCHAR(255),
    concentration VARCHAR(100),
    atc_code VARCHAR(20), -- Anatomical Therapeutic Chemical
    controlled_substance BOOLEAN DEFAULT FALSE,
    requires_prescription BOOLEAN DEFAULT TRUE,
    generic_name TEXT,
    brand_names TEXT[],
    status VARCHAR(20) DEFAULT 'active',
    last_sync_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_med_cenabast ON master_catalog_medication(cenabast_code);
CREATE INDEX idx_catalog_med_atc ON master_catalog_medication(atc_code);
CREATE INDEX idx_catalog_med_status ON master_catalog_medication(status);

COMMENT ON TABLE master_catalog_medication IS 'Catálogo maestro de medicamentos (integración CENABAST)';

CREATE TABLE IF NOT EXISTS master_catalog_diagnosis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE, -- ICD-10 code
    text TEXT NOT NULL,
    parent_code VARCHAR(20),
    level INTEGER, -- hierarchy level
    is_leaf BOOLEAN,
    valid_from DATE,
    valid_to DATE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_diag_code ON master_catalog_diagnosis(code);
CREATE INDEX idx_catalog_diag_parent ON master_catalog_diagnosis(parent_code);

COMMENT ON TABLE master_catalog_diagnosis IS 'Catálogo CIE-10 diagnósticos';

CREATE TABLE IF NOT EXISTS master_catalog_procedure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE, -- ICD-10-PCS or local coding
    text TEXT NOT NULL,
    category VARCHAR(100),
    valid_from DATE,
    valid_to DATE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_proc_code ON master_catalog_procedure(code);

COMMENT ON TABLE master_catalog_procedure IS 'Catálogo de procedimientos';

CREATE TABLE IF NOT EXISTS master_catalog_prestacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fonasa_code VARCHAR(50) UNIQUE,
    name TEXT NOT NULL,
    category VARCHAR(100),
    level VARCHAR(20), -- 'nivel1', 'nivel2', 'nivel3'
    valor_arancel DECIMAL(10, 2),
    valor_convenio DECIMAL(10, 2),
    valid_from DATE,
    valid_to DATE,
    requires_authorization BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_prest_fonasa ON master_catalog_prestacion(fonasa_code);
CREATE INDEX idx_catalog_prest_category ON master_catalog_prestacion(category);

COMMENT ON TABLE master_catalog_prestacion IS 'Catálogo de prestaciones FONASA';

-- ============================================================================
-- PRESCRIPTION MANAGEMENT (CENABAST Integration)
-- ============================================================================

CREATE TABLE IF NOT EXISTS prescription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    fhir_medication_request_id VARCHAR(100) NOT NULL UNIQUE,
    ehr_id UUID NOT NULL REFERENCES ehr(id),
    episode_id UUID REFERENCES episode(id),
    patient_id UUID NOT NULL REFERENCES person(id),
    prescriber_id VARCHAR(100) NOT NULL, -- Practitioner FHIR ID
    prescription_number VARCHAR(100) UNIQUE,
    status VARCHAR(30) NOT NULL, -- 'draft', 'active', 'on-hold', 'completed', 'cancelled', 'entered-in-error'
    intent VARCHAR(20) NOT NULL, -- 'proposal', 'plan', 'order', 'instance-order'
    priority VARCHAR(20), -- 'routine', 'urgent', 'asap', 'stat'
    medication_code VARCHAR(50),
    medication_display TEXT,
    dosage_instruction JSONB,
    quantity_value DECIMAL(10, 3),
    quantity_unit VARCHAR(50),
    refills_allowed INTEGER DEFAULT 0,
    validity_period_start TIMESTAMP,
    validity_period_end TIMESTAMP,
    authored_on TIMESTAMP NOT NULL,

    -- CENABAST Integration
    cenabast_sent_at TIMESTAMP,
    cenabast_response_json JSONB,
    cenabast_status VARCHAR(50), -- 'pending', 'sent', 'accepted', 'rejected', 'dispensed'
    cenabast_error_message TEXT,

    dispense_count INTEGER DEFAULT 0,
    last_dispensed_at TIMESTAMP,

    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_prescription_tenant ON prescription(tenant_id);
CREATE INDEX idx_prescription_ehr ON prescription(ehr_id);
CREATE INDEX idx_prescription_patient ON prescription(patient_id);
CREATE INDEX idx_prescription_status ON prescription(status);
CREATE INDEX idx_prescription_cenabast_status ON prescription(cenabast_status);
CREATE INDEX idx_prescription_authored ON prescription(authored_on DESC);

COMMENT ON TABLE prescription IS 'Recetas electrónicas con integración CENABAST';

CREATE TABLE IF NOT EXISTS prescription_dispense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescription(id),
    fhir_medication_dispense_id VARCHAR(100) NOT NULL UNIQUE,
    dispense_number VARCHAR(100),
    status VARCHAR(30) NOT NULL, -- 'preparation', 'in-progress', 'completed', 'declined', etc.
    quantity_dispensed DECIMAL(10, 3),
    quantity_unit VARCHAR(50),
    when_prepared TIMESTAMP,
    when_handed_over TIMESTAMP,
    dispensed_by VARCHAR(100), -- Practitioner ID
    dispensed_location VARCHAR(255),
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispense_prescription ON prescription_dispense(prescription_id);
CREATE INDEX idx_dispense_status ON prescription_dispense(status);
CREATE INDEX idx_dispense_handed_over ON prescription_dispense(when_handed_over);

COMMENT ON TABLE prescription_dispense IS 'Dispensaciones de recetas';

-- ============================================================================
-- PRACTITIONER & ORGANIZATION (Referencias básicas)
-- ============================================================================

CREATE TABLE IF NOT EXISTS practitioner_cache (
    fhir_id VARCHAR(100) PRIMARY KEY,
    run VARCHAR(20) UNIQUE,
    full_name VARCHAR(500),
    specialty VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    tenant_id UUID REFERENCES tenant(id),
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_practitioner_run ON practitioner_cache(run);
CREATE INDEX idx_practitioner_tenant ON practitioner_cache(tenant_id);

COMMENT ON TABLE practitioner_cache IS 'Caché de prestadores (datos completos en FHIR)';

CREATE TABLE IF NOT EXISTS organization_cache (
    fhir_id VARCHAR(100) PRIMARY KEY,
    code VARCHAR(50) UNIQUE,
    name VARCHAR(500),
    type VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    tenant_id UUID REFERENCES tenant(id),
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_organization_code ON organization_cache(code);
CREATE INDEX idx_organization_tenant ON organization_cache(tenant_id);

COMMENT ON TABLE organization_cache IS 'Caché de organizaciones (datos completos en FHIR)';

-- ============================================================================
-- CONFIGURATION & METADATA
-- ============================================================================

CREATE TABLE IF NOT EXISTS system_config (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    value_type VARCHAR(50) NOT NULL, -- 'string', 'integer', 'boolean', 'json'
    category VARCHAR(100),
    description TEXT,
    is_sensitive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

CREATE INDEX idx_sys_config_category ON system_config(category);

COMMENT ON TABLE system_config IS 'Configuración global del sistema';

-- ============================================================================
-- TRIGGERS FOR UPDATED_AT
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply to all relevant tables
CREATE TRIGGER update_tenant_updated_at BEFORE UPDATE ON tenant
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_person_updated_at BEFORE UPDATE ON person
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ehr_updated_at BEFORE UPDATE ON ehr
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_episode_updated_at BEFORE UPDATE ON episode
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_clinical_document_updated_at BEFORE UPDATE ON clinical_document
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_stored_query_updated_at BEFORE UPDATE ON stored_query
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_prescription_updated_at BEFORE UPDATE ON prescription
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_master_catalog_medication_updated_at BEFORE UPDATE ON master_catalog_medication
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_master_catalog_prestacion_updated_at BEFORE UPDATE ON master_catalog_prestacion
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_system_config_updated_at BEFORE UPDATE ON system_config
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- INITIAL DATA
-- ============================================================================

-- Insert default tenant for development
INSERT INTO tenant (code, name, type, status, created_by)
VALUES ('DEMO', 'Hospital Demo', 'hospital', 'active', 'system')
ON CONFLICT (code) DO NOTHING;

-- Insert some basic system configurations
INSERT INTO system_config (key, value, value_type, category, description)
VALUES
    ('neo.version', '1.0.0', 'string', 'system', 'Versión de NEO'),
    ('neo.env', 'development', 'string', 'system', 'Ambiente actual'),
    ('neo.max_upload_size_mb', '50', 'integer', 'limits', 'Tamaño máximo de archivos en MB'),
    ('neo.audit_retention_days', '2555', 'integer', 'compliance', 'Días de retención de auditoría (7 años)'),
    ('neo.enable_cenabast_integration', 'true', 'boolean', 'integrations', 'Habilitar integración CENABAST'),
    ('neo.enable_fonasa_integration', 'false', 'boolean', 'integrations', 'Habilitar integración FONASA')
ON CONFLICT (key) DO NOTHING;

-- ============================================================================
-- VIEWS (Optional - útiles para queries comunes)
-- ============================================================================

-- Vista consolidada de pacientes activos con su información principal
CREATE OR REPLACE VIEW v_active_patients AS
SELECT
    p.id,
    p.tenant_id,
    p.birth_date,
    p.gender,
    pn.text as full_name,
    pn.family,
    pn.given[1] as first_name,
    pi.value as run,
    pt.value as phone,
    pt_email.value as email,
    pa.city,
    pa.district,
    e.ehr_id,
    p.created_at,
    p.updated_at
FROM person p
LEFT JOIN LATERAL (
    SELECT * FROM person_name
    WHERE person_id = p.id AND use = 'official'
    ORDER BY created_at DESC LIMIT 1
) pn ON true
LEFT JOIN LATERAL (
    SELECT * FROM person_identifier
    WHERE person_id = p.id AND type_code = 'RUN'
    ORDER BY created_at DESC LIMIT 1
) pi ON true
LEFT JOIN LATERAL (
    SELECT * FROM person_telecom
    WHERE person_id = p.id AND system = 'phone'
    ORDER BY rank NULLS LAST, created_at DESC LIMIT 1
) pt ON true
LEFT JOIN LATERAL (
    SELECT * FROM person_telecom
    WHERE person_id = p.id AND system = 'email'
    ORDER BY rank NULLS LAST, created_at DESC LIMIT 1
) pt_email ON true
LEFT JOIN LATERAL (
    SELECT * FROM person_address
    WHERE person_id = p.id AND use = 'home'
    ORDER BY created_at DESC LIMIT 1
) pa ON true
LEFT JOIN ehr e ON e.person_id = p.id
WHERE p.active = true AND p.deleted_at IS NULL;

COMMENT ON VIEW v_active_patients IS 'Vista consolidada de pacientes activos con información principal';
