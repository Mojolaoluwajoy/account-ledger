-- Emails are now stored lower-cased so the unique constraint treats
-- Jane@Gmail.com and jane@gmail.com as the same address. This fails if two
-- existing clients have emails that differ only in case; merge or rename
-- one of them first.
update clients set email = lower(trim(email)) where email <> lower(trim(email));
