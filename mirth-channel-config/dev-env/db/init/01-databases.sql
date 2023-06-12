# db-ehr
CREATE DATABASE IF NOT EXISTS `interop-ehr`;
CREATE USER 'ehr'@'%' IDENTIFIED BY 'secret';
GRANT ALL PRIVILEGES ON `interop-ehr`.* TO 'ehr'@'%';

# mockehr
CREATE DATABASE IF NOT EXISTS `mock_ehr_db`;
CREATE USER 'springuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `mock_ehr_db`.* TO 'springuser'@'%';

# db-queue
CREATE DATABASE IF NOT EXISTS `interop-queue`;
CREATE USER 'queue'@'%' IDENTIFIED BY 'secret';
GRANT ALL PRIVILEGES ON `interop-queue`.* TO 'queue'@'%';

# dataauthority-db
CREATE DATABASE IF NOT EXISTS `dataauthority-db`;
CREATE USER 'ehrdauser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `dataauthority-db`.* TO 'ehrdauser'@'%';

# validation-db
CREATE DATABASE IF NOT EXISTS `validation-db`;
CREATE USER 'validationuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `validation-db`.* TO 'validationuser'@'%';
