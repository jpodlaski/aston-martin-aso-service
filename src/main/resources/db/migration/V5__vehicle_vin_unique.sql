-- One active vehicle per VIN. Soft-removed rows keep history and may free the VIN for re-add.
CREATE UNIQUE INDEX IF NOT EXISTS ux_vehicle_vin_active
    ON vehicle (lower(vin))
    WHERE removed_from_account = FALSE;
