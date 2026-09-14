-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Suppliers Master
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id UUID NOT NULL,
    name TEXT NOT NULL,
    contact_person TEXT,
    contact_number TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Materials Master
CREATE TABLE materials_master (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id UUID NOT NULL,
    item_code TEXT NOT NULL,
    description TEXT NOT NULL,
    default_uom TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Vehicle Entries
CREATE TABLE vehicle_entries (
    id UUID PRIMARY KEY, -- Maps to Room entryId
    site_id UUID,
    vehicle_number TEXT NOT NULL,
    vehicle_type TEXT,
    driver_name TEXT,
    driver_mobile TEXT,
    transporter TEXT,
    purpose TEXT,
    opening_km INT,
    closing_km INT,
    po_number TEXT,
    invoice_number TEXT,
    challan_number TEXT,
    eway_bill TEXT,
    dock TEXT,
    remarks TEXT,
    in_time BIGINT,
    out_time BIGINT,
    gate TEXT,
    recorded_by TEXT,
    status TEXT,
    supervisor_override BOOLEAN,
    supervisor_notes TEXT,
    is_demo BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Visitor Entries
CREATE TABLE visitor_entries (
    id UUID PRIMARY KEY, -- Maps to Room entryId
    site_id UUID,
    visitor_name TEXT NOT NULL,
    mobile_number TEXT,
    company TEXT,
    host TEXT,
    purpose TEXT,
    vehicle_number TEXT,
    in_time BIGINT,
    out_time BIGINT,
    gate TEXT,
    recorded_by TEXT,
    status TEXT,
    pass_id TEXT,
    is_demo BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Repair Records
CREATE TABLE repair_records (
    id UUID PRIMARY KEY,
    site_id UUID,
    repair_vendor TEXT,
    repair_reason TEXT,
    asset_or_serial TEXT,
    expected_return_date BIGINT,
    repair_challan_ref TEXT,
    authorized_by TEXT,
    remarks TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Material Movements
CREATE TABLE material_movements (
    id UUID PRIMARY KEY, -- Maps to Room movementId
    site_id UUID,
    movement_type TEXT NOT NULL,
    supplier_name TEXT,
    destination_party TEXT,
    vehicle_number TEXT,
    invoice_number TEXT,
    challan_number TEXT,
    po_number TEXT,
    eway_bill TEXT,
    purpose TEXT,
    out_reason TEXT,
    repair_id UUID REFERENCES repair_records(id) ON DELETE SET NULL,
    driver_name TEXT,
    driver_mobile TEXT,
    transporter TEXT,
    received_by TEXT,
    authorized_by TEXT,
    remarks TEXT,
    in_time BIGINT,
    out_time BIGINT,
    expected_return_date BIGINT,
    actual_return_date BIGINT,
    gate TEXT,
    recorded_by TEXT,
    status TEXT,
    is_demo BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. Material Movement Items
CREATE TABLE material_movement_items (
    id UUID PRIMARY KEY, -- Maps to Room itemId
    site_id UUID,
    movement_id UUID REFERENCES material_movements(id) ON DELETE CASCADE,
    material_description TEXT,
    item_code TEXT,
    quantity NUMERIC,
    uom TEXT,
    serial_number TEXT,
    asset_number TEXT,
    remarks TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. Audit Logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id UUID,
    action TEXT NOT NULL,
    table_name TEXT,
    record_id UUID,
    performed_by TEXT,
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for Fast Querying and Joins
CREATE INDEX idx_vehicle_entries_site_status ON vehicle_entries(site_id, status);
CREATE INDEX idx_vehicle_entries_in_time ON vehicle_entries(in_time DESC);
CREATE INDEX idx_visitor_entries_site_status ON visitor_entries(site_id, status);
CREATE INDEX idx_visitor_entries_in_time ON visitor_entries(in_time DESC);
CREATE INDEX idx_material_movements_site_status ON material_movements(site_id, status);
CREATE INDEX idx_material_movements_in_time ON material_movements(in_time DESC);
CREATE INDEX idx_material_movement_items_movement_id ON material_movement_items(movement_id);
