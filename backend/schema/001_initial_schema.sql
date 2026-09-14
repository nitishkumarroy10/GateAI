-- ==============================================================================
-- GATEAI ENTERPRISE CLOUD BACKEND SCHEMA (PostgreSQL / Supabase)
-- Version: 1.0.0
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------
-- 1. TENANCY & ACCESS MANAGEMENT
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_site_code_per_company UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('GUARD', 'SUPERVISOR', 'ADMIN')),
    badge_number VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 2. SUPPLIERS & VENDORS DIRECTORY
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    supplier_code VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    gstin VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_supplier_name_per_site UNIQUE (site_id, name)
);

-- ------------------------------------------------------------------------------
-- 3. VEHICLE GATE MOVEMENTS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    vehicle_number VARCHAR(50) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    driver_name VARCHAR(255) NOT NULL,
    driver_phone VARCHAR(50) NOT NULL,
    driver_license VARCHAR(100),
    purpose VARCHAR(255) NOT NULL,
    transporter VARCHAR(255),
    opening_km INT NOT NULL,
    closing_km INT,
    status VARCHAR(50) NOT NULL CHECK (status IN ('Inside', 'Outside')),
    gate_in_id VARCHAR(50),
    gate_out_id VARCHAR(50),
    in_time TIMESTAMPTZ NOT NULL,
    out_time TIMESTAMPTZ,
    in_guard_id UUID REFERENCES users(id),
    out_guard_id UUID REFERENCES users(id),
    remarks TEXT,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 4. VISITOR PASSES & MOVEMENTS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS visitors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    pass_number VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    company VARCHAR(255),
    id_type VARCHAR(50) NOT NULL,
    id_number VARCHAR(100) NOT NULL,
    to_meet VARCHAR(255) NOT NULL,
    department VARCHAR(100) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    badge_number VARCHAR(100),
    visitor_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('Inside', 'Outside')),
    in_time TIMESTAMPTZ NOT NULL,
    out_time TIMESTAMPTZ,
    in_guard_id UUID REFERENCES users(id),
    out_guard_id UUID REFERENCES users(id),
    remarks TEXT,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_visitor_pass_per_site UNIQUE (site_id, pass_number)
);

-- ------------------------------------------------------------------------------
-- 5. MATERIAL MOVEMENTS (INWARD / OUTWARD HEADERS)
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS material_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    movement_id VARCHAR(100) NOT NULL, -- Business ID e.g. MI-2026-0001
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('IN', 'OUT')),
    supplier_name VARCHAR(255),
    destination_party VARCHAR(255),
    po_number VARCHAR(100),
    invoice_number VARCHAR(100),
    challan_number VARCHAR(100),
    vehicle_number VARCHAR(50),
    driver_name VARCHAR(255),
    driver_phone VARCHAR(50),
    transporter_name VARCHAR(255),
    eway_bill_number VARCHAR(100),
    expected_return_date TIMESTAMPTZ,
    out_reason VARCHAR(100), -- "Repair", "Return to Supplier", "Job Work", "Other"
    authorization VARCHAR(255),
    is_returnable BOOLEAN NOT NULL DEFAULT FALSE,
    is_returned BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED', -- "COMPLETED", "PARTIALLY_RETURNED", "RETURNED"
    linked_movement_id VARCHAR(100),
    repair_id VARCHAR(100),
    in_time TIMESTAMPTZ,
    out_time TIMESTAMPTZ,
    created_by_user_id UUID REFERENCES users(id),
    remarks TEXT,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_movement_id_per_site UNIQUE (site_id, movement_id)
);

-- ------------------------------------------------------------------------------
-- 6. MATERIAL MOVEMENT LINE ITEMS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS material_movement_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movement_id UUID NOT NULL REFERENCES material_movements(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    item_code VARCHAR(100),
    material_description TEXT NOT NULL,
    quantity NUMERIC(14, 4) NOT NULL CHECK (quantity > 0),
    uom VARCHAR(50) NOT NULL,
    po_quantity NUMERIC(14, 4),
    received_quantity NUMERIC(14, 4),
    accepted_quantity NUMERIC(14, 4),
    rejected_quantity NUMERIC(14, 4) DEFAULT 0,
    unit_price NUMERIC(14, 4),
    serial_number TEXT,
    hsn_code VARCHAR(50),
    qc_status VARCHAR(50) DEFAULT 'NOT_REQUIRED',
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 7. REPAIR RECORDS & MULTI-STAGE RETURNS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS repair_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    repair_id VARCHAR(100) NOT NULL, -- Business ID e.g. REP-2026-0001
    movement_out_id VARCHAR(100) NOT NULL,
    material_description TEXT NOT NULL,
    asset_or_serial VARCHAR(255),
    sent_quantity NUMERIC(14, 4) NOT NULL CHECK (sent_quantity > 0),
    returned_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0),
    uom VARCHAR(50) NOT NULL,
    repair_vendor VARCHAR(255) NOT NULL,
    repair_reason TEXT,
    expected_return_date TIMESTAMPTZ NOT NULL,
    sent_date TIMESTAMPTZ NOT NULL,
    actual_return_date TIMESTAMPTZ,
    in_time TIMESTAMPTZ,
    out_time TIMESTAMPTZ NOT NULL,
    repair_challan_ref VARCHAR(100),
    authorized_by VARCHAR(255),
    condition_on_return VARCHAR(255),
    return_document_ref VARCHAR(100),
    received_by VARCHAR(255),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'PARTIALLY_RETURNED', 'RETURNED_FROM_REPAIR', 'CANCELLED')),
    remarks TEXT,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_repair_id_per_site UNIQUE (site_id, repair_id),
    CONSTRAINT chk_return_not_exceed_sent CHECK (returned_quantity <= sent_quantity)
);

-- ------------------------------------------------------------------------------
-- 8. DOCUMENT REFERENCES & METADATA
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL, -- INVOICE, CHALLAN, PO, EWAY_BILL, QC_REPORT
    document_number VARCHAR(100) NOT NULL,
    document_date TIMESTAMPTZ NOT NULL,
    linked_entity_type VARCHAR(50) NOT NULL, -- MATERIAL_MOVEMENT, REPAIR_RECORD, VEHICLE
    linked_entity_id VARCHAR(100) NOT NULL,
    vendor_or_party VARCHAR(255),
    file_path TEXT, -- Storage bucket path (e.g. s3://... or supabase storage)
    file_hash VARCHAR(128),
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    verified_by_user_id UUID REFERENCES users(id),
    remarks TEXT,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 9. GATE ALERTS & SECURITY NOTIFICATIONS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gate_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    type VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(50) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    related_id VARCHAR(100),
    is_dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    dismissed_by_user_id UUID REFERENCES users(id),
    dismissed_at TIMESTAMPTZ,
    client_mutation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 10. APPEND-ONLY SERVER AUDIT LOGS
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    user_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    record_id VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    details TEXT,
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 11. IDEMPOTENCY & MUTATION JOURNAL
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sync_mutations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    client_mutation_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('SUCCESS', 'DUPLICATE_IGNORED', 'CONFLICT', 'ERROR')),
    response_payload JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_client_mutation_per_site UNIQUE (site_id, client_mutation_id)
);

-- ------------------------------------------------------------------------------
-- INDEXES FOR HIGH-PERFORMANCE DELTA QUERIES & SYNCHRONIZATION
-- ------------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_vehicles_sync ON vehicles (site_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_visitors_sync ON visitors (site_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_mat_movements_sync ON material_movements (site_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_mat_items_movement ON material_movement_items (movement_id);
CREATE INDEX IF NOT EXISTS idx_repairs_sync ON repair_records (site_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_alerts_sync ON gate_alerts (site_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_site_created ON audit_logs (site_id, created_at);
CREATE INDEX IF NOT EXISTS idx_sync_mutations_lookup ON sync_mutations (site_id, client_mutation_id);
