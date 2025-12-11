# Neo FHIR Server

**Plataforma de Interoperabilidad de Salud para Hospital El Carmen**

Neo es un servidor FHIR (Fast Healthcare Interoperability Resources) multi-tenant diseñado para gestionar información clínica de manera estandarizada, segura y escalable, cumpliendo con los estándares internacionales HL7 FHIR R4 y adaptado a la normativa chilena.

## 🎯 Propósito del Proyecto

Neo FHIR Server tiene como objetivo principal:

- **Centralizar la información clínica** de múltiples instituciones de salud en una plataforma única
- **Garantizar interoperabilidad** con sistemas externos mediante estándares FHIR
- **Implementar un Master Patient Index (MPI)** para identificación única de pacientes
- **Proporcionar trazabilidad completa** de todas las operaciones clínicas y administrativas
- **Cumplir con normativa chilena** (Ley 19.628, regulaciones MINSAL, perfiles CL-CORE)
- **Facilitar integraciones** con FONASA, ISAPREs, CENABAST y otros sistemas nacionales

## 📊 Estado del Proyecto

### ✅ Completado (30%)

#### Infraestructura Base
- ✅ Arquitectura Spring Boot 3.2.0 + Java 17
- ✅ Integración HAPI FHIR 7.0.2 R4
- ✅ Base de datos PostgreSQL 15.15 con soporte JSONB
- ✅ Sistema de migraciones Flyway 10.4.1
- ✅ Multi-tenancy completo con aislamiento por tenant
- ✅ Configuración Docker para containerización

#### Modelo de Datos (11 Entidades JPA)
- ✅ **Person**: Gestión de personas (pacientes, profesionales)
- ✅ **Tenant**: Multi-tenancy (hospitales, clínicas)
- ✅ **MasterCatalog**: Catálogos maestros (LOINC, SNOMED-CT, ICD-10)
- ✅ **PersonMerge**: Auditoría de fusiones MPI con reversión
- ✅ **Ehr**: Electronic Health Record por paciente
- ✅ **Episode**: Episodios de atención
- ✅ **ClinicalDocument**: Documentos clínicos versionados
- ✅ **Prescription**: Recetas médicas
- ✅ **StoredQuery**: Queries FHIR reutilizables
- ✅ **AuditEvent**: Trazabilidad completa de eventos

#### Capa de Datos (13 Repositorios JPA)
- ✅ Repositorios con métodos de búsqueda optimizados
- ✅ Queries personalizadas con JPQL
- ✅ Soporte para búsquedas en campos JSONB
- ✅ Paginación y ordenamiento

#### Servicios de Negocio (3/9 completados)
- ✅ **NeoAuditService**: Registro de auditoría (396 líneas)
- ✅ **NeoEhrService**: Gestión de EHR (313 líneas)
- ✅ **NeoPatientService**: Lógica de pacientes (333 líneas)

#### Base de Datos
- ✅ **V1__init_neo_core_schema.sql**: Esquema base completo (728 líneas)
  - Tablas: tenant, person, person_name, person_identifier, person_address, person_telecom, person_contact
  - Tablas: ehr, episode, episode_resource, clinical_document, clinical_document_version
  - Tablas: prescription, prescription_dispense, audit_event
  - 45+ índices optimizados (B-tree, GIN, parciales)
  - 12 triggers para validación y auditoría

- ✅ **V2__create_stored_query.sql**: Queries FHIR almacenadas (45 líneas)
  - Sistema de queries predefinidas reutilizables
  - Contador de uso y analítica

- ✅ **V3__create_master_catalog.sql**: Catálogos maestros (85 líneas)
  - Soporte para LOINC, SNOMED-CT, ICD-10
  - Datos iniciales: géneros FHIR, estados civiles, identificadores chilenos (RUN, FONASA, ISAPRE)
  - Búsqueda full-text en español
  - Jerarquías de códigos

- ✅ **V4__create_person_merge.sql**: Auditoría de fusiones MPI (104 líneas)
  - Snapshots JSONB completos antes del merge
  - Capacidad de reversión
  - Trigger de validación de merges duplicados
  - Match scoring (0.0-1.0)

#### FHIR Resource Providers (3 básicos)
- ✅ PatientResourceProvider
- ✅ OrganizationResourceProvider
- ✅ PractitionerResourceProvider

#### Recursos y Configuración
- ✅ application.yml con configuración completa (330 líneas)
- ✅ Perfiles FHIR CL-CORE 1.9.3 incluidos
- ✅ Configuración de seguridad base
- ✅ Logging y monitoreo

### 🚧 En Progreso / Pendiente (70%)

#### Servicios de Negocio Faltantes (6/9)
- ❌ **NeoClinicalDocumentService**: Gestión de documentos clínicos
- ❌ **NeoPrescriptionService**: Recetas y dispensación
- ❌ **NeoCenabastService**: Integración CENABAST
- ❌ **NeoMasterCatalogService**: Gestión de catálogos
- ❌ **NeoStoredQueryService**: Queries almacenadas
- ❌ **NeoEpisodeService**: Episodios de atención

#### FHIR Resource Providers Completos
- ❌ **Patient**: CRUD completo + operaciones ($match, $merge)
- ❌ **Observation**: Resultados de laboratorio, signos vitales
- ❌ **Condition**: Diagnósticos y problemas
- ❌ **MedicationRequest**: Prescripciones
- ❌ **MedicationDispense**: Dispensaciones
- ❌ **Encounter**: Encuentros clínicos
- ❌ **DiagnosticReport**: Informes diagnósticos
- ❌ **DocumentReference**: Referencias a documentos
- ❌ **AllergyIntolerance**: Alergias
- ❌ **Immunization**: Vacunas

#### Mappers FHIR ↔ JPA
- ❌ PatientMapper (FHIR Patient ↔ Person)
- ❌ ObservationMapper
- ❌ ConditionMapper
- ❌ MedicationRequestMapper
- ❌ EncounterMapper
- ❌ Validación de perfiles CL-CORE

#### Seguridad e Integración
- ❌ **Integración Keycloak**:
  - OAuth2/OIDC
  - JWT validation
  - Role-based access control (RBAC)
  - Tenant isolation por token
- ❌ **SMART on FHIR**: Autorización granular por recurso

#### APIs REST Adicionales
- ❌ **/api/mpi**: Operaciones Master Patient Index
  - Búsqueda de duplicados
  - Merge de pacientes
  - Reversión de merges
- ❌ **/api/catalogs**: Gestión de catálogos maestros
- ❌ **/api/queries**: Gestión de queries almacenadas
- ❌ **/api/admin**: Administración de tenants

#### Integraciones Externas
- ❌ **CENABAST**: Catálogo de medicamentos
- ❌ **FONASA**: Validación de beneficiarios
- ❌ **DEIS (MINSAL)**: Reportería estadística
- ❌ **Registro Civil**: Validación RUN

#### Frontend (React/Vue)
- ❌ **Neo Pacientes**: Módulo de gestión de pacientes
  - Búsqueda y listado
  - Ficha completa del paciente
  - Gestión de duplicados
  - Merge de pacientes
- ❌ **Neo Farmacia**: Módulo de farmacia
  - Prescripciones
  - Dispensaciones
  - Stock
- ❌ **Neo Admin**: Panel de administración
  - Gestión de tenants
  - Catálogos
  - Auditoría

#### Testing
- ❌ **Tests Unitarios**: Objetivo >80% cobertura
  - Servicios
  - Repositorios
  - Mappers
- ❌ **Tests de Integración**:
  - FHIR endpoints
  - Base de datos
  - Seguridad
- ❌ **Tests E2E**:
  - Flujos completos
  - Integraciones

#### Documentación
- ❌ **Guía de Implementación FHIR**
- ❌ **API Reference completa**
- ❌ **Guía de Deployment**
- ❌ **Manual de Operaciones**

## 🏗️ Arquitectura

```
Neo FHIR Server
│
├── Core Domain (Modelo de Datos)
│   ├── Person (MPI)
│   ├── Ehr (Electronic Health Record)
│   ├── Clinical Documents
│   ├── Episodes
│   └── Prescriptions
│
├── FHIR Layer (HAPI FHIR)
│   ├── Resource Providers
│   ├── Interceptors
│   └── Validation
│
├── Business Services
│   ├── Patient Service
│   ├── MPI Service
│   ├── Clinical Document Service
│   └── Audit Service
│
├── Data Layer
│   ├── JPA Repositories
│   └── PostgreSQL + JSONB
│
├── Security
│   ├── Keycloak (OAuth2/OIDC)
│   └── SMART on FHIR
│
└── External Integrations
    ├── CENABAST
    ├── FONASA
    └── DEIS
```

## 🛠️ Stack Tecnológico

- **Backend**: Java 17 (OpenJDK Temurin)
- **Framework**: Spring Boot 3.2.0
- **FHIR**: HAPI FHIR 7.0.2 R4
- **Database**: PostgreSQL 15.15
- **Migrations**: Flyway 10.4.1
- **Build**: Maven 3.9+
- **Security**: Keycloak (pendiente)
- **Containerization**: Docker
- **Frontend**: React/Vue (pendiente)

## 🚀 Instalación y Uso

### Prerrequisitos

- Java 17 (OpenJDK Temurin)
- Maven 3.9+
- PostgreSQL 15+
- Docker (opcional)

### Configuración Base de Datos

```sql
CREATE DATABASE neo_fhir;
CREATE USER fhiruser WITH PASSWORD 'fhirpass123';
GRANT ALL PRIVILEGES ON DATABASE neo_fhir TO fhiruser;
```

### Variables de Entorno

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=neo_fhir
export DB_USER=fhiruser
export DB_PASSWORD=fhirpass123
export JAVA_HOME=/path/to/java17
```

### Ejecutar Migraciones

```bash
mvn flyway:migrate
```

### Iniciar Servidor

```bash
mvn spring-boot:run
```

El servidor estará disponible en: `http://localhost:8080/fhir`

### Probar FHIR Endpoint

```bash
curl http://localhost:8080/fhir/metadata
```

## 📝 Roadmap

### Fase 1: MVP (8-12 semanas) - 30% completado
- [x] Infraestructura base
- [x] Modelo de datos core
- [x] Migraciones Flyway
- [x] Servicios básicos
- [ ] FHIR Resource Providers completos
- [ ] Mappers FHIR ↔ JPA
- [ ] Tests unitarios >80%

### Fase 2: Seguridad e Integraciones (4-6 semanas)
- [ ] Integración Keycloak
- [ ] SMART on FHIR
- [ ] APIs REST adicionales
- [ ] Integración CENABAST
- [ ] Tests de integración

### Fase 3: Frontend (6-8 semanas)
- [ ] Neo Pacientes
- [ ] Neo Farmacia
- [ ] Neo Admin
- [ ] Tests E2E

### Fase 4: Producción (2-4 semanas)
- [ ] Documentación completa
- [ ] Performance testing
- [ ] Security audit
- [ ] Deployment a producción

## 📄 Licencia

Proyecto privado - Hospital El Carmen (HEC)

## 👥 Equipo

Proyecto HEC - Sistema Neo FHIR Server

---

**Estado Actual**: MVP en desarrollo (30% completado)
**Última Actualización**: 2025-12-10
**Versión**: 0.1.0-SNAPSHOT
