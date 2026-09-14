-- Enable Row Level Security
ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE materials_master ENABLE ROW LEVEL SECURITY;
ALTER TABLE vehicle_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE visitor_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE material_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE material_movement_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- Helper function to extract user role from JWT claims
CREATE OR REPLACE FUNCTION auth.get_user_role() RETURNS text AS $$
  SELECT (auth.jwt() -> 'app_metadata' ->> 'role')::text;
$$ LANGUAGE SQL STABLE;

-- Helper function to extract user site ID from JWT claims
CREATE OR REPLACE FUNCTION auth.get_user_site_id() RETURNS uuid AS $$
  SELECT NULLIF(auth.jwt() -> 'app_metadata' ->> 'site_id', '')::uuid;
$$ LANGUAGE SQL STABLE;

DO $$
DECLARE
    tbl_name text;
    tables_list text[] := ARRAY[
        'suppliers', 
        'materials_master', 
        'vehicle_entries', 
        'visitor_entries', 
        'repair_records', 
        'material_movements', 
        'material_movement_items', 
        'audit_logs'
    ];
BEGIN
    FOREACH tbl_name IN ARRAY tables_list LOOP
        -- Gate Guards Policies
        -- Guards can read data for their assigned site
        EXECUTE format('
            CREATE POLICY "%I_guard_select" ON %I 
            FOR SELECT 
            USING (auth.get_user_role() = ''guard'' AND site_id = auth.get_user_site_id());
        ', tbl_name, tbl_name);
        
        -- Guards can insert data for their assigned site
        EXECUTE format('
            CREATE POLICY "%I_guard_insert" ON %I 
            FOR INSERT 
            WITH CHECK (auth.get_user_role() = ''guard'' AND site_id = auth.get_user_site_id());
        ', tbl_name, tbl_name);
        
        -- Guards can update data for their assigned site
        EXECUTE format('
            CREATE POLICY "%I_guard_update" ON %I 
            FOR UPDATE 
            USING (auth.get_user_role() = ''guard'' AND site_id = auth.get_user_site_id());
        ', tbl_name, tbl_name);

        -- Supervisor and Admin Policies
        -- Supervisors/Admins have full access across the board
        EXECUTE format('
            CREATE POLICY "%I_admin_all" ON %I 
            FOR ALL 
            USING (auth.get_user_role() IN (''admin'', ''supervisor''));
        ', tbl_name, tbl_name);
    END LOOP;
END
$$;
