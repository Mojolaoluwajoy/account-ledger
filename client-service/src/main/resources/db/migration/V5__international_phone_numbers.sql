-- Phone numbers are now stored in international form (+2348142988584) so the
-- unique constraint treats 08142988584 and +234 814 298 8584 as the same
-- number. This fails if two existing clients have the same number written
-- differently; merge or change one of them first. Numbers that cannot be
-- read are left as they are and must be corrected on the client's next update.
update clients
set phone_number = regexp_replace(phone_number, '[[:space:]().-]', '', 'g')
where phone_number ~ '[[:space:]().-]';

update clients set phone_number = '+' || substring(phone_number from 3)
where phone_number ~ '^00[1-9]';

update clients set phone_number = '+234' || substring(phone_number from 2)
where phone_number ~ '^0[0-9]{10}$';

update clients set phone_number = '+' || phone_number
where phone_number ~ '^234[0-9]{10}$';

update clients set phone_number = '+234' || substring(phone_number from 6)
where phone_number ~ '^\+2340[0-9]{10}$';
