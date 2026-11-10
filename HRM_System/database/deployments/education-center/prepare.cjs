const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { randomBytes, randomInt } = require('node:crypto');
const { spawnSync } = require('node:child_process');

const database = process.argv[2] ?? 'hrm_center_education';
if (!/^hrm_center_education(?:_[a-z0-9_]{1,48})?$/.test(database)) {
  throw new Error('Use hrm_center_education or hrm_center_education_<suffix>.');
}

const root = path.resolve(__dirname, '../../..');
const output = path.join(root, 'target', 'education-center', database);
if (fs.existsSync(output)) throw new Error('An artifact already exists for this name.');

const mavenRepo = process.env.MAVEN_REPO_LOCAL ?? path.join(os.homedir(), '.m2', 'repository');
function dependencyJar(group, name) {
  const directory = path.join(mavenRepo, ...group.split('.'), name);
  const versions = fs.readdirSync(directory).sort((a, b) => b.localeCompare(a, 'en', { numeric: true }));
  const version = versions.find(v => fs.existsSync(path.join(directory, v, `${name}-${v}.jar`)));
  if (!version) throw new Error(`Missing Maven dependency ${name}. Build the backend first.`);
  return path.join(directory, version, `${name}-${version}.jar`);
}

const staff = [
  ['Nguyễn Minh Quân', 'CNTT', 'ADMIN_SYS', 'quantri', 'ADMIN', 'MALE'],
  ['Lê Thanh Hà', 'HCNS', 'HR', 'nhansu.thanhha', 'HR', 'FEMALE'],
  ['Trần Thị Thu Hương', 'HCNS', 'TRUONG_PHONG', 'truongphong.hanhchinh', 'EMPLOYEE', 'FEMALE'],
  ['Phạm Quốc Bảo', 'DT', 'TRUONG_PHONG', 'truongphong.daotao', 'EMPLOYEE', 'MALE'],
  ['Nguyễn Hoàng Anh', 'TV', 'TRUONG_PHONG', 'truongphong.tuyensinh', 'EMPLOYEE', 'MALE'],
  ['Võ Thị Ngọc Mai', 'KT', 'TRUONG_PHONG', 'truongphong.ketoan', 'EMPLOYEE', 'FEMALE'],
  ['Đặng Trung Kiên', 'CNTT', 'TRUONG_PHONG', 'truongphong.congnghe', 'EMPLOYEE', 'MALE'],
  ['Bùi Thị Kim Ngân', 'KT', 'KT_TONG_HOP', 'nv008', 'EMPLOYEE', 'FEMALE'],
  ['Lê Minh Khôi', 'DT', 'GV', 'nv009', 'EMPLOYEE', 'MALE'],
  ['Trần Thảo Nguyên', 'TV', 'TV', 'nv010', 'EMPLOYEE', 'FEMALE'],
  ['Nguyễn Đức Huy', 'KT', 'KT', 'nv011', 'EMPLOYEE', 'MALE'],
  ['Phan Gia Bảo', 'CNTT', 'IT', 'nv012', 'EMPLOYEE', 'MALE'],
  ['Nguyễn Thị Minh Châu', 'HCNS', 'HR', 'nhansu.minhchau', 'HR', 'FEMALE'],
  ['Đỗ Hải Yến', 'DT', 'GIAO_VU', 'nv014', 'EMPLOYEE', 'FEMALE'],
  ['Lâm Tuấn Anh', 'TV', 'CS_HOC_VIEN', 'nv015', 'EMPLOYEE', 'MALE'],
  ['Hoàng Thị Lan Anh', 'KT', 'KT_HOC_PHI', 'nv016', 'EMPLOYEE', 'FEMALE'],
  ['Trương Quốc Đạt', 'CNTT', 'IT_HOTRO', 'nv017', 'EMPLOYEE', 'MALE'],
  ['Vũ Ngọc Ánh', 'HCNS', 'HANH_CHINH', 'nv018', 'EMPLOYEE', 'FEMALE'],
  ['Nguyễn Thanh Tâm', 'DT', 'GV', 'nv019', 'EMPLOYEE', 'MALE'],
  ['Lê Thị Bích Hân', 'TV', 'TV', 'nv020', 'EMPLOYEE', 'FEMALE'],
  ['Phạm Anh Tuấn', 'KT', 'KT_TONG_HOP', 'nv021', 'EMPLOYEE', 'MALE'],
  ['Trần Minh Nhật', 'CNTT', 'IT', 'nv022', 'EMPLOYEE', 'MALE'],
  ['Ngô Thị Thanh Trúc', 'HCNS', 'HR', 'nv023', 'EMPLOYEE', 'FEMALE'],
  ['Đinh Quang Huy', 'DT', 'GIAO_VU', 'nv024', 'EMPLOYEE', 'MALE'],
  ['Huỳnh Thị Như Ý', 'TV', 'CS_HOC_VIEN', 'nv025', 'EMPLOYEE', 'FEMALE'],
  ['Nguyễn Bảo Trâm', 'KT', 'KT_HOC_PHI', 'nv026', 'EMPLOYEE', 'FEMALE'],
  ['Mai Hoàng Phúc', 'CNTT', 'IT_HOTRO', 'nv027', 'EMPLOYEE', 'MALE'],
  ['Phạm Thị Khánh Linh', 'HCNS', 'HANH_CHINH', 'nv028', 'EMPLOYEE', 'FEMALE'],
  ['Võ Minh Khang', 'DT', 'GV', 'nv029', 'EMPLOYEE', 'MALE'],
  ['Trần Thị Mỹ Duyên', 'TV', 'TV', 'nv030', 'EMPLOYEE', 'FEMALE'],
  ['Lê Quốc Việt', 'KT', 'KT', 'nv031', 'EMPLOYEE', 'MALE'],
  ['Đặng Thị Diệu Linh', 'CNTT', 'IT', 'nv032', 'EMPLOYEE', 'FEMALE'],
  ['Trần Phương Thảo', 'KT', 'KT_HOC_PHI', 'nv033', 'EMPLOYEE', 'FEMALE'],
  ['Vũ Đức Thành', 'CNTT', 'IT_HOTRO', 'nv034', 'EMPLOYEE', 'MALE'],
].map(([name, department, position, username, role, gender], index) => ({
  number: index + 1, code: 'NV' + String(index + 1).padStart(3, '0'),
  name, department, position, username, role, gender, password: 'Hrm!' + randomBytes(15).toString('base64url'),
}));

const classpath = [
  dependencyJar('org.springframework.security', 'spring-security-crypto'),
  dependencyJar('commons-logging', 'commons-logging'),
].join(path.delimiter);
const java = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  : 'java';
const hashed = spawnSync(java, ['--class-path', classpath, path.join(__dirname, '../refresh-personnel/HashCredentials.java')], {
  input: staff.map(person => `${person.username}\t${person.password}\n`).join(''),
  encoding: 'utf8',
});
if (hashed.status !== 0) throw new Error('Cannot generate BCrypt hashes. Check Java and Maven dependencies.');
const hashes = new Map(hashed.stdout.trim().split(/\r?\n/).map(row => row.split('\t')));
const sqlString = value => "N'" + value.replaceAll("'", "''") + "'";
const staffRows = staff.map(person => {
  const hash = hashes.get(person.username);
  if (!/^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/.test(hash)) throw new Error('Invalid BCrypt output.');
  return '(' + [
    person.number, ...[person.code, person.name, person.department, person.position,
      person.username, person.role, person.gender, hash].map(sqlString), randomInt(100000000, 900000000),
  ].join(', ') + ')';
}).join(',\n');

const read = file => fs.readFileSync(path.join(root, 'database', file), 'utf8').replace(/^\uFEFF/, '');
const sections = [
  ['Schema', read('consolidated/01_SCHEMA.sql').split(/^GO\s*$/mi).slice(3).join('\nGO\n')],
  ['Business rules', read('consolidated/02_BUSINESS_RULES.sql').split(/^GO\s*$/mi).slice(1).join('\nGO\n')],
  ...['016_contract_cancelled_status', '016_personnel_schedule_cancellation_compat',
    '017_single_day_effective_ranges', '018_contract_termination_history'].map(name =>
    [name, read(`migrations/${name}.sql`)]),
  ['Education center', read('deployments/education-center/seed.sql')
    .replace('/* ROLES_AND_PERMISSIONS */', read('consolidated/03_DEMO_DATA.sql').match(/INSERT dbo\.roles[\s\S]+?(?=INSERT dbo\.departments)/)[0])
    .replace('/* STAFF_ROWS */', staffRows)],
  ...['019_decision_number_sequences', '019_employee_position_codes', '020_leave_workspace_defaults',
    '021_leave_cancellation_roles', '022_leave_request_numbers', '023_contract_numbers'].map(name =>
    [name, read(`migrations/${name}.sql`)]),
  ['Validation', read('consolidated/05_VALIDATE.sql').split(/^GO\s*$/mi).slice(1).join('\nGO\n')],
  ['Workflow validation', read('deployments/education-center/verify.sql')],
];

// Each GO batch runs independently in the new database and shares one transaction.
const statements = sections.flatMap(([title, source]) => {
  if (!source?.trim()) throw new Error(`Missing SQL section ${title}`);
  return [`-- ${title}`, ...source.split(/^GO[ \t]*\r?$/mi).filter(batch => batch.trim())
    .map(batch => `EXEC [${database}].sys.sp_executesql ${sqlString(batch.trim())};`)];
}).join('\n');
const buildSql = `-- SQL Server. Run the entire file in one execution. SQLCMD Mode is optional.
USE master;
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
IF DB_ID(N'${database}') IS NOT NULL
    THROW 51000, N'Database already exists. Nothing overwritten.', 1;
EXEC(N'CREATE DATABASE [${database}]');
BEGIN TRY
BEGIN TRANSACTION;
${statements}
COMMIT;
SELECT N'${database}' AS database_name, N'COMMITTED' AS result;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
`;

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.mkdirSync(output);
fs.writeFileSync(path.join(output, 'DUNG_DATABASE.sql'), '\uFEFF' + buildSql);
fs.writeFileSync(path.join(output, 'TAI_KHOAN.txt'), staff.map(person =>
  `${person.code} | ${person.name} | ${person.department} | ${person.role}\n${person.username}\t${person.password}\n`).join('\n'));
console.log(`Prepared SQL and private credentials in ${output}`);
