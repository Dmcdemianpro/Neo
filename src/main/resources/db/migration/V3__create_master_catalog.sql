-- =====================================================
-- Migration V3: Create master_catalog table
-- Purpose: Master code catalogs (LOINC, SNOMED-CT, ICD-10, etc.)
-- =====================================================

-- Drop table if it exists (in case Hibernate created it with wrong structure)
DROP TABLE IF EXISTS master_catalog CASCADE;

CREATE TABLE master_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,  -- NULL para catálogos globales
    catalog_type VARCHAR(50) NOT NULL,
    system VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    display VARCHAR(500) NOT NULL,
    definition TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    parent_code VARCHAR(100),
    properties_json JSONB,
    mappings_json JSONB,
    version VARCHAR(50),
    effective_from TIMESTAMP,
    effective_to TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_master_catalog_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT uk_catalog_system_code_tenant UNIQUE (system, code, tenant_id)
);

-- Índices para búsquedas frecuentes
CREATE INDEX IF NOT EXISTS idx_master_catalog_system ON master_catalog(system);
CREATE INDEX IF NOT EXISTS idx_master_catalog_code ON master_catalog(code);
CREATE INDEX IF NOT EXISTS idx_master_catalog_type ON master_catalog(catalog_type);
CREATE INDEX IF NOT EXISTS idx_master_catalog_tenant ON master_catalog(tenant_id);
CREATE INDEX IF NOT EXISTS idx_master_catalog_active ON master_catalog(active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_master_catalog_parent ON master_catalog(parent_code) WHERE parent_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_master_catalog_display ON master_catalog USING gin(to_tsvector('spanish', display));

-- Índices GIN para búsquedas en campos JSON
CREATE INDEX IF NOT EXISTS idx_master_catalog_properties_json ON master_catalog USING gin(properties_json);
CREATE INDEX IF NOT EXISTS idx_master_catalog_mappings_json ON master_catalog USING gin(mappings_json);

-- Índice para búsquedas de códigos vigentes
CREATE INDEX IF NOT EXISTS idx_master_catalog_effective ON master_catalog(effective_from, effective_to)
    WHERE active = true;

-- Comentarios para documentación
COMMENT ON TABLE master_catalog IS 'Catálogos maestros de códigos médicos y administrativos';
COMMENT ON COLUMN master_catalog.tenant_id IS 'NULL para catálogos globales, UUID para catálogos específicos de tenant';
COMMENT ON COLUMN master_catalog.catalog_type IS 'Tipo: MEDICATION, DIAGNOSIS, PROCEDURE, LABORATORY, etc.';
COMMENT ON COLUMN master_catalog.system IS 'URI del sistema de codificación (ej: http://loinc.org, http://snomed.info/sct)';
COMMENT ON COLUMN master_catalog.code IS 'Código dentro del sistema';
COMMENT ON COLUMN master_catalog.display IS 'Nombre para mostrar';
COMMENT ON COLUMN master_catalog.parent_code IS 'Código padre para jerarquías';
COMMENT ON COLUMN master_catalog.properties_json IS 'Propiedades adicionales (ej: unidad de medida, rango normal)';
COMMENT ON COLUMN master_catalog.mappings_json IS 'Mappings a otros sistemas de codificación';
COMMENT ON COLUMN master_catalog.effective_from IS 'Fecha de vigencia desde';
COMMENT ON COLUMN master_catalog.effective_to IS 'Fecha de vigencia hasta';

-- Datos iniciales: Catálogo de géneros (FHIR Administrative Gender)
INSERT INTO master_catalog (catalog_type, system, code, display, active, tenant_id) VALUES
('GENDER', 'http://hl7.org/fhir/administrative-gender', 'male', 'Masculino', true, NULL),
('GENDER', 'http://hl7.org/fhir/administrative-gender', 'female', 'Femenino', true, NULL),
('GENDER', 'http://hl7.org/fhir/administrative-gender', 'other', 'Otro', true, NULL),
('GENDER', 'http://hl7.org/fhir/administrative-gender', 'unknown', 'Desconocido', true, NULL);

-- Datos iniciales: Catálogo de estados civiles chilenos
INSERT INTO master_catalog (catalog_type, system, code, display, active, tenant_id) VALUES
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'A', 'Anulado', true, NULL),
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'D', 'Divorciado', true, NULL),
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'M', 'Casado', true, NULL),
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'S', 'Soltero', true, NULL),
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'W', 'Viudo', true, NULL),
('MARITAL_STATUS', 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus', 'UNK', 'Desconocido', true, NULL);

-- Datos iniciales: Tipos de identificadores chilenos
INSERT INTO master_catalog (catalog_type, system, code, display, definition, active, tenant_id) VALUES
('ORGANIZATION', 'http://regcivil.cl/run', 'RUN', 'RUN - Rol Único Nacional', 'Número de identificación nacional de Chile', true, NULL),
('ORGANIZATION', 'http://regcivil.cl/passport', 'PPN', 'Pasaporte', 'Número de pasaporte', true, NULL),
('ORGANIZATION', 'http://minsal.cl/fonasa', 'FONASA', 'FONASA', 'Número de beneficiario FONASA', true, NULL),
('ORGANIZATION', 'http://supersalud.cl/isapre', 'ISAPRE', 'ISAPRE', 'Número de beneficiario ISAPRE', true, NULL);
