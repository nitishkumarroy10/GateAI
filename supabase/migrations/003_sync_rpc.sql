CREATE OR REPLACE FUNCTION public.bulk_sync_gate_data(payload JSONB)
RETURNS JSONB AS $$
DECLARE
    inserted_vehicles INT := 0;
    inserted_visitors INT := 0;
    inserted_movements INT := 0;
    inserted_items INT := 0;
    v_site_id UUID := auth.get_user_site_id();
BEGIN
    -- Ensure the user has a valid site_id (or they are an admin doing a generic push, though usually apps push per site)
    
    -- 1. Sync Vehicle Entries
    IF payload ? 'vehicles' THEN
        INSERT INTO vehicle_entries (
            id, site_id, vehicle_number, vehicle_type, driver_name, driver_mobile,
            transporter, purpose, opening_km, closing_km, po_number, invoice_number,
            challan_number, eway_bill, dock, remarks, in_time, out_time, gate,
            recorded_by, status, supervisor_override, supervisor_notes, is_demo, updated_at
        )
        SELECT 
            (x->>'entryId')::uuid,
            COALESCE(NULLIF(x->>'siteId', '')::uuid, v_site_id),
            x->>'vehicleNumber',
            x->>'vehicleType',
            x->>'driverName',
            x->>'driverMobile',
            x->>'transporter',
            x->>'purpose',
            (x->>'openingKm')::int,
            (x->>'closingKm')::int,
            x->>'poNumber',
            x->>'invoiceNumber',
            x->>'challanNumber',
            x->>'ewayBill',
            x->>'dock',
            x->>'remarks',
            (x->>'inTime')::bigint,
            (x->>'outTime')::bigint,
            x->>'gate',
            x->>'recordedBy',
            x->>'status',
            (x->>'supervisorOverride')::boolean,
            x->>'supervisorNotes',
            (x->>'isDemo')::boolean,
            NOW()
        FROM jsonb_array_elements(payload->'vehicles') AS x
        ON CONFLICT (id) DO UPDATE SET
            closing_km = EXCLUDED.closing_km,
            out_time = EXCLUDED.out_time,
            status = EXCLUDED.status,
            supervisor_override = EXCLUDED.supervisor_override,
            supervisor_notes = EXCLUDED.supervisor_notes,
            updated_at = NOW();
            
        GET DIAGNOSTICS inserted_vehicles = ROW_COUNT;
    END IF;

    -- 2. Sync Visitor Entries
    IF payload ? 'visitors' THEN
        INSERT INTO visitor_entries (
            id, site_id, visitor_name, mobile_number, company, host, purpose,
            vehicle_number, in_time, out_time, gate, recorded_by, status, pass_id, is_demo, updated_at
        )
        SELECT 
            (x->>'entryId')::uuid,
            COALESCE(NULLIF(x->>'siteId', '')::uuid, v_site_id),
            x->>'visitorName',
            x->>'mobileNumber',
            x->>'company',
            x->>'host',
            x->>'purpose',
            x->>'vehicleNumber',
            (x->>'inTime')::bigint,
            (x->>'outTime')::bigint,
            x->>'gate',
            x->>'recordedBy',
            x->>'status',
            x->>'passId',
            (x->>'isDemo')::boolean,
            NOW()
        FROM jsonb_array_elements(payload->'visitors') AS x
        ON CONFLICT (id) DO UPDATE SET
            out_time = EXCLUDED.out_time,
            status = EXCLUDED.status,
            updated_at = NOW();
            
        GET DIAGNOSTICS inserted_visitors = ROW_COUNT;
    END IF;

    -- 3. Sync Material Movements
    IF payload ? 'movements' THEN
        INSERT INTO material_movements (
            id, site_id, movement_type, supplier_name, destination_party, vehicle_number,
            invoice_number, challan_number, po_number, eway_bill, purpose, out_reason,
            repair_id, driver_name, driver_mobile, transporter, received_by, authorized_by,
            remarks, in_time, out_time, expected_return_date, actual_return_date, gate,
            recorded_by, status, is_demo, updated_at
        )
        SELECT 
            (x->>'movementId')::uuid,
            COALESCE(NULLIF(x->>'siteId', '')::uuid, v_site_id),
            x->>'movementType',
            x->>'supplierName',
            x->>'destinationParty',
            x->>'vehicleNumber',
            x->>'invoiceNumber',
            x->>'challanNumber',
            x->>'poNumber',
            x->>'ewayBill',
            x->>'purpose',
            x->>'outReason',
            NULLIF(x->>'repairId', '')::uuid,
            x->>'driverName',
            x->>'driverMobile',
            x->>'transporter',
            x->>'receivedBy',
            x->>'authorizedBy',
            x->>'remarks',
            (x->>'inTime')::bigint,
            (x->>'outTime')::bigint,
            (x->>'expectedReturnDate')::bigint,
            (x->>'actualReturnDate')::bigint,
            x->>'gate',
            x->>'recordedBy',
            x->>'status',
            (x->>'isDemo')::boolean,
            NOW()
        FROM jsonb_array_elements(payload->'movements') AS x
        ON CONFLICT (id) DO UPDATE SET
            out_time = EXCLUDED.out_time,
            status = EXCLUDED.status,
            actual_return_date = EXCLUDED.actual_return_date,
            updated_at = NOW();
            
        GET DIAGNOSTICS inserted_movements = ROW_COUNT;
    END IF;

    -- 4. Sync Material Movement Items
    IF payload ? 'movement_items' THEN
        INSERT INTO material_movement_items (
            id, site_id, movement_id, material_description, item_code, quantity,
            uom, serial_number, asset_number, remarks, updated_at
        )
        SELECT 
            (x->>'itemId')::uuid,
            COALESCE(NULLIF(x->>'siteId', '')::uuid, v_site_id),
            (x->>'movementId')::uuid,
            x->>'materialDescription',
            x->>'itemCode',
            (x->>'quantity')::numeric,
            x->>'uom',
            x->>'serialNumber',
            x->>'assetNumber',
            x->>'remarks',
            NOW()
        FROM jsonb_array_elements(payload->'movement_items') AS x
        ON CONFLICT (id) DO UPDATE SET
            quantity = EXCLUDED.quantity,
            updated_at = NOW();
            
        GET DIAGNOSTICS inserted_items = ROW_COUNT;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'vehicles_synced', inserted_vehicles,
        'visitors_synced', inserted_visitors,
        'movements_synced', inserted_movements,
        'items_synced', inserted_items,
        'timestamp', NOW()
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', SQLERRM,
        'state', SQLSTATE
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
