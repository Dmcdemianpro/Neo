-- =====================================================
-- Migration V2: Create stored_query table
-- Purpose: Store predefined FHIR queries for reuse
-- =====================================================

-- Drop table if it exists (in case Hibernate created it with wrong structure)
DROP TABLE IF EXISTS stored_query CASCADE;

CREATE TABLE stored_query (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    resource_type VARCHAR(50) NOT NULL,
    query_string TEXT NOT NULL,
    parameters_json JSONB,
    is_public BOOLEAN NOT NULL DEFAULT false,
    usage_count BIGINT DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_stored_query_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT uk_stored_query_tenant_name UNIQUE (tenant_id, name)
);

-- Indices para búsquedas frecuentes
CREATE INDEX IF NOT EXISTS idx_stored_query_tenant ON stored_query(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stored_query_resource_type ON stored_query(resource_type);
CREATE INDEX IF NOT EXISTS idx_stored_query_public ON stored_query(is_public) WHERE is_public = true;
CREATE INDEX IF NOT EXISTS idx_stored_query_usage ON stored_query(usage_count DESC);
CREATE INDEX IF NOT EXISTS idx_stored_query_created_by ON stored_query(created_by);

-- Índice GIN para búsquedas en el JSON de parámetros
CREATE INDEX IF NOT EXISTS idx_stored_query_parameters_json ON stored_query USING gin(parameters_json);

-- Comentarios para documentación
COMMENT ON TABLE stored_query IS 'Queries FHIR predefinidas para reutilización';
COMMENT ON COLUMN stored_query.query_string IS 'Query string FHIR (ej: "Patient?family=Smith&birthdate=gt1990-01-01")';
COMMENT ON COLUMN stored_query.parameters_json IS 'Definición de parámetros en formato JSON';
COMMENT ON COLUMN stored_query.is_public IS 'Si es true, la query puede ser usada por todos los usuarios del tenant';
COMMENT ON COLUMN stored_query.usage_count IS 'Contador de usos para analítica';
