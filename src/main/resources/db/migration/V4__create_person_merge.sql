-- =====================================================
-- Migration V4: Create person_merge table
-- Purpose: Audit trail for MPI merge operations with reversal capability
-- =====================================================

-- Drop table if it exists (in case Hibernate created it with wrong structure)
DROP TABLE IF EXISTS person_merge CASCADE;

CREATE TABLE person_merge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_person_id UUID NOT NULL,
    target_person_id UUID NOT NULL,
    match_score NUMERIC(5,4),
    match_type VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    merged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    merged_by VARCHAR(100) NOT NULL,
    merged_by_role VARCHAR(50),
    reason TEXT,
    is_automatic BOOLEAN NOT NULL DEFAULT false,
    source_snapshot_json JSONB,
    target_snapshot_json JSONB,
    merge_details_json JSONB,
    reversed_at TIMESTAMP,
    reversed_by VARCHAR(100),
    reversal_reason TEXT,
    correlation_id VARCHAR(100),

    CONSTRAINT fk_person_merge_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_person_merge_source FOREIGN KEY (source_person_id)
        REFERENCES person(id) ON DELETE RESTRICT,
    CONSTRAINT fk_person_merge_target FOREIGN KEY (target_person_id)
        REFERENCES person(id) ON DELETE RESTRICT,
    CONSTRAINT chk_person_merge_match_type CHECK (match_type IN ('EXACT', 'PROBABLE', 'POSSIBLE', 'MANUAL')),
    CONSTRAINT chk_person_merge_status CHECK (status IN ('ACTIVE', 'REVERSED', 'SUPERSEDED')),
    CONSTRAINT chk_person_merge_match_score CHECK (match_score IS NULL OR (match_score >= 0 AND match_score <= 1)),
    CONSTRAINT chk_person_merge_different_persons CHECK (source_person_id != target_person_id)
);

-- Índices para búsquedas frecuentes
CREATE INDEX IF NOT EXISTS idx_person_merge_source ON person_merge(source_person_id);
CREATE INDEX IF NOT EXISTS idx_person_merge_target ON person_merge(target_person_id);
CREATE INDEX IF NOT EXISTS idx_person_merge_tenant ON person_merge(tenant_id);
CREATE INDEX IF NOT EXISTS idx_person_merge_status ON person_merge(status);
CREATE INDEX IF NOT EXISTS idx_person_merge_date ON person_merge(merged_at);
CREATE INDEX IF NOT EXISTS idx_person_merge_merged_by ON person_merge(merged_by);
CREATE INDEX IF NOT EXISTS idx_person_merge_correlation ON person_merge(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_person_merge_automatic ON person_merge(is_automatic) WHERE is_automatic = true;
CREATE INDEX IF NOT EXISTS idx_person_merge_reversed ON person_merge(reversed_at) WHERE reversed_at IS NOT NULL;

-- Índices compuestos para consultas comunes
CREATE INDEX IF NOT EXISTS idx_person_merge_tenant_status ON person_merge(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_person_merge_source_status ON person_merge(source_person_id, status);
CREATE INDEX IF NOT EXISTS idx_person_merge_target_status ON person_merge(target_person_id, status);

-- Índices GIN para búsquedas en campos JSON
CREATE INDEX IF NOT EXISTS idx_person_merge_source_snapshot ON person_merge USING gin(source_snapshot_json);
CREATE INDEX IF NOT EXISTS idx_person_merge_target_snapshot ON person_merge USING gin(target_snapshot_json);
CREATE INDEX IF NOT EXISTS idx_person_merge_details ON person_merge USING gin(merge_details_json);

-- Índice para búsquedas por rango de fechas
CREATE INDEX IF NOT EXISTS idx_person_merge_date_range ON person_merge(tenant_id, merged_at DESC);

-- Comentarios para documentación
COMMENT ON TABLE person_merge IS 'Historial de fusiones de pacientes en el MPI con capacidad de reversión';
COMMENT ON COLUMN person_merge.source_person_id IS 'Persona origen (fusionada/merged) - queda inactiva';
COMMENT ON COLUMN person_merge.target_person_id IS 'Persona destino (permanece activa) - absorbe datos del source';
COMMENT ON COLUMN person_merge.match_score IS 'Score de matching (0.0 a 1.0) que justificó el merge';
COMMENT ON COLUMN person_merge.match_type IS 'EXACT (>=95%), PROBABLE (>=80%), POSSIBLE (>=60%), MANUAL';
COMMENT ON COLUMN person_merge.status IS 'ACTIVE: merge vigente, REVERSED: revertido, SUPERSEDED: supersedido por otro merge';
COMMENT ON COLUMN person_merge.is_automatic IS 'true si fue merge automático, false si fue manual';
COMMENT ON COLUMN person_merge.source_snapshot_json IS 'Snapshot completo del source person antes del merge (para reversión)';
COMMENT ON COLUMN person_merge.target_snapshot_json IS 'Snapshot completo del target person antes del merge (para reversión)';
COMMENT ON COLUMN person_merge.merge_details_json IS 'Detalles del merge: campos consolidados, conflictos resueltos, etc.';
COMMENT ON COLUMN person_merge.correlation_id IS 'ID de correlación para tracing distribuido';

-- Función para validar que no exista un merge activo entre las mismas dos personas
CREATE OR REPLACE FUNCTION check_active_merge_exists()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        IF EXISTS (
            SELECT 1 FROM person_merge
            WHERE status = 'ACTIVE'
            AND (
                (source_person_id = NEW.source_person_id AND target_person_id = NEW.target_person_id)
                OR (source_person_id = NEW.target_person_id AND target_person_id = NEW.source_person_id)
            )
            AND id != COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid)
        ) THEN
            RAISE EXCEPTION 'Ya existe un merge activo entre estas dos personas';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger para validar merges activos duplicados
CREATE TRIGGER trg_check_active_merge
    BEFORE INSERT OR UPDATE ON person_merge
    FOR EACH ROW
    EXECUTE FUNCTION check_active_merge_exists();
