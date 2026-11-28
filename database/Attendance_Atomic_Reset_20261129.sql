-- Normalize existing legacy tables before installing the atomic attendance reset.
-- The server initializes attendance_startup row 2 as the reset generation.
-- Row 1 retains its original daily calendar role.
ALTER TABLE attendance_startup ENGINE=InnoDB;
ALTER TABLE attendance_userinfo ENGINE=InnoDB;
