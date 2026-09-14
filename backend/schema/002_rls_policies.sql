-- ==============================================================================
-- GATEAI ROW LEVEL SECURITY (RLS) & MULTI-TENANT AUTHORIZATION POLICIES
-- ==============================================================================

-- Helper Functions to extract Claims from JWT
CREATE OR REPLACE FUNCTION public.current_company_id() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'company_id', '')::UUID;
$$ LANGUAGE SQL STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.current_site_id() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'site_id', '')::UUID;
$$ LANGUAGE SQL STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.current_user_role() RETURNS VARCHAR AS $$
  SELECT NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'role', '')::VARCHAR;
$$ LANGUAGE SQL STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.current_user_id() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::UUID;
$$ LANGUAGE SQL STABLE SET search_path = public;

-- ------------------------------------------------------------------------------
-- ENABLE RLS ON ALL DOMAIN TABLES
-- ------------------------------------------------------------------------------

ALTER TABLE companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE visitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE material_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE material_movement_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE gate_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_mutations ENABLE ROW LEVEL SECURITY;

-- ------------------------------------------------------------------------------
-- POLICIES: SITE & COMPANY ISOLATION
-- ------------------------------------------------------------------------------

-- Companies: Users can only read their assigned company
CREATE POLICY policy_companies_select ON companies
    AS PERMISSIVE FOR SELECT TO authenticated
    USING (id = public.current_company_id());

-- Sites: Users can only see sites in their company
CREATE POLICY policy_sites_select ON sites
    AS PERMISSIVE FOR SELECT TO authenticated
    USING (company_id = public.current_company_id());

-- ------------------------------------------------------------------------------
-- POLICIES: USERS
-- ------------------------------------------------------------------------------

-- Everyone can read users in their company/site
CREATE POLICY policy_users_select ON users
    AS PERMISSIVE FOR SELECT TO authenticated
    USING (company_id = public.current_company_id() AND site_id = public.current_site_id());

-- Only ADMIN can insert/update/delete users
CREATE POLICY policy_users_all_admin ON users
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() 
        AND site_id = public.current_site_id() 
        AND public.current_user_role() = 'ADMIN'
    )
    WITH CHECK (
        company_id = public.current_company_id() 
        AND site_id = public.current_site_id() 
        AND public.current_user_role() = 'ADMIN'
    );

-- ------------------------------------------------------------------------------
-- POLICIES: SUPPLIERS
-- ------------------------------------------------------------------------------

CREATE POLICY policy_suppliers_select ON suppliers
    AS PERMISSIVE FOR SELECT TO authenticated
    USING (company_id = public.current_company_id() AND site_id = public.current_site_id());

-- SUPERVISOR and ADMIN can modify suppliers
CREATE POLICY policy_suppliers_modify ON suppliers
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() 
        AND site_id = public.current_site_id() 
        AND public.current_user_role() IN ('SUPERVISOR', 'ADMIN')
    )
    WITH CHECK (
        company_id = public.current_company_id() 
        AND site_id = public.current_site_id() 
        AND public.current_user_role() IN ('SUPERVISOR', 'ADMIN')
    );

-- ------------------------------------------------------------------------------
-- POLICIES: OPERATIONAL ENTITIES
-- ------------------------------------------------------------------------------

-- Generic Site Scoped Table Policies for Domain Entities:
-- (Vehicles, Visitors, Material Movements, Repair Records, Documents, Alerts, Mutations)

CREATE POLICY policy_vehicles_site_isolation ON vehicles
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_visitors_site_isolation ON visitors
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_material_movements_site_isolation ON material_movements
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_material_items_site_isolation ON material_movement_items
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        site_id = public.current_site_id()
    ) WITH CHECK (
        site_id = public.current_site_id()
    );

CREATE POLICY policy_repair_records_site_isolation ON repair_records
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_documents_site_isolation ON documents
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_alerts_site_isolation ON gate_alerts
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_mutations_site_isolation ON sync_mutations
    AS PERMISSIVE FOR ALL TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    ) WITH CHECK (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

-- ------------------------------------------------------------------------------
-- AUDIT LOGS: STRICTLY APPEND-ONLY (NO UPDATE / NO DELETE)
-- ------------------------------------------------------------------------------

CREATE POLICY policy_audit_logs_select ON audit_logs
    AS PERMISSIVE FOR SELECT TO authenticated
    USING (
        company_id = public.current_company_id() AND site_id = public.current_site_id()
    );

CREATE POLICY policy_audit_logs_insert ON audit_logs
    AS PERMISSIVE FOR INSERT TO authenticated
    WITH CHECK (
        company_id = public.current_company_id() 
        AND site_id = public.current_site_id()
        AND user_id = public.current_user_id()
    );

-- Prevent any UPDATE or DELETE on audit_logs by throwing an exception via trigger
CREATE OR REPLACE FUNCTION public.fn_prevent_audit_modification() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries are immutable and cannot be modified or deleted.';
END;
$$ LANGUAGE plpgsql SET search_path = public;

DROP TRIGGER IF EXISTS trg_prevent_audit_update_delete ON audit_logs;
CREATE TRIGGER trg_prevent_audit_update_delete
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION public.fn_prevent_audit_modification();
